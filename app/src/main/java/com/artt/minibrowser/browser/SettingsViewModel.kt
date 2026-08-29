package com.artt.minibrowser.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.SearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime

internal data class SettingsUiState(
    val prefs: Prefs = Prefs(),
    val adblockStatus: ExtensionLoader.Status? = null,
    val votStatus: ExtensionLoader.Status? = null,
)

/**
 * Owns persistent settings state and the extension side effects coupled to extension preferences.
 * The renderer receives values/callbacks only; DataStore and Gecko policy coordination stay here.
 */
internal class SettingsViewModel : ViewModel {
    private val setSearchEnginePreference: suspend (SearchEngine) -> Unit
    private val setThemePreference: suspend (Int) -> Unit
    private val setAdblockPreference: suspend (Boolean) -> Unit
    private val setVotPreference: suspend (Boolean) -> Unit
    private val setTranslateTargetPreference: suspend (String) -> Unit
    private val initializeExtensions: (Prefs) -> Unit
    private val applyAdblock: (Boolean) -> Unit
    private val retryAdblockExtension: (Boolean) -> Unit
    private val applyVot: (Boolean) -> Unit
    private val retryVotExtension: (Boolean) -> Unit

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    constructor(
        repository: SettingsRepository,
        runtime: GeckoRuntime,
    ) : super() {
        setSearchEnginePreference = { repository.setSearchEngine(it) }
        setThemePreference = { repository.setTheme(it) }
        setAdblockPreference = { repository.setAdblock(it) }
        setVotPreference = { repository.setVot(it) }
        setTranslateTargetPreference = { repository.setTranslateTarget(it) }
        initializeExtensions = { prefs ->
            ExtensionLoader.installAll(
                runtime,
                adblockEnabled = prefs.adblockEnabled,
                votEnabled = prefs.votEnabled,
            )
        }
        applyAdblock = { ExtensionLoader.setAdblock(runtime, it) }
        retryAdblockExtension = { ExtensionLoader.retryAdblock(runtime, it) }
        applyVot = { ExtensionLoader.setVot(runtime, it) }
        retryVotExtension = { ExtensionLoader.retryVot(runtime, it) }
        observe(
            prefs = repository.prefs,
            extensionStates = ExtensionLoader.state,
            scope = viewModelScope,
        )
    }

    internal constructor(
        prefs: Flow<Prefs>,
        extensionStates: StateFlow<Map<String, ExtensionLoader.ExtensionState>>,
        setSearchEnginePreference: suspend (SearchEngine) -> Unit = {},
        setThemePreference: suspend (Int) -> Unit = {},
        setAdblockPreference: suspend (Boolean) -> Unit = {},
        setVotPreference: suspend (Boolean) -> Unit = {},
        setTranslateTargetPreference: suspend (String) -> Unit = {},
        initializeExtensions: (Prefs) -> Unit = {},
        applyAdblock: (Boolean) -> Unit = {},
        retryAdblockExtension: (Boolean) -> Unit = {},
        applyVot: (Boolean) -> Unit = {},
        retryVotExtension: (Boolean) -> Unit = {},
        viewModelScope: CoroutineScope,
    ) : super(viewModelScope) {
        this.setSearchEnginePreference = setSearchEnginePreference
        this.setThemePreference = setThemePreference
        this.setAdblockPreference = setAdblockPreference
        this.setVotPreference = setVotPreference
        this.setTranslateTargetPreference = setTranslateTargetPreference
        this.initializeExtensions = initializeExtensions
        this.applyAdblock = applyAdblock
        this.retryAdblockExtension = retryAdblockExtension
        this.applyVot = applyVot
        this.retryVotExtension = retryVotExtension
        observe(prefs, extensionStates, viewModelScope)
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { setSearchEnginePreference(engine) }
    }

    fun setTheme(theme: Int) {
        viewModelScope.launch { setThemePreference(theme) }
    }

    fun setAdblock(enabled: Boolean) {
        viewModelScope.launch {
            // Preserve the existing ordering: persist the user's desired policy first, then ask
            // Gecko to converge the installed extension to that policy.
            setAdblockPreference(enabled)
            applyAdblock(enabled)
        }
    }

    fun retryAdblock() {
        retryAdblockExtension(_uiState.value.prefs.adblockEnabled)
    }

    fun setVot(enabled: Boolean) {
        viewModelScope.launch {
            setVotPreference(enabled)
            applyVot(enabled)
        }
    }

    fun retryVot() {
        retryVotExtension(_uiState.value.prefs.votEnabled)
    }

    fun setTranslateTarget(language: String) {
        viewModelScope.launch { setTranslateTargetPreference(language) }
    }

    private fun observe(
        prefs: Flow<Prefs>,
        extensionStates: StateFlow<Map<String, ExtensionLoader.ExtensionState>>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            var extensionsInitialized = false
            combine(prefs, extensionStates) { currentPrefs, states ->
                SettingsUiState(
                    prefs = currentPrefs,
                    adblockStatus = states[ExtensionLoader.UBLOCK_ID]?.status,
                    votStatus = states[ExtensionLoader.VOT_ID]?.status,
                )
            }.collect { state ->
                _uiState.value = state
                if (!extensionsInitialized) {
                    extensionsInitialized = true
                    initializeExtensions(state.prefs)
                }
            }
        }
    }

    companion object {
        fun factory(
            repository: SettingsRepository,
            runtime: GeckoRuntime,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository, runtime) }
        }
    }
}
