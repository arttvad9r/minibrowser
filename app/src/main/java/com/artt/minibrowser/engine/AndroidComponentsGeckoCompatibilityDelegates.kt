package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * GeckoView callbacks retained by MiniBrowser while BrowserStore owns the surrounding EngineSession.
 *
 * WEEK and linked-media context menus are lossy in A-C 154. Permission callbacks intentionally stay
 * on MiniBrowser's existing policy/UI until a BrowserStore permission feature is introduced with
 * equivalent policy and ActivityResult semantics.
 */
internal interface AndroidComponentsGeckoCompatibilityHandler {
    fun onWeekPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>?

    fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    )

    fun onContentPermissionRequest(
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>

    fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    )

    /** Returns true when the raw-compatible context menu consumed the event. */
    fun onLinkedMediaContextMenu(
        session: GeckoSession,
        element: GeckoSession.ContentDelegate.ContextElement,
    ): Boolean
}

internal fun shouldUseRawWeekPrompt(type: Int): Boolean =
    type == GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK

internal fun shouldUseRawLinkedMediaContextMenu(
    elementType: Int,
    linkUri: String?,
    srcUri: String?,
): Boolean =
    linkUri != null &&
        srcUri != null &&
        (elementType == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO ||
            elementType == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO)

/**
 * Keeps stock A-C prompt ownership except for WEEK. In A-C 154 GeckoPromptDelegate formats WEEK as
 * yyyy-'W'ww and then collapses it to PromptRequest.TimeSelection.Type.DATE, losing the original
 * HTML input type before PromptFeature can apply MiniBrowser's ISO week-year behavior.
 */
internal class AndroidComponentsPromptCompatibilityDelegate(
    private val delegate: GeckoSession.PromptDelegate,
    private val compatibility: AndroidComponentsGeckoCompatibilityHandler,
) : GeckoSession.PromptDelegate by delegate {
    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        if (shouldUseRawWeekPrompt(prompt.type)) {
            compatibility.onWeekPrompt(session, prompt)?.let { return it }
        }
        return delegate.onDateTimePrompt(session, prompt)
    }
}

/**
 * Retains MiniBrowser's permission policy/UI across the EngineSession ownership boundary.
 *
 * A-C's stock Gecko permission delegate produces BrowserStore PermissionRequests, but this migration
 * intentionally has no second SitePermissionsFeature consumer yet. Forwarding there would leave
 * requests pending and would also bypass MiniBrowser's current site-policy and serialized
 * ActivityResult handling. All three Gecko permission callback families therefore stay on the
 * Activity-scoped compatibility host and fail closed if that host is unavailable.
 */
internal class AndroidComponentsPermissionCompatibilityDelegate(
    private val delegate: GeckoSession.PermissionDelegate,
    private val compatibility: AndroidComponentsGeckoCompatibilityHandler,
) : GeckoSession.PermissionDelegate by delegate {
    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) = compatibility.onAndroidPermissionsRequest(session, permissions, callback)

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> = compatibility.onContentPermissionRequest(session, perm)

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) = compatibility.onMediaPermissionRequest(session, uri, video, audio, callback)
}

/**
 * Keeps stock A-C content ownership except for linked audio/video context menus. A-C 154 maps
 * TYPE_AUDIO/TYPE_VIDEO to HitResult using srcUri only, dropping linkUri; MiniBrowser needs both to
 * offer distinct link and media actions. Plain media and all other context menus remain stock A-C.
 */
internal class AndroidComponentsContentCompatibilityDelegate(
    private val delegate: GeckoSession.ContentDelegate,
    private val compatibility: AndroidComponentsGeckoCompatibilityHandler,
) : GeckoSession.ContentDelegate by delegate {
    override fun onContextMenu(
        session: GeckoSession,
        screenX: Int,
        screenY: Int,
        element: GeckoSession.ContentDelegate.ContextElement,
    ) {
        val consumed = shouldUseRawLinkedMediaContextMenu(
            elementType = element.type,
            linkUri = element.linkUri,
            srcUri = element.srcUri,
        ) && compatibility.onLinkedMediaContextMenu(session, element)

        if (!consumed) {
            delegate.onContextMenu(session, screenX, screenY, element)
        }
    }
}
