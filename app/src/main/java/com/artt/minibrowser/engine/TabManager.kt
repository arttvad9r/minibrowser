package com.artt.minibrowser.engine

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.artt.minibrowser.data.HistorySink
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import java.io.File

internal class ProgressGate(private val intervalMs: Long = 100) {
    private var lastPublishedAt: Long? = null

    fun accept(nowMs: Long = SystemClock.uptimeMillis(), progress: Int): Boolean {
        if (progress >= 100 || lastPublishedAt == null || nowMs - lastPublishedAt!! >= intervalMs) {
            lastPublishedAt = nowMs
            return true
        }
        return false
    }
}

enum class SecurityState { Unknown, Secure, Insecure, Exception }

internal fun shouldCloseSession(isOpen: Boolean): Boolean = isOpen

internal fun closeIfOpen(session: GeckoSession) {
    if (shouldCloseSession(session.isOpen)) {
        runCatching { session.stop() }
        runCatching { session.close() }
    }
}

internal enum class PersistSignal { Dirty, Immediate }

internal fun mergePersistSignal(current: PersistSignal, next: PersistSignal): PersistSignal =
    if (current == PersistSignal.Immediate || next == PersistSignal.Immediate) PersistSignal.Immediate else PersistSignal.Dirty

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class PersistSignalQueue(
    private val awaitNextOrTimeout: suspend (ReceiveChannel<PersistSignal>, Long) -> PersistSignal? = { channel, timeoutMs ->
        select<PersistSignal?> {
            channel.onReceive { it }
            onTimeout(timeoutMs) { null }
        }
    },
) {
    private val signals = Channel<PersistSignal>(Channel.UNLIMITED)

    fun send(signal: PersistSignal) {
        signals.trySend(signal)
    }

    suspend fun nextForWrite(): PersistSignal {
        var effective = signals.receive()
        if (effective == PersistSignal.Dirty) {
            val deadline = System.nanoTime() + 350_000_000L
            while (true) {
                val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1L)
                val next = awaitNextOrTimeout(signals, remainingMs) ?: break
                effective = mergePersistSignal(effective, next)
                if (effective == PersistSignal.Immediate) break
            }
        }
        while (true) {
            val next = signals.tryReceive().getOrNull() ?: break
            effective = mergePersistSignal(effective, next)
        }
        return effective
    }
}

internal data class PersistTabCandidate(
    val id: Long,
    val url: String,
    val title: String,
    val desktop: Boolean,
    val sessionState: String?,
    val lastAccess: Long,
    val isPrivate: Boolean,
)

internal fun snapshotPersistedState(selectedId: Long?, tabs: List<PersistTabCandidate>): PersistedBrowserState = PersistedBrowserState(
    selectedId = selectedId,
    tabs = tabs.filterNot { it.isPrivate }.map {
        PersistedTab(it.id, it.url, it.title, it.desktop, it.sessionState, it.lastAccess)
    },
)

