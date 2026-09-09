package com.artt.minibrowser.engine

import mozilla.components.concept.engine.mediasession.MediaSession

/**
 * Raw-owner media state that must survive an identity-preserving GeckoSession ownership transfer.
 *
 * GeckoView may not replay activation/playback callbacks when GeckoEngineSession installs its media
 * delegate on an already-open session. Keep this state engine-neutral and replay it only after the
 * existing session has been linked into BrowserStore.
 */
internal data class AndroidComponentsRawMediaSessionHandoffState<T : Any>(
    val activeSession: T? = null,
    val playbackState: MediaSession.PlaybackState = MediaSession.PlaybackState.UNKNOWN,
    val fullScreen: Boolean = false,
    val elementMetadata: MediaSession.ElementMetadata? = null,
) {
    fun toHandoff(
        controllerFactory: (T) -> MediaSession.Controller,
    ): AndroidComponentsMediaSessionHandoff? = activeSession?.let { session ->
        AndroidComponentsMediaSessionHandoff(
            controller = controllerFactory(session),
            playbackState = playbackState,
            fullScreen = fullScreen,
            elementMetadata = elementMetadata,
        )
    }
}

internal fun <T : Any> rawMediaSessionActivated(
    session: T,
): AndroidComponentsRawMediaSessionHandoffState<T> =
    AndroidComponentsRawMediaSessionHandoffState(activeSession = session)

internal fun <T : Any> rawMediaSessionPlayed(
    state: AndroidComponentsRawMediaSessionHandoffState<T>,
    session: T,
): AndroidComponentsRawMediaSessionHandoffState<T> =
    if (state.activeSession === session) {
        state.copy(playbackState = MediaSession.PlaybackState.PLAYING)
    } else {
        AndroidComponentsRawMediaSessionHandoffState(
            activeSession = session,
            playbackState = MediaSession.PlaybackState.PLAYING,
        )
    }

internal fun <T : Any> rawMediaSessionPaused(
    state: AndroidComponentsRawMediaSessionHandoffState<T>,
    session: T,
): AndroidComponentsRawMediaSessionHandoffState<T> =
    if (state.activeSession === session) {
        state.copy(playbackState = MediaSession.PlaybackState.PAUSED)
    } else {
        state
    }

internal fun <T : Any> rawMediaSessionStopped(
    state: AndroidComponentsRawMediaSessionHandoffState<T>,
    session: T,
): AndroidComponentsRawMediaSessionHandoffState<T> =
    if (state.activeSession === session) {
        state.copy(playbackState = MediaSession.PlaybackState.STOPPED)
    } else {
        state
    }

internal fun <T : Any> rawMediaSessionFullscreenChanged(
    state: AndroidComponentsRawMediaSessionHandoffState<T>,
    session: T,
    fullScreen: Boolean,
    elementMetadata: MediaSession.ElementMetadata?,
): AndroidComponentsRawMediaSessionHandoffState<T> =
    if (state.activeSession === session) {
        state.copy(
            fullScreen = fullScreen,
            elementMetadata = elementMetadata,
        )
    } else {
        AndroidComponentsRawMediaSessionHandoffState(
            activeSession = session,
            fullScreen = fullScreen,
            elementMetadata = elementMetadata,
        )
    }

internal fun <T : Any> rawMediaSessionDeactivated(
    state: AndroidComponentsRawMediaSessionHandoffState<T>,
    session: T,
): AndroidComponentsRawMediaSessionHandoffState<T> =
    if (state.activeSession === session) {
        AndroidComponentsRawMediaSessionHandoffState()
    } else {
        state
    }
