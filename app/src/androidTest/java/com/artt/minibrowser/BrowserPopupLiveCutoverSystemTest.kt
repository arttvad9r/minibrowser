package com.artt.minibrowser

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
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
import mozilla.components.browser.state.action.TabListAction
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPopupLiveCutoverSystemTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun geckoOpenedPopupRetriesAndTransfersToAndroidComponents() {
        val app = targetApp()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        LocalPopupServer().use { server ->
            composeRule.waitUntil(LINK_TIMEOUT_MS) {
                selectedTab(app)?.engineState?.engineSession != null
            }
            composeRule.waitForIdle()

            composeRule.runOnUiThread {
                composeRule.activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(server.pageUrl),
                        composeRule.activity,
                        MainActivity::class.java,
                    ),
                )
            }
            composeRule.waitUntil(LINK_TIMEOUT_MS) {
                selectedTab(app)?.let { tab ->
                    tab.content.url == server.pageUrl && tab.engineState.engineSession != null
                } == true
            }

            val initialIds = app.browserStore.state.tabs.mapTo(mutableSetOf()) { it.id }
            val tapPoint = AtomicReference<Pair<Float, Float>>()
            composeRule.runOnUiThread {
                val decor = composeRule.activity.window.decorView
                tapPoint.set(decor.width * 0.5f to decor.height * 0.5f)
            }
            SystemClock.sleep(PAGE_SETTLE_MS)
            sendTap(instrumentation, tapPoint.get())

            composeRule.waitUntil(LINK_TIMEOUT_MS) {
                val state = app.browserStore.state
                val popup = state.tabs.singleOrNull { it.id !in initialIds }
                popup != null &&
                    state.selectedTabId == popup.id &&
                    popup.content.url == server.popupUrl &&
                    popup.engineState.engineSession != null
            }

            val state = app.browserStore.state
            val popup = state.tabs.single { it.id !in initialIds }
            assertTrue(
                "Gecko-opened popup is linked to Android Components after its first raw content update",
                popup.engineState.engineSession != null,
            )
        }
    }

    private fun selectedTab(app: BrowserApp) = app.browserStore.state.let { state ->
        state.tabs.firstOrNull { it.id == state.selectedTabId }
    }

    private fun sendTap(
        instrumentation: android.app.Instrumentation,
        point: Pair<Float, Float>,
    ) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            point.first,
            point.second,
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(
            downTime,
            downTime + TAP_DURATION_MS,
            MotionEvent.ACTION_UP,
            point.first,
            point.second,
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            instrumentation.sendPointerSync(down)
            SystemClock.sleep(TAP_DURATION_MS)
            instrumentation.sendPointerSync(up)
            instrumentation.waitForIdleSync()
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private class LocalPopupServer : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName(LOOPBACK_HOST))
        private val running = AtomicBoolean(true)
        private val worker = Thread(::acceptLoop, "popup-cutover-test-server").apply {
            isDaemon = true
            start()
        }

        val pageUrl: String = "http://$LOOPBACK_HOST:${server.localPort}/"
        val popupUrl: String = "http://$LOOPBACK_HOST:${server.localPort}/popup"

        override fun close() {
            if (!running.getAndSet(false)) return
            runCatching { server.close() }
            runCatching { worker.join(SERVER_JOIN_TIMEOUT_MS) }
        }

        private fun acceptLoop() {
            while (running.get()) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                runCatching { socket.use(::handle) }
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

            val body = when (path) {
                "/" -> PAGE_BYTES
                "/popup" -> POPUP_BYTES
                else -> NOT_FOUND_BYTES
            }
            val status = if (path == "/" || path == "/popup") "200 OK" else "404 Not Found"
            writeResponse(socket, method, status, body)
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
        const val PAGE_SETTLE_MS = 300L
        const val TAP_DURATION_MS = 50L
        const val SERVER_JOIN_TIMEOUT_MS = 1_000L
        const val SOCKET_TIMEOUT_MS = 5_000

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
              <button onclick="window.open('/popup', '_blank')">Open popup</button>
            </body>
            </html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val POPUP_BYTES = """
            <!doctype html>
            <html><body>Popup ownership cutover</body></html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val NOT_FOUND_BYTES = "not found".toByteArray(StandardCharsets.UTF_8)

        @JvmStatic
        @BeforeClass
        fun prepareIsolatedBrowserState() {
            resetBrowserState()
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
                SystemClock.sleep(25L)
            }
            check(app.browserStore.state.tabs.isEmpty()) { "BrowserStore did not reset before test" }
            TabStore.saveState(File(targetContext.filesDir, "tabs"), PersistedBrowserState())
        }
    }
}
