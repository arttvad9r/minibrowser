package com.artt.minibrowser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.engine.BrowserApp
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import mozilla.components.browser.engine.gecko.GeckoEngineView
import mozilla.components.browser.state.action.TabListAction
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPopupLiveCutoverSystemTest {
    @Test
    fun popupTransfersAfterGeckoOpensReturnedSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val app = targetApp()
        val launchObservation = AtomicReference("MainActivity was not created")
        val lifecycleCallbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainActivity) {
                    launchObservation.set(
                        "action=${activity.intent?.action}, data=${activity.intent?.dataString}, " +
                            "savedInstanceState=${savedInstanceState != null}",
                    )
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)

        try {
            LocalPopupServer().use { server ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(server.pageUrl),
                    targetContext,
                    MainActivity::class.java,
                )
                ActivityScenario.launch<MainActivity>(intent).use {
                    val pageReady = waitUntil(LINK_TIMEOUT_MS) { server.pageReady }
                    assertTrue(
                        "Initial popup parent executed its local DOM readiness script in Gecko; " +
                            "launch=${launchObservation.get()}; " +
                            app.browserStore.state.let { state ->
                                "selected=${state.selectedTabId}, tabs=" +
                                    state.tabs.joinToString(prefix = "[", postfix = "]") { tab ->
                                        "${tab.id}{url=${tab.content.url}, linked=${tab.engineState.engineSession != null}, " +
                                            "loading=${tab.content.loading}}"
                                    }
                            },
                        pageReady,
                    )
                    assertTrue(
                        "Initial popup parent is owned by a linked Android Components session",
                        waitUntil(LINK_TIMEOUT_MS) {
                            selectedTab(app)?.engineState?.engineSession != null
                        },
                    )
                    assertTrue(
                        "Diagnostic persisted tab ID survived restore and ACTION_VIEW received the next ID",
                        app.browserStore.state.let { state ->
                            state.selectedTabId == (DIAGNOSTIC_TAB_ID + 1L).toString() &&
                                state.tabs.any { it.id == DIAGNOSTIC_TAB_ID.toString() }
                        },
                    )

                    val initialIds = app.browserStore.state.tabs.mapTo(mutableSetOf()) { it.id }
                    onView(isAssignableFrom(GeckoEngineView::class.java)).check { view, _ ->
                        assertTrue(
                            "Gecko content is still occluded before tap; " +
                                "storeUrl=${selectedTab(app)?.content?.url}",
                            view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                        )
                    }
                    onView(isAssignableFrom(GeckoEngineView::class.java)).perform(click())

                    assertTrue(
                        "Tap reached the current popup parent page",
                        waitUntil(LINK_TIMEOUT_MS) { server.popupClicked },
                    )
                    assertTrue(
                        "Gecko opened the requested child page in the popup session",
                        waitUntil(LINK_TIMEOUT_MS) { server.childRequested },
                    )
                    assertTrue(
                        "Gecko popup becomes the selected linked Android Components tab",
                        waitUntil(LINK_TIMEOUT_MS) {
                            val state = app.browserStore.state
                            val popup = state.tabs.singleOrNull { it.id !in initialIds }
                            popup != null &&
                                state.selectedTabId == popup.id &&
                                popup.engineState.engineSession != null
                        },
                    )
                }
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }

    private fun selectedTab(app: BrowserApp) = app.browserStore.state.let { state ->
        state.tabs.firstOrNull { it.id == state.selectedTabId }
    }

    private class LocalPopupServer : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName(LOOPBACK_HOST))
        private val running = AtomicBoolean(true)
        private val ready = AtomicBoolean(false)
        private val clicked = AtomicBoolean(false)
        private val child = AtomicBoolean(false)
        private val worker = Thread(::acceptLoop, "popup-cutover-test-server").apply {
            isDaemon = true
            start()
        }

        val pageUrl: String = "http://$LOOPBACK_HOST:${server.localPort}/"
        val pageReady: Boolean
            get() = ready.get()
        val popupClicked: Boolean
            get() = clicked.get()
        val childRequested: Boolean
            get() = child.get()

        override fun close() {
            if (!running.getAndSet(false)) return
            runCatching { server.close() }
            runCatching { worker.join(SERVER_JOIN_TIMEOUT_MS) }
        }

        private fun acceptLoop() {
            while (running.get()) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                Thread(
                    { runCatching { socket.use(::handle) } },
                    "popup-cutover-test-connection",
                ).apply {
                    isDaemon = true
                    start()
                }
            }
        }

        private fun handle(socket: Socket) {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val reader = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
            val requestLine = reader.readLine() ?: return
            val method = requestLine.substringBefore(' ')
            val path = requestLine.substringAfter(' ', "").substringBefore(' ').substringBefore('?')
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            when (path) {
                "/" -> writeResponse(socket, method, "200 OK", PAGE_BYTES)
                "/ready" -> {
                    writeResponse(socket, method, "200 OK", READY_BYTES)
                    ready.set(true)
                }
                "/clicked" -> {
                    writeResponse(socket, method, "200 OK", CLICKED_BYTES)
                    clicked.set(true)
                }
                "/child" -> {
                    writeResponse(socket, method, "200 OK", CHILD_BYTES)
                    child.set(true)
                }
                else -> writeResponse(socket, method, "404 Not Found", NOT_FOUND_BYTES)
            }
        }

        private fun writeResponse(
            socket: Socket,
            method: String,
            status: String,
            body: ByteArray,
        ) {
            val output = socket.getOutputStream()
            val headers = buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
            output.write(headers)
            if (!method.equals("HEAD", ignoreCase = true)) output.write(body)
            output.flush()
        }
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val LINK_TIMEOUT_MS = 10_000L
        const val RESET_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 25L
        const val SERVER_JOIN_TIMEOUT_MS = 1_000L
        const val SOCKET_TIMEOUT_MS = 5_000
        const val DIAGNOSTIC_TAB_ID = 10_000L

        val PAGE_BYTES = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>
                html, body { margin: 0; width: 100%; height: 100%; }
                button { position: fixed; inset: 0; border: 0; font-size: 32px; }
              </style>
            </head>
            <body>
              <button onclick="openPopup()">Open popup</button>
              <script>
                function openPopup() {
                  fetch('/clicked', { cache: 'no-store', keepalive: true }).catch(() => {});
                  window.open('/child', '_blank');
                }
                requestAnimationFrame(() => requestAnimationFrame(() => {
                  fetch('/ready', { cache: 'no-store' }).catch(() => {});
                }));
              </script>
            </body>
            </html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val CHILD_BYTES = """
            <!doctype html>
            <html><body>Popup child</body></html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val READY_BYTES = "ready".toByteArray(StandardCharsets.UTF_8)
        val CLICKED_BYTES = "clicked".toByteArray(StandardCharsets.UTF_8)
        val NOT_FOUND_BYTES = "not found".toByteArray(StandardCharsets.UTF_8)

        @JvmStatic
        @BeforeClass
        fun prepareIsolatedBrowserState() {
            resetBrowserState()
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            TabStore.saveState(
                File(targetContext.filesDir, "tabs"),
                PersistedBrowserState(
                    selectedId = DIAGNOSTIC_TAB_ID,
                    tabs = listOf(
                        PersistedTab(
                            id = DIAGNOSTIC_TAB_ID,
                            url = "about:blank",
                        ),
                    ),
                ),
            )
        }

        @JvmStatic
        @AfterClass
        fun clearIsolatedBrowserState() {
            resetBrowserState()
        }

        fun targetApp(): BrowserApp =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BrowserApp

        fun resetBrowserState() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = instrumentation.targetContext
            val app = targetContext.applicationContext as BrowserApp
            instrumentation.runOnMainSync {
                val tabIds = app.browserStore.state.tabs.map { it.id }
                if (tabIds.isNotEmpty()) {
                    app.browserStore.dispatch(TabListAction.RemoveTabsAction(tabIds))
                }
            }

            val deadline = SystemClock.uptimeMillis() + RESET_TIMEOUT_MS
            while (app.browserStore.state.tabs.isNotEmpty() && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
            check(app.browserStore.state.tabs.isEmpty()) { "BrowserStore did not reset before test" }
            TabStore.saveState(File(targetContext.filesDir, "tabs"), PersistedBrowserState())
        }

        fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (SystemClock.uptimeMillis() < deadline) {
                if (condition()) return true
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
            return condition()
        }
    }
}
