package com.artt.minibrowser.engine

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.BuildConfig
import com.artt.minibrowser.data.HistorySink
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
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
    private val nowNanos: () -> Long = System::nanoTime,
    private val awaitNextOrTimeout: suspend (ReceiveChannel<Unit>, Long) -> Unit? = { channel, timeoutMs ->
        select<Unit?> {
            channel.onReceive { it }
            onTimeout(timeoutMs) { null }
        }
    },
) {
    private companion object {
        const val TRAILING_DEBOUNCE_NS = 1_000_000_000L
        const val HARD_DEADLINE_NS = 3_000_000_000L
    }

    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val pendingLock = Any()
    private var pending: PersistSignal? = null

    fun send(signal: PersistSignal) {
        synchronized(pendingLock) {
            pending = pending?.let { mergePersistSignal(it, signal) } ?: signal
        }
        wakeups.trySend(Unit)
    }

    suspend fun nextForWrite(): PersistSignal {
        var effective = receivePending()
        if (effective == PersistSignal.Dirty) {
            val hardDeadline = nowNanos() + HARD_DEADLINE_NS
            var quietDeadline = nowNanos() + TRAILING_DEBOUNCE_NS
            while (effective == PersistSignal.Dirty) {
                val deadline = minOf(quietDeadline, hardDeadline)
                val remainingNs = deadline - nowNanos()
                if (remainingNs <= 0L) break
                val next = awaitNextOrTimeout(wakeups, (remainingNs + 999_999L) / 1_000_000L)
                val pending = takePending()
                if (pending == PersistSignal.Immediate) {
                    effective = PersistSignal.Immediate
                    break
                }
                if (pending == PersistSignal.Dirty) {
                    val now = nowNanos()
                    if (now >= hardDeadline) break
                    quietDeadline = minOf(now + TRAILING_DEBOUNCE_NS, hardDeadline)
                    continue
                }
                if (next == null) break
            }
        }
        return takePending()?.let { mergePersistSignal(effective, it) } ?: effective
    }

    private suspend fun receivePending(): PersistSignal {
        while (true) {
            takePending()?.let { return it }
            wakeups.receive()
        }
    }

    private fun takePending(): PersistSignal? = synchronized(pendingLock) {
        val value = pending
        pending = null
        value
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

internal data class SessionStateSelection(
    val state: String?,
    val stateUrl: String?,
)

internal fun selectSessionStateForUrl(
    tabUrl: String,
    latestState: String?,
    latestStateUrl: String?,
    serializedState: String?,
    serializedStateUrl: String?,
): SessionStateSelection {
    if (latestState != null && latestStateUrl == tabUrl) {
        return SessionStateSelection(latestState, tabUrl)
    }
    if (serializedState != null && serializedStateUrl == tabUrl) {
        return SessionStateSelection(serializedState, tabUrl)
    }
    return SessionStateSelection(null, null)
}

/** Returns the URI represented by the current history entry inside Gecko's own state snapshot. */
internal fun currentSessionStateUrl(state: GeckoSession.SessionState): String? = runCatching {
    val index = state.currentIndex
    if (index < 0 || index >= state.size) null else state[index].uri
}.getOrNull()?.takeIf { it.isNotBlank() }

internal data class PersistenceTabSnapshot(
    val id: Long,
    val url: String,
    val title: String,
    val desktop: Boolean,
    val lastAccess: Long,
    val latestSessionState: GeckoSession.SessionState?,
    val latestSessionStateUrl: String?,
    val serializedSessionState: String?,
    val serializedSessionStateUrl: String?,
    val isPrivate: Boolean,
)

internal data class PersistenceSnapshot(
    val selectedId: Long?,
    val tabs: List<PersistenceTabSnapshot>,
)

internal fun serializePersistenceSnapshot(snapshot: PersistenceSnapshot): PersistedBrowserState = PersistedBrowserState(
    selectedId = snapshot.selectedId,
    tabs = snapshot.tabs.filterNot { it.isPrivate }.map {
        val selectedState = selectSessionStateForUrl(
            tabUrl = it.url,
            latestState = it.latestSessionState?.toString(),
            latestStateUrl = it.latestSessionStateUrl,
            serializedState = it.serializedSessionState,
            serializedStateUrl = it.serializedSessionStateUrl,
        )
        PersistedTab(
            id = it.id,
            url = it.url,
            title = it.title,
            desktop = it.desktop,
            sessionState = selectedState.state,
            lastAccess = it.lastAccess,
            sessionStateUrl = selectedState.stateUrl,
        )
    },
)

internal fun snapshotPersistedState(selectedId: Long?, tabs: List<PersistTabCandidate>): PersistedBrowserState = PersistedBrowserState(
    selectedId = selectedId,
    tabs = tabs.filterNot { it.isPrivate }.map {
        PersistedTab(
            id = it.id,
            url = it.url,
            title = it.title,
            desktop = it.desktop,
            sessionState = it.sessionState,
            lastAccess = it.lastAccess,
            sessionStateUrl = it.url.takeIf { _ -> it.sessionState != null },
        )
    },
)

class Tab(session: GeckoSession, val id: Long, val isPrivate: Boolean) {
    // Compose state: смена сессии при краш-восстановлении должна перерисовать AndroidView.
    var session: GeckoSession by mutableStateOf(session)
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    var scrollY by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var securityState by mutableStateOf(SecurityState.Unknown)
    var loadError by mutableStateOf<String?>(null)
    internal val progressGate = ProgressGate()
    internal var restoreUrlOnOpen = false
    @Volatile internal var latestSessionState: GeckoSession.SessionState? = null
    @Volatile internal var latestSessionStateUrl: String? = null
    internal var persistedSessionState: String? = null
    internal var persistedSessionStateUrl: String? = null
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
    private val downloadController = (context as? Activity)?.let {
        GeckoDownloadController(it, permissionRequester)
    }
    private val contextMenuController = (context as? Activity)?.let { activity ->
        GeckoContextMenuController(activity) { uri, private -> newTab(uri, private) }
    }
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    private val persistJob = SupervisorJob()
    private val persistScope = CoroutineScope(persistJob + Dispatchers.IO)
    private val persistRequests = PersistSignalQueue()
    private val lifecycleOwner = context as? LifecycleOwner
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            close()
        }
    }
    val tabs get() = _tabs
    val currentId = MutableStateFlow<Long?>(null)
    private var seq = 0L
    private var closed = false

    init {
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        persistScope.launch {
            while (true) {
                persistRequests.nextForWrite()
                val snapshot = withContext(Dispatchers.Main.immediate) { capturePersistenceSnapshot() }
                runCatching { TabStore.saveState(storeDir, serializePersistenceSnapshot(snapshot)) }
                    .onFailure { Log.e("MinibrowserTabs", "Failed to persist tab metadata", it) }
            }
        }
        restore()
    }

    fun newTab(url: String?, private: Boolean = false): Tab {
        check(!closed) { "TabManager is closed" }
        val tab = createTab(private)
        deactivateOthers(tab.id)
        currentId.value = tab.id
        openTab(tab)
        url?.let(tab.session::loadUri)
        return tab
    }

    /** Creates the session GeckoView will own for target="_blank"/window.open(). */
    fun newWindowSession(private: Boolean): GeckoSession {
        check(!closed) { "TabManager is closed" }
        val tab = createTab(private)
        deactivateOthers(tab.id)
        currentId.value = tab.id
        runtime.webExtensionController.setTabActive(tab.session, true)
        return tab.session
    }

    private fun createTab(private: Boolean): Tab = createTab(private, null)

    private fun createTab(private: Boolean, persisted: PersistedTab?): Tab {
        val s = GeckoSession(sessionSettings(private))
        val id = persisted?.id ?: ++seq
        seq = maxOf(seq, id)
        val tab = Tab(s, id, private)
        persisted?.let { saved ->
            tab.url = saved.url
            tab.title = saved.title
            tab.desktop = saved.desktop
            tab.lastAccess = saved.lastAccess
            tab.restoreUrlOnOpen = true
            val stateUrl = saved.sessionStateUrl
            if (saved.sessionState != null && stateUrl == saved.url) {
                tab.persistedSessionState = saved.sessionState
                tab.persistedSessionStateUrl = stateUrl
            }
        }
        attachDelegates(tab)
        _tabs.value += tab
        return tab
    }

    private fun openTab(tab: Tab) {
        if (closed) return
        tab.session.open(runtime)
        val selected = tab.id == currentId.value
        tab.session.setActive(selected)
        tab.session.setFocused(selected)
        runtime.webExtensionController.setTabActive(tab.session, selected)
        applyDesktop(tab)
        tab.persistedSessionState?.takeIf { tab.persistedSessionStateUrl == tab.url }?.let { encoded ->
            runCatching {
                GeckoSession.SessionState.fromString(encoded)?.let(tab.session::restoreState)
                    ?: error("Invalid session state")
            }.onFailure {
                tab.persistedSessionState = null
                tab.persistedSessionStateUrl = null
                tab.restoreUrlOnOpen = true
            }
        }
        if (tab.restoreUrlOnOpen && tab.url.isNotBlank() &&
            (tab.persistedSessionState == null || tab.persistedSessionStateUrl != tab.url)
        ) {
            tab.session.loadUri(tab.url)
        }
        tab.restoreUrlOnOpen = false
    }

    fun select(id: Long) {
        if (closed) return
        val selectedTab = _tabs.value.firstOrNull { it.id == id } ?: return
        _tabs.value.forEach { tab ->
            val selected = tab.id == id
            runtime.webExtensionController.setTabActive(tab.session, selected)
            if (tab.session.isOpen) {
                tab.session.setActive(selected)
                tab.session.setFocused(selected)
            }
        }
        currentId.value = id
        selectedTab.let {
            it.lastAccess = System.currentTimeMillis()
            if (!it.session.isOpen) openTab(it)
            applyDesktop(it)
        }
    }

    fun closeTab(id: Long) {
        if (closed) return
        val idx = _tabs.value.indexOfFirst { it.id == id }
        if (idx < 0) return
        val dying = _tabs.value[idx]
        runtime.webExtensionController.setTabActive(dying.session, false)
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
        if (closed) return
        _tabs.value.filter { it.session.isOpen }.forEach { tab ->
            val active = visible && tab.id == currentId.value
            tab.session.setActive(active)
            tab.session.setFocused(active)
        }
    }

    fun clearWebData(): GeckoResult<Void> {
        if (closed) return GeckoResult.fromValue<Void>(null)
        _tabs.value.forEach { tab ->
            runtime.webExtensionController.setTabActive(tab.session, false)
            closeIfOpen(tab.session)
        }
        _tabs.value = emptyList()
        currentId.value = null
        return runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept(
            { if (!closed) newTab(null) },
            { error ->
                Log.e("MinibrowserTabs", "Failed to clear web data", error)
                if (!closed) newTab(null)
            },
        )
    }

    fun persist() {
        requestPersist(immediate = true)
    }

    fun close() {
        if (closed) return
        val finalSnapshot = capturePersistenceSnapshot()
        runCatching { TabStore.saveState(storeDir, serializePersistenceSnapshot(finalSnapshot)) }
            .onFailure { Log.e("MinibrowserTabs", "Failed to persist final tab metadata", it) }
        closed = true
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        _tabs.value.forEach { tab ->
            runCatching { runtime.webExtensionController.setTabActive(tab.session, false) }
            closeIfOpen(tab.session)
        }
        persistJob.cancel()
    }

    private fun requestPersist(immediate: Boolean) {
        if (!closed) persistRequests.send(if (immediate) PersistSignal.Immediate else PersistSignal.Dirty)
    }

    private fun capturePersistenceSnapshot(): PersistenceSnapshot = PersistenceSnapshot(
        selectedId = currentId.value,
        tabs = _tabs.value.map {
            PersistenceTabSnapshot(
                id = it.id,
                url = it.url,
                title = it.title,
                desktop = it.desktop,
                lastAccess = it.lastAccess,
                latestSessionState = it.latestSessionState,
                latestSessionStateUrl = it.latestSessionStateUrl,
                serializedSessionState = it.persistedSessionState,
                serializedSessionStateUrl = it.persistedSessionStateUrl,
                isPrivate = it.isPrivate,
            )
        },
    )

    private fun restore() {
        val saved = TabStore.loadState(storeDir)
        saved.tabs.forEach { createTab(private = false, persisted = it) }
        if (_tabs.value.isEmpty()) {
            newTab(null)
        } else {
            val selected = saved.selectedId?.takeIf { id -> _tabs.value.any { it.id == id } }
                ?: _tabs.value.maxByOrNull { it.lastAccess }!!.id
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
                tab.scrollY = 0
                tab.progress = 0.05f
                tab.url = url
                tab.title = ""
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
                tab.latestSessionState = state
                tab.latestSessionStateUrl = currentSessionStateUrl(state)
                if (!tab.isPrivate) requestPersist(immediate = false)
            }
        }

        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                triggeredByUser: Boolean,
            ) {
                tab.url = url.orEmpty()
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MinibrowserNavigation",
                        "load uri=${request.uri} target=${request.target} trigger=${request.triggerUri} userGesture=${request.hasUserGesture} redirect=${request.isRedirect}",
                    )
                }
                if (isAllowedWebUri(request.uri) || request.uri == "about:blank") {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                if (request.hasUserGesture && isExternalScheme(request.uri)) {
                    launchExternalUri(tab, request.uri)
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

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            // target="_blank"/window.open — GeckoView loads the URI into the returned session.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                if (BuildConfig.DEBUG) Log.d("MinibrowserNavigation", "new session uri=$uri")
                return GeckoResult.fromValue(newWindowSession(tab.isPrivate))
            }
        }

        tab.session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                tab.scrollY = scrollY.coerceAtLeast(0)
            }
        }

        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                if (!tab.isPrivate) HistorySink.updateTitle(tab.url, title)
            }

            override fun onFullScreen(session: GeckoSession, fullscreen: Boolean) {
                tab.fullscreen = fullscreen
            }

            override fun onCloseRequest(session: GeckoSession) {
                closeTab(tab.id)
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                contextMenuController?.show(element, tab.isPrivate)
            }

            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                val controller = downloadController
                if (controller != null) {
                    controller.handle(response)
                } else {
                    runCatching { response.body?.close() }
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
            override fun onVisited(
                session: GeckoSession,
                url: String,
                lastVisitedURL: String?,
                flags: Int,
            ): GeckoResult<Boolean>? {
                if (!tab.isPrivate && flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0) {
                    HistorySink.record(url, tab.title)
                }
                return null
            }
        }
        promptController?.let(tab.session::setPromptDelegate)
        permissionController?.let(tab.session::setPermissionDelegate)
    }

    private fun launchExternalUri(tab: Tab, uri: String) {
        val intent = createSafeExternalIntent(uri)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val launched = intent != null && intent.resolveActivity(context.packageManager) != null && runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            safeExternalFallbackUrl(uri)?.let(tab.session::loadUri)
        }
    }

    private fun deactivateOthers(selectedId: Long) {
        _tabs.value.filter { it.id != selectedId }.forEach {
            runtime.webExtensionController.setTabActive(it.session, false)
            if (it.session.isOpen) {
                it.session.setFocused(false)
                it.session.setActive(false)
            }
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
        if (closed) return
        val wasActive = tab.id == currentId.value
        runtime.webExtensionController.setTabActive(tab.session, false)
        closeIfOpen(tab.session)
        tab.canGoBack = false
        tab.canGoForward = false
        tab.scrollY = 0
        val fresh = GeckoSession(sessionSettings(tab.isPrivate))
        tab.session = fresh
        attachDelegates(tab)
        fresh.open(runtime)
        applyDesktop(tab)
        fresh.setActive(wasActive)
        fresh.setFocused(wasActive)
        runtime.webExtensionController.setTabActive(fresh, wasActive)

        val latest = tab.latestSessionState.takeIf { tab.latestSessionStateUrl == tab.url }
        val restoredLatest = latest?.let { state ->
            runCatching {
                fresh.restoreState(state)
                true
            }.getOrDefault(false)
        } == true
        val restoredPersisted = if (!restoredLatest) {
            tab.persistedSessionState?.takeIf { tab.persistedSessionStateUrl == tab.url }?.let { encoded ->
                runCatching {
                    GeckoSession.SessionState.fromString(encoded)?.let {
                        fresh.restoreState(it)
                        true
                    } ?: false
                }.getOrDefault(false)
            } == true
        } else false

        if (!restoredLatest && !restoredPersisted) {
            fresh.loadUri(tab.url.ifEmpty { "about:blank" })
        }
    }
}
