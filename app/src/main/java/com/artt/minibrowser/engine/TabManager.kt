package com.artt.minibrowser.engine

import android.app.Activity
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.BuildConfig
import com.artt.minibrowser.browser.isCurrentPermissionRequestTab
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequestError
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val TAB_RESTORE_MATERIALIZE_TRACE = "TabManager.restoreTabs"
internal const val TAB_RESTORE_OPEN_SELECTED_TRACE = "TabManager.openSelected"

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
enum class PageLoadError { Security, Network, Generic }

internal fun pageLoadErrorForCategory(category: Int): PageLoadError = when (category) {
    WebRequestError.ERROR_CATEGORY_SECURITY -> PageLoadError.Security
    WebRequestError.ERROR_CATEGORY_NETWORK -> PageLoadError.Network
    else -> PageLoadError.Generic
}

internal fun shouldCloseSession(isOpen: Boolean): Boolean = isOpen

internal fun closeIfOpen(session: GeckoSession) {
    if (shouldCloseSession(session.isOpen)) {
        runCatching { session.stop() }
        runCatching { session.close() }
    }
}

internal fun shouldCreateBlankTabAfterClear(
    requestGeneration: Long,
    currentGeneration: Long,
    hasTabs: Boolean,
    isClosed: Boolean,
): Boolean = !isClosed && requestGeneration == currentGeneration && !hasTabs

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
        const val TRAILING_DEBOUNCE_NS = 1_500_000_000L
        const val HARD_DEADLINE_NS = 5_000_000_000L
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
    var session: GeckoSession by mutableStateOf(session)
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var securityState by mutableStateOf(SecurityState.Unknown)
    var loadError by mutableStateOf<PageLoadError?>(null)
    internal val progressGate = ProgressGate()
    internal var restoreUrlOnOpen = false
    @Volatile internal var latestSessionState: GeckoSession.SessionState? = null
    @Volatile internal var latestSessionStateUrl: String? = null
    internal var persistedSessionState: String? = null
    internal var persistedSessionStateUrl: String? = null
    internal var historyTitleUrl: String? = null
    internal var lastAccess = System.currentTimeMillis()
}

