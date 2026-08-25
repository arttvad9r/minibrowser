package com.artt.minibrowser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.SystemClock
import com.artt.minibrowser.data.HistorySink
import com.artt.minibrowser.data.TabStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
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

class Tab(session: GeckoSession, val id: Long, val isPrivate: Boolean) {
    // Compose state: смена сессии при краш-восстановлении должна перерисовать AndroidView.
    var session: GeckoSession by mutableStateOf(session)
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    var canGoBack by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    internal val progressGate = ProgressGate()
}

class TabManager(
    private val runtime: GeckoRuntime,
    private val storeDir: File,
    private val context: android.content.Context,
) {
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs get() = _tabs
    val currentId = MutableStateFlow<Long?>(null)
    private var seq = 0L

    init {
        restore()
    }

    fun newTab(url: String?, private: Boolean = false): Tab {
        val s = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(private)
                .suspendMediaWhenInactive(true)
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
        TabStore.save(storeDir, _tabs.value.filter { !it.isPrivate && it.url.isNotBlank() }.map { it.url })

    private fun restore() {
        TabStore.load(storeDir).forEach { newTab(it) }
        if (_tabs.value.isEmpty()) newTab(null)
    }

    private fun attachDelegates(tab: Tab) {
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.progressGate.accept(progress = 5)
                tab.progress = 0.05f; tab.url = url
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.progress = -1f
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (tab.progressGate.accept(progress = progress)) tab.progress = progress / 100f
            }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {}
        }
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>, triggeredByUser: Boolean) {
                tab.url = url.orEmpty()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack }
            // target="_blank"/window.open (так открывают результаты поисковиков) — новая вкладка с автопереключением.
            // Возвращённую сессию открываем сами, uri в неё грузит сам GeckoView.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val s = GeckoSession(
                    GeckoSessionSettings.Builder()
                        .usePrivateMode(tab.isPrivate)
                        .suspendMediaWhenInactive(true)
                        .build()
                )
                val t = Tab(s, ++seq, tab.isPrivate)
                attachDelegates(t)
                _tabs.value += t
                currentId.value = t.id
                s.open(runtime)
                return GeckoResult.fromValue(s)
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
            }
            override fun onCrash(session: GeckoSession) {
                // Восстановление крашнутой сессии с тем же URL.
                val url = tab.url
                session.close()
                val fresh = GeckoSession(
                    GeckoSessionSettings.Builder()
                        .usePrivateMode(tab.isPrivate)
                        .suspendMediaWhenInactive(true)
                        .build()
                )
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
