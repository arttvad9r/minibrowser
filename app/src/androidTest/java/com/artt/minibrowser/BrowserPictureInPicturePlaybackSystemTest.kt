package com.artt.minibrowser

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
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
import mozilla.components.browser.engine.gecko.GeckoEngineView
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
                    waitFor("The local Gecko media page executed its DOM readiness script") {
                        server.pageReady
                    }

                    onView(isAssignableFrom(GeckoEngineView::class.java)).perform(click())

                    waitFor("Tap reached the Gecko media page") {
                        server.clicked
                    }
                    waitFor(
                        "Fullscreen request did not settle after the page click",
                        MEDIA_READY_TIMEOUT_MS,
                    ) {
                        server.fullscreenSucceeded || server.fullscreenFailed
                    }
                    assertTrue(
                        "Fullscreen API rejected the real video request; " +
                            "playSucceeded=${server.playSucceeded}, playFailed=${server.playFailed}",
                        server.fullscreenSucceeded,
                    )
                    waitFor(
                        "Video play() did not settle after the page click",
                        MEDIA_READY_TIMEOUT_MS,
                    ) {
                        server.playSucceeded || server.playFailed
                    }
                    assertTrue(
                        "Video play() rejected after a real page click",
                        server.playSucceeded,
                    )

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
        private val ready = AtomicBoolean(false)
        private val clickedMarker = AtomicBoolean(false)
        private val playSucceededMarker = AtomicBoolean(false)
        private val playFailedMarker = AtomicBoolean(false)
        private val fullscreenSucceededMarker = AtomicBoolean(false)
        private val fullscreenFailedMarker = AtomicBoolean(false)
        private val worker = Thread(::acceptLoop, "pip-media-test-server").apply {
            isDaemon = true
            start()
        }

        val pageUrl: String = "http://$LOOPBACK_HOST:${server.localPort}/"
        val pageReady: Boolean
            get() = ready.get()
        val clicked: Boolean
            get() = clickedMarker.get()
        val playSucceeded: Boolean
            get() = playSucceededMarker.get()
        val playFailed: Boolean
            get() = playFailedMarker.get()
        val fullscreenSucceeded: Boolean
            get() = fullscreenSucceededMarker.get()
        val fullscreenFailed: Boolean
            get() = fullscreenFailedMarker.get()

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
                    "pip-media-test-connection",
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
                "/ready" -> {
                    writeResponse(
                        socket = socket,
                        method = method,
                        status = "200 OK",
                        contentType = "text/plain; charset=utf-8",
                        body = READY_BYTES,
                    )
                    ready.set(true)
                }
                "/clicked" -> writeMarkerResponse(socket, method, clickedMarker)
                "/play-ok" -> writeMarkerResponse(socket, method, playSucceededMarker)
                "/play-error" -> writeMarkerResponse(socket, method, playFailedMarker)
                "/fullscreen-ok" -> writeMarkerResponse(socket, method, fullscreenSucceededMarker)
                "/fullscreen-error" -> writeMarkerResponse(socket, method, fullscreenFailedMarker)
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

        private fun writeMarkerResponse(
            socket: Socket,
            method: String,
            marker: AtomicBoolean,
        ) {
            writeResponse(
                socket = socket,
                method = method,
                status = "200 OK",
                contentType = "text/plain; charset=utf-8",
                body = MARKER_BYTES,
            )
            marker.set(true)
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
                function mark(path) {
                  fetch(path, { cache: 'no-store', keepalive: true }).catch(() => {});
                }
                function startPlayback() {
                  mark('/clicked');
                  const video = document.getElementById('video');
                  document.getElementById('start').remove();
                  const playResult = video.play();
                  if (playResult) {
                    playResult.then(() => mark('/play-ok')).catch(() => mark('/play-error'));
                  } else {
                    mark('/play-ok');
                  }
                  if (video.requestFullscreen) {
                    video.requestFullscreen()
                      .then(() => mark('/fullscreen-ok'))
                      .catch(() => mark('/fullscreen-error'));
                  } else {
                    mark('/fullscreen-error');
                  }
                }
                requestAnimationFrame(() => requestAnimationFrame(() => {
                  fetch('/ready', { cache: 'no-store' }).catch(() => {});
                }));
              </script>
            </body>
            </html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val READY_BYTES = "ready".toByteArray(StandardCharsets.UTF_8)
        val MARKER_BYTES = "ok".toByteArray(StandardCharsets.UTF_8)
    }
}