internal data class ClosedTabSnapshot(
    val id: Long,
    val index: Int,
    val wasCurrent: Boolean,
    val isPrivate: Boolean,
    val url: String,
    val title: String,
    val desktop: Boolean,
    val lastAccess: Long,
    val latestSessionState: GeckoSession.SessionState?,
    val latestSessionStateUrl: String?,
    val persistedSessionState: String?,
    val persistedSessionStateUrl: String?,
)

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
        GeckoPermissionController(it, permissionRequester, ::isCurrentPermissionSession)
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
    private val persistRevision = AtomicLong(0L)
    private val clearGeneration = AtomicLong(0L)
    private val hotTabLimit = BrowserPerformance.policy.hotTabLimit
    private val backgroundHotTabLimit = BrowserPerformance.policy.backgroundHotTabLimit
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
    private var appVisible = true
    private var backgroundTrimRequested = false

    init {
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        persistScope.launch {
            while (true) {
                persistRequests.nextForWrite()
                val (revision, snapshot) = withContext(Dispatchers.Main.immediate) {
                    persistRevision.get() to capturePersistenceSnapshot()
                }
                runCatching {
                    TabStore.saveStateVersioned(storeDir, serializePersistenceSnapshot(snapshot), revision)
                }.onFailure { Log.e("MinibrowserTabs", "Failed to persist tab metadata", it) }
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
        enforceHotTabBudget()
        return tab
    }

    fun newWindowSession(private: Boolean): GeckoSession {
        check(!closed) { "TabManager is closed" }
        val tab = createTab(private)
        deactivateOthers(tab.id)
        currentId.value = tab.id
        tab.session.setPriorityHint(if (appVisible) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT)
        runtime.webExtensionController.setTabActive(tab.session, true)
        return tab.session
    }

    private fun createTab(private: Boolean): Tab = createTab(private, null, publish = true)

    private fun createTab(private: Boolean, persisted: PersistedTab?, publish: Boolean = true): Tab {
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
        if (publish) _tabs.value += tab
        return tab
    }

    private fun openTab(tab: Tab) {
        if (closed) return
        tab.session.open(runtime)
        val selected = tab.id == currentId.value
        val active = selected && appVisible
        tab.session.setActive(active)
        tab.session.setFocused(active)
        tab.session.setPriorityHint(if (active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT)
        runtime.webExtensionController.setTabActive(tab.session, selected)
        applyDesktop(tab)

        var restored = false
        tab.latestSessionState?.takeIf { tab.latestSessionStateUrl == tab.url }?.let { state ->
            restored = runCatching {
                tab.session.restoreState(state)
                true
            }.getOrDefault(false)
        }
        if (!restored) {
            tab.persistedSessionState?.takeIf { tab.persistedSessionStateUrl == tab.url }?.let { encoded ->
                restored = runCatching {
                    GeckoSession.SessionState.fromString(encoded)?.let {
                        tab.session.restoreState(it)
                        true
                    } ?: false
                }.getOrDefault(false)
                if (!restored) {
                    tab.persistedSessionState = null
                    tab.persistedSessionStateUrl = null
                }
            }
        }
        if (tab.restoreUrlOnOpen && tab.url.isNotBlank() && !restored) {
            tab.session.loadUri(tab.url)
        }
        tab.restoreUrlOnOpen = false
    }

    fun select(id: Long) {
        if (closed) return
        val selectedTab = _tabs.value.firstOrNull { it.id == id } ?: return
        val previousId = currentId.value

        if (previousId != id) {
            _tabs.value.firstOrNull { it.id == previousId }?.let(::deactivateTab)
            currentId.value = id
        }

        selectedTab.lastAccess = System.currentTimeMillis()
        if (!selectedTab.session.isOpen) {
            openTab(selectedTab)
        } else {
            runtime.webExtensionController.setTabActive(selectedTab.session, true)
            val active = appVisible
            selectedTab.session.setActive(active)
            selectedTab.session.setFocused(active)
            selectedTab.session.setPriorityHint(
                if (active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT,
            )
            applyDesktop(selectedTab)
        }
        enforceHotTabBudget()
    }

    fun closeTab(id: Long): ClosedTabSnapshot? {
        if (closed) return null
        val idx = _tabs.value.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val dying = _tabs.value[idx]
        val snapshot = ClosedTabSnapshot(
            id = dying.id,
            index = idx,
            wasCurrent = currentId.value == id,
            isPrivate = dying.isPrivate,
            url = dying.url,
            title = dying.title,
            desktop = dying.desktop,
            lastAccess = dying.lastAccess,
            latestSessionState = dying.latestSessionState,
            latestSessionStateUrl = dying.latestSessionStateUrl,
            persistedSessionState = dying.persistedSessionState,
            persistedSessionStateUrl = dying.persistedSessionStateUrl,
        )
        runtime.webExtensionController.setTabActive(dying.session, false)
        dying.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        closeIfOpen(dying.session)
        _tabs.value = _tabs.value - dying
        if (snapshot.wasCurrent) {
            val next = _tabs.value.getOrNull(idx.coerceAtMost(_tabs.value.size - 1))
            if (next != null) {
                select(next.id)
            } else {
                currentId.value = null
            }
        }
        persist()
        return snapshot
    }

    fun restoreClosedTab(snapshot: ClosedTabSnapshot): Tab? {
        if (closed || _tabs.value.any { it.id == snapshot.id }) return null
        val tab = Tab(GeckoSession(sessionSettings(snapshot.isPrivate)), snapshot.id, snapshot.isPrivate).apply {
            url = snapshot.url
            title = snapshot.title
            desktop = snapshot.desktop
            lastAccess = snapshot.lastAccess
            latestSessionState = snapshot.latestSessionState
            latestSessionStateUrl = snapshot.latestSessionStateUrl
            persistedSessionState = snapshot.persistedSessionState
            persistedSessionStateUrl = snapshot.persistedSessionStateUrl
            restoreUrlOnOpen = true
        }
        seq = maxOf(seq, tab.id)
        attachDelegates(tab)
        val restoredTabs = _tabs.value.toMutableList()
        restoredTabs.add(snapshot.index.coerceIn(0, restoredTabs.size), tab)
        _tabs.value = restoredTabs

        if (snapshot.wasCurrent || currentId.value == null) {
            deactivateOthers(tab.id)
            currentId.value = tab.id
            openTab(tab)
        }
        persist()
        enforceHotTabBudget()
        return tab
    }

    fun current(): Tab? = _tabs.value.firstOrNull { it.id == currentId.value }

    private fun isCurrentPermissionSession(session: GeckoSession): Boolean =
        isCurrentPermissionRequestTab(
            requestTabId = _tabs.value.firstOrNull { it.session === session }?.id,
            currentTabId = currentId.value,
        )

    fun setAppVisible(visible: Boolean) {
        if (closed) return
        appVisible = visible
        if (visible) backgroundTrimRequested = false
        _tabs.value.filter { it.session.isOpen }.forEach { tab ->
            val active = visible && tab.id == currentId.value
            tab.session.setActive(active)
            tab.session.setFocused(active)
            tab.session.setPriorityHint(if (active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT)
        }
    }

    fun trimForBackground() {
        if (closed) return
        backgroundTrimRequested = true
        enforceHotTabBudget()
    }

    private fun effectiveHotTabLimit(): Int =
        if (backgroundTrimRequested) backgroundHotTabLimit else hotTabLimit

    private fun enforceHotTabBudget() {
        if (closed) return
        val targetLimit = effectiveHotTabLimit()
        var openCount = _tabs.value.count { it.session.isOpen }
        if (openCount <= targetLimit) return

        val selectedId = currentId.value
        val coldest = _tabs.value
            .asSequence()
            .filter { it.id != selectedId && it.session.isOpen }
            .sortedBy { it.lastAccess }
            .toList()
        for (tab in coldest) {
            if (openCount <= targetLimit) break
            if (tab.progress >= 0f) continue
            val hasRestorableState = tab.url.isBlank() || tab.url == "about:blank" ||
                (tab.latestSessionState != null && tab.latestSessionStateUrl == tab.url)
            if (!hasRestorableState) continue
            hibernateTab(tab)
            openCount--
        }
    }

    private fun hibernateTab(tab: Tab) {
        if (!tab.session.isOpen || tab.id == currentId.value) return
        runtime.webExtensionController.setTabActive(tab.session, false)
        tab.session.setFocused(false)
        tab.session.setActive(false)
        tab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        closeIfOpen(tab.session)
        tab.restoreUrlOnOpen = true
    }

    suspend fun clearWebData() {
        if (closed) return
        val clearRequest = clearGeneration.incrementAndGet()
        _tabs.value.forEach { tab ->
            runtime.webExtensionController.setTabActive(tab.session, false)
            tab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
            closeIfOpen(tab.session)
        }
        _tabs.value = emptyList()
        currentId.value = null

        val clearRevision = TabStore.nextRevision(storeDir)
        withContext(Dispatchers.IO) {
            TabStore.saveStateVersioned(storeDir, PersistedBrowserState(), clearRevision)
        }

        val restoreBlankTab = {
            if (shouldCreateBlankTabAfterClear(
                    requestGeneration = clearRequest,
                    currentGeneration = clearGeneration.get(),
                    hasTabs = _tabs.value.isNotEmpty(),
                    isClosed = closed,
                )
            ) {
                newTab(null)
            }
        }
        val clearResult = runtime.storageController.clearData(StorageController.ClearFlags.ALL)
        suspendCancellableCoroutine<Unit> { continuation ->
            clearResult.accept(
                {
                    restoreBlankTab()
                    if (continuation.isActive) continuation.resume(Unit)
                },
                { error ->
                    val failure = error ?: IllegalStateException("Gecko storage clear failed")
                    Log.e("MinibrowserTabs", "Failed to clear web data", failure)
                    restoreBlankTab()
                    if (continuation.isActive) continuation.resumeWithException(failure)
                },
            )
        }
    }

    fun persist() {
        requestPersist(immediate = true)
    }

    fun close() {
        if (closed) return
        val finalSnapshot = capturePersistenceSnapshot()
        val finalRevision = TabStore.nextRevision(storeDir)
        closed = true
        persistJob.cancel()
        runCatching {
            TabStore.saveStateVersioned(storeDir, serializePersistenceSnapshot(finalSnapshot), finalRevision)
        }.onFailure { Log.e("MinibrowserTabs", "Failed to persist final tab metadata", it) }
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        _tabs.value.forEach { tab ->
            runCatching { runtime.webExtensionController.setTabActive(tab.session, false) }
            runCatching { tab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT) }
            closeIfOpen(tab.session)
        }
    }

    private fun requestPersist(immediate: Boolean) {
        if (!closed) {
            val revision = TabStore.nextRevision(storeDir)
            persistRevision.updateAndGet { current -> maxOf(current, revision) }
            persistRequests.send(if (immediate) PersistSignal.Immediate else PersistSignal.Dirty)
        }
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
        Trace.beginSection(TAB_RESTORE_MATERIALIZE_TRACE)
        val restoredTabs = try {
            saved.tabs.map { createTab(private = false, persisted = it, publish = false) }
        } finally {
            Trace.endSection()
        }
        _tabs.value = restoredTabs
        if (restoredTabs.isEmpty()) {
            newTab(null)
        } else {
            val selected = saved.selectedId?.takeIf { id -> restoredTabs.any { it.id == id } }
                ?: restoredTabs.maxByOrNull { it.lastAccess }!!.id
            currentId.value = selected
            Trace.beginSection(TAB_RESTORE_OPEN_SELECTED_TRACE)
            try {
                restoredTabs.first { it.id == selected }.let(::openTab)
            } finally {
                Trace.endSection()
            }
        }
    }

    private fun attachDelegates(tab: Tab) {
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.progressGate.accept(progress = 5)
                tab.loadError = null
                tab.securityState = SecurityState.Unknown
                tab.progress = 0.05f
                tab.historyTitleUrl = null
                tab.url = url
                tab.title = ""
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.progress = -1f
                if (tab.id != currentId.value && _tabs.value.count { it.session.isOpen } > effectiveHotTabLimit()) {
                    enforceHotTabBudget()
                }
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
                val stateUrl = currentSessionStateUrl(state)
                tab.latestSessionState = state
                tab.latestSessionStateUrl = stateUrl
                if (stateUrl != null && stateUrl == tab.url) {
                    tab.persistedSessionState = null
                    tab.persistedSessionStateUrl = null
                }
                if (!tab.isPrivate) requestPersist(immediate = false)
                if (tab.id != currentId.value && _tabs.value.count { it.session.isOpen } > effectiveHotTabLimit()) {
                    enforceHotTabBudget()
                }
            }
        }

        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                triggeredByUser: Boolean,
            ) {
                val nextUrl = url.orEmpty()
                if (nextUrl != tab.url) tab.historyTitleUrl = null
                tab.url = nextUrl
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MinibrowserNavigation",
                        "load uri=${navigationDebugLabel(request.uri)} target=${request.target} trigger=${navigationDebugLabel(request.triggerUri)} userGesture=${request.hasUserGesture} redirect=${request.isRedirect}",
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
                error: WebRequestError,
            ): GeckoResult<String>? {
                tab.loadError = pageLoadErrorForCategory(error.category)
                return GeckoResult.fromValue(null)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (BuildConfig.DEBUG) {
                    Log.d("MinibrowserNavigation", "new session uri=${navigationDebugLabel(uri)}")
                }
                if (!isAllowedPopupTarget(uri)) return null
                return GeckoResult.fromValue(newWindowSession(tab.isPrivate))
            }
        }

        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                val historyUrl = tab.historyTitleUrl
                if (!tab.isPrivate && historyUrl != null) HistorySink.updateTitle(historyUrl, title)
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
                    controller.handle(response, tab.isPrivate)
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
                    tab.historyTitleUrl = url
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

    private fun deactivateTab(tab: Tab) {
        runtime.webExtensionController.setTabActive(tab.session, false)
        tab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        if (tab.session.isOpen) {
            tab.session.setFocused(false)
            tab.session.setActive(false)
        }
    }

    private fun deactivateOthers(selectedId: Long) {
        val previousId = currentId.value ?: return
        if (previousId == selectedId) return
        _tabs.value.firstOrNull { it.id == previousId }?.let(::deactivateTab)
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
        tab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        closeIfOpen(tab.session)
        tab.canGoBack = false
        tab.canGoForward = false
        val fresh = GeckoSession(sessionSettings(tab.isPrivate))
        tab.session = fresh
        attachDelegates(tab)
        fresh.open(runtime)
        applyDesktop(tab)
        val active = wasActive && appVisible
        fresh.setActive(active)
        fresh.setFocused(active)
        fresh.setPriorityHint(if (active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT)
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
