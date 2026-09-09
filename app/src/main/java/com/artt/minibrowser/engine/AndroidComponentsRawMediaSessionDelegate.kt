package com.artt.minibrowser.engine

import mozilla.components.concept.engine.mediasession.MediaSession as ConceptMediaSession
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession as GeckoMediaSession

/**
 * Raw-owner media delegate prepared for the final ownership boundary.
 *
 * It preserves MiniBrowser's current lightweight PiP playback snapshot while also retaining the
 * GeckoView MediaSession object and concept-engine metadata needed for an identity-preserving A-C
 * handoff. Nothing in the current raw path instantiates this class yet.
 */
internal class AndroidComponentsRawMediaSessionDelegate(
    private val ownerSession: GeckoSession,
    private val stillOwnsSession: () -> Boolean,
    private val onPlaybackSnapshotChanged: (TabMediaPlaybackState) -> Unit = {},
) : GeckoMediaSession.Delegate {
    var playbackSnapshot: TabMediaPlaybackState = TabMediaPlaybackState()
        private set

    private var handoffState = AndroidComponentsRawMediaSessionHandoffState<GeckoMediaSession>()

    fun reset() {
        playbackSnapshot = TabMediaPlaybackState()
        handoffState = AndroidComponentsRawMediaSessionHandoffState()
        onPlaybackSnapshotChanged(playbackSnapshot)
    }

    fun handoffSnapshot(): AndroidComponentsMediaSessionHandoff? =
        handoffState.toHandoff(::AndroidComponentsTransferredMediaSessionController)

    private fun owns(session: GeckoSession): Boolean = session === ownerSession && stillOwnsSession()

    override fun onActivated(session: GeckoSession, mediaSession: GeckoMediaSession) {
        if (!owns(session)) return
        handoffState = rawMediaSessionActivated(mediaSession)
    }

    override fun onDeactivated(session: GeckoSession, mediaSession: GeckoMediaSession) {
        if (!owns(session) || handoffState.activeSession !== mediaSession) return
        handoffState = rawMediaSessionDeactivated(handoffState, mediaSession)
        playbackSnapshot = TabMediaPlaybackState()
        onPlaybackSnapshotChanged(playbackSnapshot)
    }

    override fun onPlay(session: GeckoSession, mediaSession: GeckoMediaSession) {
        if (!owns(session)) return
        handoffState = rawMediaSessionPlayed(handoffState, mediaSession)
        playbackSnapshot = playbackSnapshot.copy(playing = true)
        onPlaybackSnapshotChanged(playbackSnapshot)
    }

    override fun onPause(session: GeckoSession, mediaSession: GeckoMediaSession) {
        if (!owns(session) || handoffState.activeSession !== mediaSession) return
        handoffState = rawMediaSessionPaused(handoffState, mediaSession)
        playbackSnapshot = playbackSnapshot.copy(playing = false)
        onPlaybackSnapshotChanged(playbackSnapshot)
    }

    override fun onStop(session: GeckoSession, mediaSession: GeckoMediaSession) {
        if (!owns(session) || handoffState.activeSession !== mediaSession) return
        handoffState = rawMediaSessionStopped(handoffState, mediaSession)
        playbackSnapshot = playbackSnapshot.copy(playing = false)
        onPlaybackSnapshotChanged(playbackSnapshot)
    }

    override fun onFullscreen(
        session: GeckoSession,
        mediaSession: GeckoMediaSession,
        enabled: Boolean,
        meta: GeckoMediaSession.ElementMetadata?,
    ) {
        if (!owns(session)) return
        val conceptMetadata = meta?.toConceptMediaElementMetadata()
        handoffState = rawMediaSessionFullscreenChanged(
            state = handoffState,
            session = mediaSession,
            fullScreen = enabled,
            elementMetadata = conceptMetadata,
        )

        val fullscreenVideo = enabled && (meta == null || meta.videoTrackCount > 0)
        playbackSnapshot = tabMediaPlaybackStateWithVideoMetadata(
            state = playbackSnapshot,
            fullscreenVideo = fullscreenVideo,
            width = meta?.width,
            height = meta?.height,
        )
        onPlaybackSnapshotChanged(playbackSnapshot)
    }
}

internal fun GeckoMediaSession.ElementMetadata.toConceptMediaElementMetadata(): ConceptMediaSession.ElementMetadata =
    ConceptMediaSession.ElementMetadata(
        source = source,
        duration = duration,
        width = width,
        height = height,
        audioTrackCount = audioTrackCount,
        videoTrackCount = videoTrackCount,
    )
