package com.artt.minibrowser

import com.artt.minibrowser.browser.SettingsUiState
import com.artt.minibrowser.browser.SettingsViewModel
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.SearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @Test
    fun initialStateCombinesPrefsAndExtensionStatusesAndInitializesOnce() {
        val prefs = MutableStateFlow(Prefs(theme = 2, adblockEnabled = false, votEnabled = true))
        val extensionStates = MutableStateFlow(
            mapOf(
                ExtensionLoader.UBLOCK_ID to ExtensionLoader.ExtensionState(ExtensionLoader.Status.Disabled),
                ExtensionLoader.VOT_ID to ExtensionLoader.ExtensionState(ExtensionLoader.Status.Enabled),
            ),
        )
        val initialized = mutableListOf<Prefs>()
        val viewModel = settingsViewModel(
            prefs = prefs,
            extensionStates = extensionStates,
            initializeExtensions = { initialized += it },
        )

        assertEquals(
            SettingsUiState(
                prefs = prefs.value,
                adblockStatus = ExtensionLoader.Status.Disabled,
                votStatus = ExtensionLoader.Status.Enabled,
            ),
            viewModel.uiState.value,
        )
        assertEquals(listOf(prefs.value), initialized)

        extensionStates.value = extensionStates.value +
            (ExtensionLoader.UBLOCK_ID to ExtensionLoader.ExtensionState(ExtensionLoader.Status.Enabled))

        assertEquals(ExtensionLoader.Status.Enabled, viewModel.uiState.value.adblockStatus)
        assertEquals(1, initialized.size)
    }

    @Test
    fun ordinaryPreferencesDelegateToRepositoryBoundary() {
        val selectedEngine = SearchEngine.entries.last()
        var engine: SearchEngine? = null
        var theme: Int? = null
        var language: String? = null
        val viewModel = settingsViewModel(
            setSearchEnginePreference = { engine = it },
            setThemePreference = { theme = it },
            setTranslateTargetPreference = { language = it },
        )

        viewModel.setSearchEngine(selectedEngine)
        viewModel.setTheme(1)
        viewModel.setTranslateTarget("de")

        assertEquals(selectedEngine, engine)
        assertEquals(1, theme)
        assertEquals("de", language)
    }

    @Test
    fun newerOrdinaryPreferenceCancelsOlderWrite() {
        val writes = mutableListOf<Int>()
        var firstCancelled = false
        val viewModel = settingsViewModel(
            setThemePreference = { theme ->
                if (theme == 1) {
                    try {
                        awaitCancellation()
                    } finally {
                        firstCancelled = true
                    }
                } else {
                    writes += theme
                }
            },
        )

        viewModel.setTheme(1)
        viewModel.setTheme(2)

        assertTrue(firstCancelled)
        assertEquals(listOf(2), writes)
    }

    @Test
    fun adblockPersistsBeforeApplyingRuntimePolicy() {
        val events = mutableListOf<String>()
        val viewModel = settingsViewModel(
            setAdblockPreference = { events += "persist:$it" },
            applyAdblock = { events += "apply:$it" },
        )

        viewModel.setAdblock(false)

        assertEquals(listOf("persist:false", "apply:false"), events)
    }

    @Test
    fun newerAdblockChoiceCancelsOlderWriteBeforeRuntimeApply() {
        val events = mutableListOf<String>()
        var firstCancelled = false
        val viewModel = settingsViewModel(
            setAdblockPreference = { enabled ->
                if (enabled) {
                    try {
                        awaitCancellation()
                    } finally {
                        firstCancelled = true
                    }
                } else {
                    events += "persist:false"
                }
            },
            applyAdblock = { events += "apply:$it" },
        )

        viewModel.setAdblock(true)
        viewModel.setAdblock(false)

        assertTrue(firstCancelled)
        assertEquals(listOf("persist:false", "apply:false"), events)
    }

    @Test
    fun adblockRetryUsesCurrentPreferenceWithoutRewritingIt() {
        val prefs = MutableStateFlow(Prefs(adblockEnabled = false))
        var persistedWrites = 0
        var retried: Boolean? = null
        val viewModel = settingsViewModel(
            prefs = prefs,
            setAdblockPreference = { persistedWrites++ },
            retryAdblockExtension = { retried = it },
        )

        viewModel.retryAdblock()

        assertEquals(false, retried)
        assertEquals(0, persistedWrites)
    }

    @Test
    fun votPersistsBeforeApplyAndRetryUsesCurrentPreference() {
        val prefs = MutableStateFlow(Prefs(votEnabled = false))
        val events = mutableListOf<String>()
        var retried: Boolean? = null
        val viewModel = settingsViewModel(
            prefs = prefs,
            setVotPreference = { events += "persist:$it" },
            applyVot = { events += "apply:$it" },
            retryVotExtension = { retried = it },
        )

        viewModel.setVot(true)
        viewModel.retryVot()

        assertEquals(listOf("persist:true", "apply:true"), events)
        assertEquals(false, retried)
    }

    private fun settingsViewModel(
        prefs: MutableStateFlow<Prefs> = MutableStateFlow(Prefs()),
        extensionStates: MutableStateFlow<Map<String, ExtensionLoader.ExtensionState>> = MutableStateFlow(emptyMap()),
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
    ): SettingsViewModel = SettingsViewModel(
        prefs = prefs,
        extensionStates = extensionStates,
        setSearchEnginePreference = setSearchEnginePreference,
        setThemePreference = setThemePreference,
        setAdblockPreference = setAdblockPreference,
        setVotPreference = setVotPreference,
        setTranslateTargetPreference = setTranslateTargetPreference,
        initializeExtensions = initializeExtensions,
        applyAdblock = applyAdblock,
        retryAdblockExtension = retryAdblockExtension,
        applyVot = applyVot,
        retryVotExtension = retryVotExtension,
        viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
