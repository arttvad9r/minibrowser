package com.artt.minibrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.browser.BrowserPictureInPictureController
import com.artt.minibrowser.browser.BrowserPictureInPictureMediaState
import com.artt.minibrowser.engine.TabManager
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/**
 * Bridges Gecko media-session callbacks into Android PiP without taking ownership of playback.
 * PiP is deliberately disabled for private tabs to preserve the app's FLAG_SECURE privacy model.
 */
@Composable
internal fun BrowserPictureInPictureEffect(
    tabManager: TabManager,
    controller: BrowserPictureInPictureController,
) {
    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val currentId by tabManager.currentId.collectAsStateWithLifecycle()
    val currentTab = tabs.firstOrNull { it.id == currentId }
    val session = currentTab?.session
    val privateTab = currentTab?.isPrivate == true

    var mediaState by remember(session, privateTab) {
        mutableStateOf(BrowserPictureInPictureMediaState(privateTab = privateTab))
    }

    DisposableEffect(session, privateTab) {
        if (session == null) {
            controller.update(BrowserPictureInPictureMediaState(privateTab = privateTab))
            onDispose { }
        } else {
            var disposed = false
            var activeMediaSession: MediaSession? = null
            val delegate = object : MediaSession.Delegate {
                override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                    if (disposed) return
                    activeMediaSession = mediaSession
                }

                override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                    if (disposed || activeMediaSession !== mediaSession) return
                    activeMediaSession = null
                    mediaState = BrowserPictureInPictureMediaState(privateTab = privateTab)
                }

                override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                    if (disposed) return
                    activeMediaSession = mediaSession
                    mediaState = mediaState.copy(playing = true)
                }

                override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                    if (disposed || activeMediaSession !== mediaSession) return
                    mediaState = mediaState.copy(playing = false)
                }

                override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                    if (disposed || activeMediaSession !== mediaSession) return
                    mediaState = mediaState.copy(playing = false)
                }

                override fun onFullscreen(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                    enabled: Boolean,
                    meta: MediaSession.ElementMetadata?,
                ) {
                    if (disposed) return
                    activeMediaSession = mediaSession
                    val isVideo = enabled && (meta == null || meta.videoTrackCount > 0)
                    mediaState = mediaState.copy(
                        fullscreenVideo = isVideo,
                        videoWidth = if (isVideo) meta?.width ?: 0L else 0L,
                        videoHeight = if (isVideo) meta?.height ?: 0L else 0L,
                    )
                }
            }

            session.setMediaSessionDelegate(delegate)
            onDispose {
                disposed = true
                if (session.getMediaSessionDelegate() === delegate) {
                    session.setMediaSessionDelegate(null)
                }
                controller.update(BrowserPictureInPictureMediaState(privateTab = privateTab))
            }
        }
    }

    SideEffect {
        controller.update(mediaState)
    }
}
