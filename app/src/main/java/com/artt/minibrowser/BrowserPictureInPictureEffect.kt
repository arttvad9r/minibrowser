package com.artt.minibrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.browser.BrowserPictureInPictureController
import com.artt.minibrowser.browser.BrowserPictureInPicturePlaybackState
import com.artt.minibrowser.browser.pictureInPictureMediaStateForTab
import com.artt.minibrowser.engine.RawSessionOwnership
import com.artt.minibrowser.engine.TabManager
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession

internal fun pictureInPicturePlaybackStateForOwnership(
    ownership: RawSessionOwnership?,
    rawPlayback: BrowserPictureInPicturePlaybackState,
    linkedPlayback: BrowserPictureInPicturePlaybackState,
): BrowserPictureInPicturePlaybackState = when (ownership) {
    RawSessionOwnership.Owned -> rawPlayback
    RawSessionOwnership.Relinquished -> linkedPlayback
    null -> BrowserPictureInPicturePlaybackState()
}

/**
 * Maps BrowserStore content state plus media state from the tab's current session owner into Android
 * PiP without taking ownership of playback. PiP remains disabled for private tabs to preserve
 * FLAG_SECURE. MiniBrowser keeps its custom platform controller so aspect ratio, source rect,
 * seamless resize and auto-enter behavior remain unchanged across the ownership boundary.
 */
@Composable
internal fun BrowserPictureInPictureEffect(
    tabManager: TabManager,
    browserStore: BrowserStore,
    controller: BrowserPictureInPictureController,
) {
    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val currentId by tabManager.currentId.collectAsStateWithLifecycle()
    val browserStoreState by browserStore.stateFlow.collectAsStateWithLifecycle()
    val currentTab = tabs.firstOrNull { it.id == currentId }
    val currentStoreTab = browserStoreState.tabs
        .firstOrNull { it.id == currentId?.toString() }
    val currentContent = currentStoreTab?.content
    val privateTab = currentContent?.private ?: (currentTab?.isPrivate == true)
    val contentFullscreen = currentContent?.fullScreen ?: (currentTab?.fullscreen == true)
    val rawMediaPlayback = currentTab?.mediaPlaybackState
    val linkedMediaSession = currentStoreTab?.mediaSessionState
    val mediaPlayback = pictureInPicturePlaybackStateForOwnership(
        ownership = currentTab?.rawSessionOwnership,
        rawPlayback = BrowserPictureInPicturePlaybackState(
            playing = rawMediaPlayback?.playing == true,
            videoWidth = rawMediaPlayback?.videoWidth ?: 0L,
            videoHeight = rawMediaPlayback?.videoHeight ?: 0L,
        ),
        linkedPlayback = BrowserPictureInPicturePlaybackState(
            playing = linkedMediaSession?.playbackState == MediaSession.PlaybackState.PLAYING,
            videoWidth = linkedMediaSession?.elementMetadata?.width ?: 0L,
            videoHeight = linkedMediaSession?.elementMetadata?.height ?: 0L,
        ),
    )

    SideEffect {
        controller.update(
            pictureInPictureMediaStateForTab(
                contentFullscreen = contentFullscreen,
                privateTab = privateTab,
                playback = mediaPlayback,
            ),
        )
    }
}