class Tab(session: GeckoSession, val id: Long, val isPrivate: Boolean) {
    // Compose state: смена сессии при краш-восстановлении должна перерисовать AndroidView.
    var session: GeckoSession by mutableStateOf(session)
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    var canGoBack by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var securityState by mutableStateOf(SecurityState.Unknown)
    var loadError by mutableStateOf<String?>(null)
    internal val progressGate = ProgressGate()
    internal var restoreUrlOnOpen = false
    internal var persistedSessionState: String? = null
    internal var lastAccess = System.currentTimeMillis()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TabManager(
    private val runtime: GeckoRuntime,
    private val storeDir: File,
    private val context: android.content.Context,
    permissionRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null,
    filePicker: ((Int, Array<String>, (Array<android.net.Uri>) -> Unit) -> Unit)? = null,
) {
    private val promptController = (context as? Activity)?.let { GeckoPromptController(it, filePicker) }
    private val permissionController = (context as? Activity)?.let {
        GeckoPermissionController(it, permissionRequester)
    }
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistRequests = PersistSignalQueue()
    val tabs get() = _tabs
    val currentId = MutableStateFlow<Long?>(null)
    private var seq = 0L

    init {
        persistScope.launch {
            while (true) {
                persistRequests.nextForWrite()
                runCatching { TabStore.saveState(storeDir, snapshotForPersistence()) }
                    .onFailure { Log.e("MinibrowserTabs", "Failed to persist tab metadata", it) }
            }
        }
        restore()
    }

    fun newTab(url: String?, private: Boolean = false): Tab {
        val tab = createTab(private)
        currentId.value = tab.id
        openTab(tab)
        deactivateOthers(tab.id)
        url?.let(tab.session::loadUri)
        return tab
    }

    /** Creates the session GeckoView will own for target="_blank"/window.open(). */
    fun newWindowSession(private: Boolean): GeckoSession {
        val tab = createTab(private)
        currentId.value = tab.id
        deactivateOthers(tab.id)
        return tab.session
    }

    private fun createTab(private: Boolean): Tab = createTab(private, null)

    private fun createTab(private: Boolean, persisted: PersistedTab?): Tab {
        val s = GeckoSession(sessionSettings(private))
        val id = persisted?.id ?: ++seq
        seq = maxOf(seq, id)
        val tab = Tab(s, id, private)
        persisted?.let {
            tab.url = it.url
            tab.title = it.title
            tab.desktop = it.desktop
            tab.lastAccess = it.lastAccess
            tab.restoreUrlOnOpen = true
            tab.persistedSessionState = it.sessionState
        }
        attachDelegates(tab)
        _tabs.value += tab
        return tab
    }

    private fun openTab(tab: Tab) {
        tab.session.open(runtime)
        tab.session.setActive(true)
        tab.session.setFocused(true)
        applyDesktop(tab)
        tab.persistedSessionState?.let { encoded ->
            runCatching {
                GeckoSession.SessionState.fromString(encoded)?.let(tab.session::restoreState)
                    ?: error("Invalid session state")
            }.onFailure {
                tab.persistedSessionState = null
                tab.restoreUrlOnOpen = true
            }
        }
        if (tab.restoreUrlOnOpen && tab.url.isNotBlank() && tab.persistedSessionState == null) {
            tab.session.loadUri(tab.url)
        }
        tab.restoreUrlOnOpen = false
    }

    fun select(id: Long) {
        _tabs.value.forEach { tab ->
            if (tab.session.isOpen) {
                val selected = tab.id == id
                tab.session.setActive(selected)
                tab.session.setFocused(selected)
            }
        }
        currentId.value = id
        _tabs.value.firstOrNull { it.id == id }?.let {
            it.lastAccess = System.currentTimeMillis()
            if (!it.session.isOpen) openTab(it)
            applyDesktop(it)
        }
    }

    fun closeTab(id: Long) {
        val idx = _tabs.value.indexOfFirst { it.id == id }
        if (idx < 0) return
        val dying = _tabs.value[idx]
        closeIfOpen(dying.session)
        _tabs.value = _tabs.value - dying
        if (currentId.value == id) {
            val next = _tabs.value.getOrNull(idx.coerceAtMost(_tabs.value.size - 1))
            if (next != null) select(next.id) else newTab(null)
        }
        persist()
    }

    fun current(): Tab? = _tabs.value.firstOrNull { it.id == currentId.value }

    fun setAppVisible(visible: Boolean) {
        _tabs.value.filter { it.session.isOpen }.forEach { tab ->
            if (!visible && tab.id == currentId.value) tab.session.flushSessionState()
            val active = visible && tab.id == currentId.value
            tab.session.setActive(active)
            tab.session.setFocused(active)
        }
    }

    fun clearWebData(): GeckoResult<Void> {
        _tabs.value.forEach { tab ->
            closeIfOpen(tab.session)
        }
        _tabs.value = emptyList()
        currentId.value = null
        return runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept {
            newTab(null)
        }
    }

    fun persist() {
        requestPersist(immediate = true)
    }

    private fun requestPersist(immediate: Boolean) {
        persistRequests.send(if (immediate) PersistSignal.Immediate else PersistSignal.Dirty)
    }

    private fun snapshotForPersistence(): PersistedBrowserState = snapshotPersistedState(
        currentId.value,
        _tabs.value.map { PersistTabCandidate(it.id, it.url, it.title, it.desktop, it.persistedSessionState, it.lastAccess, it.isPrivate) },
    )

    private fun restore() {
        val saved = TabStore.loadState(storeDir)
        saved.tabs.forEach { createTab(private = false, persisted = it) }
        if (_tabs.value.isEmpty()) {
            newTab(null)
        } else {
            val selected = saved.selectedId?.takeIf { id -> _tabs.value.any { it.id == id } }
                ?: _tabs.value.first().id
            currentId.value = selected
            _tabs.value.first { it.id == selected }.let(::openTab)
        }
    }

    private fun attachDelegates(tab: Tab) {
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.progressGate.accept(progress = 5)
                tab.loadError = null
                tab.securityState = SecurityState.Unknown
                tab.progress = 0.05f; tab.url = url
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.progress = -1f
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (tab.progressGate.accept(progress = progress)) tab.progress = progress / 100f
            }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                tab.securityState = when {
                    securityInfo.isException -> SecurityState.Exception
                    securityInfo.isSecure -> SecurityState.Secure
                    else -> SecurityState.Insecure
                }
            }
            override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) {
                tab.persistedSessionState = state.toString()
                if (!tab.isPrivate) {
                    requestPersist(immediate = false)
                }
            }
        }
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>, triggeredByUser: Boolean) {
                tab.url = url.orEmpty()
            }
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                if (isAllowedWebUri(request.uri) || request.uri == "about:blank") {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                if (request.hasUserGesture && isExternalScheme(request.uri)) {
                    launchExternalUri(request.uri)
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: org.mozilla.geckoview.WebRequestError,
            ): GeckoResult<String>? {
                tab.loadError = when (error.category) {
                    org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_SECURITY -> "Проблема безопасности соединения"
                    org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_NETWORK -> "Проверьте интернет-соединение"
                    else -> "Не удалось открыть страницу"
                }
                return GeckoResult.fromValue(null)
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack }
            // target="_blank"/window.open (так открывают результаты поисковиков) — новая вкладка с автопереключением.
            // Возвращённую сессию открываем сами, uri в неё грузит сам GeckoView.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val newSession = newWindowSession(tab.isPrivate)
                // GeckoView opens and owns this session after the result is returned.
                return GeckoResult.fromValue(newSession)
            }
        }
        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                if (!tab.isPrivate) HistorySink.updateTitle(tab.url, title)
            }
            // Движок сам разворачивает видео; приложение по этому колбэку прячет бары и тулбар.
            override fun onFullScreen(session: GeckoSession, fullscreen: Boolean) {
                tab.fullscreen = fullscreen
            }
            // Файлы по прямым ссылкам (attachment) — в системный DownloadManager.
            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                val fallback = response.uri.substringAfterLast('/').substringBefore('?').ifBlank { "file" }
                runCatching { enqueueDownload(context, response.uri, fallback, response.headers) }
                    .onFailure {
                        Log.e("MinibrowserDownload", "Failed to enqueue download", it)
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Не удалось начать загрузку", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            override fun onCrash(session: GeckoSession) {
                recoverDeadSession(tab)
            }

            override fun onKill(session: GeckoSession) {
                recoverDeadSession(tab)
            }
        }
        tab.session.historyDelegate = object : GeckoSession.HistoryDelegate {
            // История пишется в задаче 6; приватные вкладки фильтруем явно.
            override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
                if (!tab.isPrivate && flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0) {
                    HistorySink.record(url, tab.title)
                }
                return null
            }
        }
        promptController?.let(tab.session::setPromptDelegate)
        permissionController?.let(tab.session::setPermissionDelegate)
    }

    private fun launchExternalUri(uri: String) {
        val intent = createSafeExternalIntent(uri)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        runCatching {
            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        }
    }

    private fun deactivateOthers(selectedId: Long) {
        _tabs.value.filter { it.id != selectedId && it.session.isOpen }.forEach {
            it.session.setFocused(false)
            it.session.setActive(false)
        }
    }

    private fun applyDesktop(tab: Tab) {
        if (!tab.session.isOpen) return
        tab.session.settings.userAgentMode =
            if (tab.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        tab.session.settings.viewportMode =
            if (tab.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }

    private fun sessionSettings(private: Boolean): GeckoSessionSettings =
        GeckoSessionSettings.Builder()
            .usePrivateMode(private)
            .suspendMediaWhenInactive(true)
            .build()

    private fun recoverDeadSession(tab: Tab) {
        val wasActive = tab.id == currentId.value
        closeIfOpen(tab.session)
        val fresh = GeckoSession(sessionSettings(tab.isPrivate))
        tab.session = fresh
        attachDelegates(tab)
        fresh.open(runtime)
        applyDesktop(tab)
        fresh.setActive(wasActive)
        fresh.setFocused(wasActive)
        val restored = tab.persistedSessionState?.let { encoded ->
            runCatching {
                GeckoSession.SessionState.fromString(encoded)?.let {
                    fresh.restoreState(it)
                    true
                } ?: false
            }.getOrDefault(false)
        } == true
        if (!restored) fresh.loadUri(tab.url.ifEmpty { "about:blank" })
    }
}
