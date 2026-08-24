package com.artt.minibrowser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.artt.minibrowser.data.HistorySink
import com.artt.minibrowser.data.TabStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.io.File

class Tab(session: GeckoSession, val id: Long, val isPrivate: Boolean) {
    // Compose state: смена сессии при краш-восстановлении должна перерисовать AndroidView.
    var session: GeckoSession by mutableStateOf(session)
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    var canGoBack by mutableStateOf(false)
}

class TabManager(private val runtime: GeckoRuntime, storeDir: File) {
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs get() = _tabs
    val currentId = MutableStateFlow<Long?>(null)
    private var seq = 0L

    init {
        if (TabStore.dir == null) TabStore.dir = storeDir
        restore()
    }

    fun newTab(url: String?, private: Boolean = false): Tab {
        val s = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(private)
                .build()
        )
        val tab = Tab(s, ++seq, private)
        attachDelegates(tab)
        _tabs.value += tab
        currentId.value = tab.id
        s.open(runtime)
        url?.let(s::loadUri)
        return tab
    }

    fun select(id: Long) { currentId.value = id }

    fun closeTab(id: Long) {
        val idx = _tabs.value.indexOfFirst { it.id == id }
        if (idx < 0) return
        val dying = _tabs.value[idx]
        dying.session.stop(); dying.session.close()
        _tabs.value = _tabs.value - dying
        if (currentId.value == id) {
            val next = _tabs.value.getOrNull(idx.coerceAtMost(_tabs.value.size - 1))
            if (next != null) currentId.value = next.id else newTab(null)
        }
        persist()
    }

    fun current(): Tab? = _tabs.value.firstOrNull { it.id == currentId.value }

    // Приватные и ещё не начавшие загрузку вкладки не сохраняются.
    fun persist() =
        TabStore.save(_tabs.value.filter { !it.isPrivate && it.url.isNotBlank() }.map { it.url })

    private fun restore() {
        TabStore.load().forEach { newTab(it) }
        if (_tabs.value.isEmpty()) newTab(null)
    }

    private fun attachDelegates(tab: Tab) {
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.progress = 0.05f; tab.url = url
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.progress = -1f
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress / 100f
            }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {}
        }
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>, triggeredByUser: Boolean) {
                tab.url = url.orEmpty()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? = null
        }
        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                if (!tab.isPrivate) HistorySink.updateTitle(tab.url, title)
            }
            override fun onCrash(session: GeckoSession) {
                // Восстановление крашнутой сессии с тем же URL.
                val url = tab.url
                session.close()
                val fresh = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(tab.isPrivate).build())
                fresh.open(runtime)
                tab.session = fresh
                attachDelegates(tab)
                fresh.loadUri(url.ifEmpty { "about:blank" })
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
    }
}
