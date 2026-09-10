package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * GeckoView callbacks retained by MiniBrowser while BrowserStore owns the surrounding EngineSession.
 *
 * Prompt callbacks intentionally keep the existing MiniBrowser prompt policy/UI until structural tab
 * ownership moves away from TabManager. A-C's PromptFeature can open tabs through TabsUseCases, which
 * would otherwise create BrowserStore-only tabs during this transitional ownership phase. Permission
 * callbacks likewise stay on MiniBrowser's existing policy/UI until an equivalent BrowserStore
 * permission feature is introduced. Linked-media context menus remain here because A-C 154 drops the
 * wrapping link URI for audio/video hit results.
 */
internal interface AndroidComponentsGeckoCompatibilityHandler {
    /** Resolves the current Activity-scoped raw-compatible prompt owner, or null between hosts. */
    fun promptDelegate(): GeckoSession.PromptDelegate?

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
 * Retains the current MiniBrowser prompt owner across the EngineSession ownership boundary.
 *
 * GeckoPromptController overrides the same 14 callbacks below on the raw path. The remaining
 * GeckoView PromptDelegate callbacks intentionally keep their interface defaults, matching the raw
 * controller's current behavior for autocomplete, identity-credential and certificate requests.
 * Returning null while no Activity host is bound also keeps those requests out of BrowserStore; it
 * never falls through to A-C's stock GeckoPromptDelegate without a PromptFeature consumer.
 */
internal class AndroidComponentsPromptCompatibilityDelegate(
    private val compatibility: AndroidComponentsGeckoCompatibilityHandler,
) : GeckoSession.PromptDelegate {
    private fun current(): GeckoSession.PromptDelegate? = compatibility.promptDelegate()

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onAlertPrompt(session, prompt)

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onTextPrompt(session, prompt)

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onButtonPrompt(session, prompt)

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onAuthPrompt(session, prompt)

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onChoicePrompt(session, prompt)

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onBeforeUnloadPrompt(session, prompt)

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onRepostConfirmPrompt(session, prompt)

    override fun onFolderUploadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FolderUploadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onFolderUploadPrompt(session, prompt)

    override fun onRedirectPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RedirectPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onRedirectPrompt(session, prompt)

    override fun onSharePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.SharePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onSharePrompt(session, prompt)

    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onDateTimePrompt(session, prompt)

    override fun onColorPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ColorPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onColorPrompt(session, prompt)

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onPopupPrompt(session, prompt)

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = current()?.onFilePrompt(session, prompt)
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
