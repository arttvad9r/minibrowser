package com.artt.minibrowser

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPictureInPicturePlaybackSystemTest {
    @Test
    fun geckoFullscreenVideoDrivesRealSystemPictureInPicture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val browserApp = targetContext.applicationContext as BrowserApp
        val video = testContext.assets.open(TEST_VIDEO_ASSET).use { it.readBytes() }

        resetBrowserState(instrumentation, browserApp)
        try {
            LocalMediaServer(video).use { server ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(server.pageUrl)).apply {
                    setClass(targetContext, MainActivity::class.java)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val scenario = ActivityScenario.launch<MainActivity>(intent)
                try {
                    waitFor("The local Gecko media page became selected") {
                        selectedContent(browserApp)?.url == server.pageUrl
                    }

                    val tapPoint = AtomicReference<Pair<Float, Float>>()
                    scenario.onActivity { activity ->
                        val decor = activity.window.decorView
                        tapPoint.set(decor.width * 0.5f to decor.height * 0.45f)
                    }
                    SystemClock.sleep(PAGE_SETTLE_MS)
                    sendTap(instrumentation, tapPoint.get())

                    waitFor("Gecko reported content fullscreen from the real video element") {
                        selectedContent(browserApp)?.fullScreen == true
                    }

                    val requested = AtomicBoolean(false)
                    val requestDeadline = SystemClock.uptimeMillis() + MEDIA_READY_TIMEOUT_MS
                    while (!requested.get() && SystemClock.uptimeMillis() < requestDeadline) {
                        scenario.onActivity { activity -> requested.set(activity.onPictureInPictureRequested()) }
                        if (!requested.get()) SystemClock.sleep(POLL_INTERVAL_MS)
                    }
                    assertTrue(
                        "Real Gecko media playback made MainActivity PiP-eligible",
                        requested.get(),
                    )

                    waitFor("MainActivity entered system picture-in-picture") {
                        val inPip = AtomicBoolean(false)
                        scenario.onActivity { activity -> inPip.set(activity.isInPictureInPictureMode) }
                        inPip.get()
                    }

                    waitFor("The platform PiP callback was mirrored into BrowserStore") {
                        selectedContent(browserApp)?.pictureInPictureEnabled == true
                    }
                } finally {
                    scenario.close()
                }
            }
        } finally {
            resetBrowserState(instrumentation, browserApp)
        }
    }

    private fun selectedContent(browserApp: BrowserApp) = browserApp.browserStore.state.let { state ->
        state.tabs.firstOrNull { it.id == state.selectedTabId }?.content
    }

    private fun resetBrowserState(
        instrumentation: android.app.Instrumentation,
        browserApp: BrowserApp,
    ) {
        val targetContext = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val tabIds = browserApp.browserStore.state.tabs.map { it.id }
            if (tabIds.isNotEmpty()) {
                browserApp.browserStore.dispatch(TabListAction.RemoveTabsAction(tabIds))
            }
        }

        val deadline = SystemClock.uptimeMillis() + RESET_TIMEOUT_MS
        while (browserApp.browserStore.state.tabs.isNotEmpty() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(25L)
        }
        check(browserApp.browserStore.state.tabs.isEmpty()) { "BrowserStore did not reset before PiP test" }
        TabStore.saveState(File(targetContext.filesDir, "tabs"), PersistedBrowserState())
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

    private fun waitFor(
        message: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        assertTrue(message, condition())
    }

    private class LocalMediaServer(
        private val video: ByteArray,
    ) : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName(LOOPBACK_HOST))
        private val running = AtomicBoolean(true)
        private val worker = Thread(::acceptLoop, "pip-media-test-server").apply {
            isDaemon = true
            start()
        }

        val pageUrl: String = "http://$LOOPBACK_HOST:${server.localPort}/"

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
            val requestParts = requestLine.split(' ')
            val method = requestParts.getOrNull(0).orEmpty()
            val path = requestParts.getOrNull(1).orEmpty().substringBefore('?')
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] =
                        line.substring(separator + 1).trim()
                }
            }

            when (path) {
                "/" -> writeResponse(
                    socket = socket,
                    method = method,
                    status = "200 OK",
                    contentType = "text/html; charset=utf-8",
                    body = PAGE_BYTES,
                )
                "/pip.webm" -> writeVideoResponse(socket, method, headers["range"])
                else -> writeResponse(
                    socket = socket,
                    method = method,
                    status = "404 Not Found",
                    contentType = "text/plain; charset=utf-8",
                    body = "not found".toByteArray(StandardCharsets.UTF_8),
                )
            }
        }

        private fun writeVideoResponse(
            socket: Socket,
            method: String,
            rangeHeader: String?,
        ) {
            val range = parseRange(rangeHeader, video.size)
            if (range == null) {
                writeResponse(
                    socket = socket,
                    method = method,
                    status = "200 OK",
                    contentType = "video/webm",
                    body = video,
                    extraHeaders = listOf("Accept-Ranges: bytes"),
                )
                return
            }

            val body = video.copyOfRange(range.first, range.last + 1)
            writeResponse(
                socket = socket,
                method = method,
                status = "206 Partial Content",
                contentType = "video/webm",
                body = body,
                extraHeaders = listOf(
                    "Accept-Ranges: bytes",
                    "Content-Range: bytes ${range.first}-${range.last}/${video.size}",
                ),
            )
        }

        private fun parseRange(header: String?, size: Int): IntRange? {
            if (header == null || !header.startsWith("bytes=")) return null
            val spec = header.removePrefix("bytes=").substringBefore(',')
            val start = spec.substringBefore('-').toIntOrNull() ?: return null
            if (start !in 0 until size) return null
            val requestedEnd = spec.substringAfter('-', "").toIntOrNull()
            val end = (requestedEnd ?: size - 1).coerceIn(start, size - 1)
            return start..end
        }

        private fun writeResponse(
            socket: Socket,
            method: String,
            status: String,
            contentType: String,
            body: ByteArray,
            extraHeaders: List<String> = emptyList(),
        ) {
            val output = socket.getOutputStream()
            val headers = buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${body.size}\r\n")
                extraHeaders.forEach { append(it).append("\r\n") }
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
        const val TEST_VIDEO_ASSET = "pip_test.webm"
        const val LOOPBACK_HOST = "127.0.0.1"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        // MediaSession playback callbacks can lag fullscreen on heavily loaded CI emulators. This
        // test validates eventual real Gecko -> PiP behavior, not a playback-start latency SLA.
        const val MEDIA_READY_TIMEOUT_MS = 25_000L
        const val POLL_INTERVAL_MS = 100L
        const val PAGE_SETTLE_MS = 300L
        const val TAP_DURATION_MS = 50L
        const val SERVER_JOIN_TIMEOUT_MS = 1_000L
        const val SOCKET_TIMEOUT_MS = 5_000
        const val RESET_TIMEOUT_MS = 5_000L

        val PAGE_BYTES = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>
                html, body { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
                video { width: 100%; height: 100%; object-fit: contain; background: #000; }
                #start { position: fixed; inset: 0; z-index: 10; border: 0; font-size: 32px; }
              </style>
            </head>
            <body>
              <video id="video" preload="auto" loop playsinline src="/pip.webm"></video>
              <button id="start" onclick="startPlayback()">Start video</button>
              <script>
                function startPlayback() {
                  const video = document.getElementById('video');
                  document.getElementById('start').remove();
                  const playResult = video.play();
                  if (playResult) playResult.catch(error => document.title = 'play-error:' + error.name);
                  if (video.requestFullscreen) {
                    video.requestFullscreen().catch(error => document.title = 'fullscreen-error:' + error.name);
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
    }
}
