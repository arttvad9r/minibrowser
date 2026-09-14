package com.artt.minibrowser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.MediaSessionAction
import mozilla.components.concept.engine.mediasession.MediaSession
import org.mozilla.geckoview.MediaSession as GeckoViewMediaSession

/**
 * Public concept-engine controller adapter for an already-active GeckoView media session.
 *
 * A-C's equivalent GeckoMediaSessionController is internal. The explicit raw -> A-C ownership
 * transfer therefore cannot instantiate it directly, but the concept interface and GeckoView media
 * controls are both public and map one-to-one.
 */
internal class AndroidComponentsTransferredMediaSessionController(
    private val mediaSession: GeckoViewMediaSession,
) : MediaSession.Controller {
    override fun pause() = mediaSession.pause()
    override fun stop() = mediaSession.stop()
    override fun play() = mediaSession.play()
    override fun seekTo(time: Double, fast: Boolean) = mediaSession.seekTo(time, fast)
    override fun seekForward() = mediaSession.seekForward()
    override fun seekBackward() = mediaSession.seekBackward()
    override fun nextTrack() = mediaSession.nextTrack()
    override fun previousTrack() = mediaSession.previousTrack()
    override fun skipAd() = mediaSession.skipAd()
    override fun muteAudio(mute: Boolean) = mediaSession.muteAudio(mute)
}

/**
 * Media state captured by the raw owner immediately before an identity-preserving session transfer.
 * This exists only to bridge callbacks GeckoView does not replay when GeckoEngineSession replaces the
 * media-session delegate on an already-open GeckoSession.
 */
internal data class AndroidComponentsMediaSessionHandoff(
    val controller: MediaSession.Controller,
    val playbackState: MediaSession.PlaybackState = MediaSession.PlaybackState.UNKNOWN,
    val fullscreen: Boolean = false,
    val elementMetadata: MediaSession.ElementMetadata? = null,
)

/**
 * Replays the minimum ordered BrowserStore media state after the existing session has been linked.
 * Activation must be first because A-C reducers intentionally ignore playback/fullscreen updates when
 * no MediaSessionState exists yet.
 */
internal fun androidComponentsMediaSessionHandoffActions(
    tabId: String,
    handoff: AndroidComponentsMediaSessionHandoff?,
): List<BrowserAction> {
    if (handoff == null) return emptyList()

    return buildList {
        add(MediaSessionAction.ActivatedMediaSessionAction(tabId, handoff.controller))
        if (handoff.playbackState != MediaSession.PlaybackState.UNKNOWN) {
            add(MediaSessionAction.UpdateMediaPlaybackStateAction(tabId, handoff.playbackState))
        }
        if (handoff.fullscreen || handoff.elementMetadata != null) {
            add(
                MediaSessionAction.UpdateMediaFullscreenAction(
                    tabId = tabId,
                    fullScreen = handoff.fullscreen,
                    elementMetadata = handoff.elementMetadata,
                ),
            )
        }
    }
}
