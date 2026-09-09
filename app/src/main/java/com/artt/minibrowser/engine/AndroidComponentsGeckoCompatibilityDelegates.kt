package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * The narrow GeckoView callbacks that Android Components 154 cannot represent without losing
 * MiniBrowser semantics. Implementations may delegate back to the existing raw controllers while
 * BrowserStore owns the surrounding EngineSession.
 */
internal interface AndroidComponentsGeckoCompatibilityHandler {
    fun onWeekPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>?

    fun onXrPermission(
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>?

    /** Returns true when the raw-compatible context menu consumed the event. */
    fun onLinkedMediaContextMenu(
        session: GeckoSession,
        element: GeckoSession.ContentDelegate.ContextElement,
    ): Boolean
}

internal fun shouldUseRawWeekPrompt(type: Int): Boolean =
    type == GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK

internal fun shouldUseRawXrPermission(permission: Int): Boolean =
    permission == GeckoSession.PermissionDelegate.PERMISSION_XR

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
 * Keeps stock A-C permission ownership except for XR. A-C 154 has no XR entry in
 * GeckoPermissionRequest.Content.permissionsMap, so PERMISSION_XR becomes a generic permission and
 * cannot retain MiniBrowser's XR-specific policy/UI contract.
 */
internal class AndroidComponentsPermissionCompatibilityDelegate(
    private val delegate: GeckoSession.PermissionDelegate,
    private val compatibility: AndroidComponentsGeckoCompatibilityHandler,
) : GeckoSession.PermissionDelegate by delegate {
    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>? {
        if (shouldUseRawXrPermission(perm.permission)) {
            compatibility.onXrPermission(session, perm)?.let { return it }
        }
        return delegate.onContentPermissionRequest(session, perm)
    }
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
