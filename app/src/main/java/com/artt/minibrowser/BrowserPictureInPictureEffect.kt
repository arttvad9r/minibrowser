package com.artt.minibrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.browser.BrowserPictureInPictureController
import com.artt.minibrowser.browser.BrowserPictureInPicturePlaybackState
import com.artt.minibrowser.browser.pictureInPictureMediaStateForTab
import com.artt.minibrowser.engine.TabManager
import mozilla.components.browser.state.store.BrowserStore

/**
 * Maps BrowserStore content state plus the raw-owner media handoff snapshot into Android PiP without
 * taking ownership of playback. PiP remains disabled for private tabs to preserve FLAG_SECURE.
 * Playing/video geometry intentionally stays on the MiniBrowser media snapshot because stock A-C
 * PiP does not preserve the current aspect-ratio/source-rect behavior.
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
    val currentContent = browserStoreState.tabs
        .firstOrNull { it.id == currentId?.toString() }
        ?.content
    val privateTab = currentContent?.private ?: (currentTab?.isPrivate == true)
    val contentFullscreen = currentContent?.fullScreen ?: (currentTab?.fullscreen == true)
    val mediaPlayback = currentTab?.mediaPlaybackState

    SideEffect {
        controller.update(
            pictureInPictureMediaStateForTab(
                contentFullscreen = contentFullscreen,
                privateTab = privateTab,
                playback = BrowserPictureInPicturePlaybackState(
                    playing = mediaPlayback?.playing == true,
                    videoWidth = mediaPlayback?.videoWidth ?: 0L,
                    videoHeight = mediaPlayback?.videoHeight ?: 0L,
                ),
            ),
        )
    }
}
