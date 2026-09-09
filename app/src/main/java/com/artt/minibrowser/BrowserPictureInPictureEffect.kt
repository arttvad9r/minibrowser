package com.artt.minibrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.browser.BrowserPictureInPictureController
import com.artt.minibrowser.browser.BrowserPictureInPicturePlaybackState
import com.artt.minibrowser.browser.pictureInPictureMediaStateForTab
import com.artt.minibrowser.engine.TabManager

/**
 * Maps TabManager-owned Gecko media state into Android PiP without taking ownership of playback.
 * PiP is deliberately disabled for private tabs to preserve the app's FLAG_SECURE privacy model.
 * Browser fullscreen comes from TabManager's ContentDelegate state, matching A-C's PiP contract.
 */
@Composable
internal fun BrowserPictureInPictureEffect(
    tabManager: TabManager,
    controller: BrowserPictureInPictureController,
) {
    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val currentId by tabManager.currentId.collectAsStateWithLifecycle()
    val currentTab = tabs.firstOrNull { it.id == currentId }
    val privateTab = currentTab?.isPrivate == true
    val contentFullscreen = currentTab?.fullscreen == true
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
