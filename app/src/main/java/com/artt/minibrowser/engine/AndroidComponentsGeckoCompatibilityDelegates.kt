package com.artt.minibrowser.engine

import android.view.PointerIcon
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.SlowScriptResponse
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

/**
 * GeckoView callbacks retained by MiniBrowser while BrowserStore owns the surrounding EngineSession.
 *
 * Prompt callbacks intentionally keep the existing MiniBrowser prompt policy/UI until structural tab
 * ownership moves away from TabManager. A-C's PromptFeature can open tabs through TabsUseCases, which
 * would otherwise create BrowserStore-only tabs during this transitional ownership phase. Permission
 * callbacks likewise stay on MiniBrowser's existing policy/UI until an equivalent BrowserStore
 * permission feature is introduced. Download responses now remain with stock GeckoEngineSession and
 * flow through BrowserStore. New-window and close requests stay on TabManager's structural path so
 * stock A-C cannot create or retain untracked window state. Linked-media context menus remain here
 * because A-C 154 drops the wrapping link URI for audio/video hit results.
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

    /** Legacy download bridge retained until the BrowserStore cutover is fully validated. */
    fun onExternalResponse(
        session: GeckoSession,
        response: WebResponse,
    )

    /** Returns a structurally-owned popup session, or null to fail the web-content request closed. */
    fun onNewSession(
        session: GeckoSession,
        uri: String,
    ): GeckoResult<GeckoSession>? = null

    /** Consumes window.close without falling through to an unconsumed BrowserStore WindowRequest. */
    fun onCloseRequest(session: GeckoSession) = Unit

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
 * Prevents stock GeckoEngineSession.onNewSession() from creating an EngineSession outside
 * TabManager's structural ownership during the transitional live-transfer phase. All other stock
 * navigation callbacks are forwarded explicitly because GeckoView exposes them as Java defaults.
 */
internal class AndroidComponentsNewSessionCompatibilityDelegate(
    private val delegate: GeckoSession.NavigationDelegate,
    private val compatibility: AndroidComponentsGeckoCompatibilityHandler,
) : GeckoSession.NavigationDelegate {
    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
    ) = delegate.onLocationChange(session, url, perms, hasUserGesture)

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) =
        delegate.onCanGoBack(session, canGoBack)

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) =
        delegate.onCanGoForward(session, canGoForward)

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? = delegate.onLoadRequest(session, request)

    override fun onSubframeLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? = delegate.onSubframeLoadRequest(session, request)

    override fun onNewSession(
        session: GeckoSession,
        uri: String,
    ): GeckoResult<GeckoSession>? = compatibility.onNewSession(session, uri)

    override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError,
    ): GeckoResult<String>? = delegate.onLoadError(session, uri, error)
}

/**
 * Keeps stock A-C content ownership except for semantics that are not yet safely consumable after
 * transfer: window-close requests and linked media context menus. GeckoView content callbacks are
 * Java defaults, so non-owned callbacks must be forwarded explicitly.
 */
internal class AndroidComponentsContentCompatibilityDelegate(
    private val delegate: GeckoSession.ContentDelegate,
    private val compatibility: AndroidComponentsGeckoCompatibilityHandler,
) : GeckoSession.ContentDelegate {
    override fun onTitleChange(session: GeckoSession, title: String?) =
        delegate.onTitleChange(session, title)

    override fun onPreviewImage(session: GeckoSession, previewImageUrl: String) =
        delegate.onPreviewImage(session, previewImageUrl)

    override fun onFocusRequest(session: GeckoSession) = delegate.onFocusRequest(session)

    override fun onCloseRequest(session: GeckoSession) {
        // A-C only publishes a BrowserStore WindowRequest here. MiniBrowser has no TabsFeature
        // consumer during this ownership phase, so forwarding would silently stop window.close().
        compatibility.onCloseRequest(session)
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) =
        delegate.onFullScreen(session, fullScreen)

    override fun onMetaViewportFitChange(session: GeckoSession, viewportFit: String) =
        delegate.onMetaViewportFitChange(session, viewportFit)

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

    override fun onExternalResponse(
        session: GeckoSession,
        response: WebResponse,
    ) = delegate.onExternalResponse(session, response)

    override fun onCrash(session: GeckoSession) = delegate.onCrash(session)

    override fun onKill(session: GeckoSession) = delegate.onKill(session)

    override fun onFirstComposite(session: GeckoSession) = delegate.onFirstComposite(session)

    override fun onFirstContentfulPaint(session: GeckoSession) =
        delegate.onFirstContentfulPaint(session)

    override fun onPaintStatusReset(session: GeckoSession) = delegate.onPaintStatusReset(session)

    override fun onPointerIconChange(session: GeckoSession, icon: PointerIcon) =
        delegate.onPointerIconChange(session, icon)

    override fun onWebAppManifest(session: GeckoSession, manifest: JSONObject) =
        delegate.onWebAppManifest(session, manifest)

    override fun onSlowScript(
        geckoSession: GeckoSession,
        scriptFileName: String,
    ): GeckoResult<SlowScriptResponse>? = delegate.onSlowScript(geckoSession, scriptFileName)

    override fun onShowDynamicToolbar(geckoSession: GeckoSession) =
        delegate.onShowDynamicToolbar(geckoSession)

    override fun onHideDynamicToolbar(geckoSession: GeckoSession) =
        delegate.onHideDynamicToolbar(geckoSession)
}
