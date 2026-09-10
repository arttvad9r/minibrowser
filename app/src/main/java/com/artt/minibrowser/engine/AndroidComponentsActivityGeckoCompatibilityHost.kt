package com.artt.minibrowser.engine

import android.app.Activity
import android.net.Uri
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

/**
 * Activity-scoped compatibility UI retained while BrowserStore owns transferred EngineSessions.
 *
 * Tab/session lifetime ownership stays outside this class. Callers provide BrowserStore selection
 * and tab-opening functions, so compatibility actions never rediscover or reclaim raw ownership.
 */
internal class AndroidComponentsActivityGeckoCompatibilityHost(
    private val activity: Activity,
    private val selectedSessionId: () -> String?,
    requestPermissions: ((Array<String>, (Boolean) -> Unit) -> Unit)?,
    pickFiles: ((Int, Array<String>, (Array<Uri>) -> Unit) -> Unit)?,
    openTab: (String, Boolean) -> Unit,
    openBackgroundTab: (String, Boolean) -> Unit,
    private val openWindowSession: (Boolean) -> GeckoSession,
    private val closeWindowTab: (String) -> Boolean = { false },
) : AndroidComponentsGeckoCompatibilityHost {
    private val promptController by lazy(LazyThreadSafetyMode.NONE) {
        GeckoPromptController(activity, pickFiles)
    }
    private val permissionController by lazy(LazyThreadSafetyMode.NONE) {
        // Every transferred-session permission callback supplies BrowserStore identity explicitly.
        GeckoPermissionController(activity, requestPermissions) { false }
    }
    private val downloadController by lazy(LazyThreadSafetyMode.NONE) {
        GeckoDownloadController(activity, requestPermissions)
    }
    private val contextMenuController by lazy(LazyThreadSafetyMode.NONE) {
        GeckoContextMenuController(
            activity = activity,
            openTab = openTab,
            openBackgroundTab = openBackgroundTab,
        )
    }

    override fun promptDelegate(
        context: AndroidComponentsGeckoSessionContext,
    ): GeckoSession.PromptDelegate? =
        // Raw TabManager installs one GeckoPromptController on every owned session without a
        // selected-tab predicate. Keep that behavior across transfer; the registry supplies only
        // the current Activity instance, never session lifetime authority.
        promptController.takeIf { canShowUi() }

    override fun onAndroidPermissionsRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) = permissionController.handleAndroidPermissionsRequest(permissions, callback) {
        context.isSelected(selectedSessionId())
    }

    override fun onContentPermissionRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> = permissionController.handleContentPermissionRequest(permission) {
        context.isSelected(selectedSessionId())
    }

    override fun onMediaPermissionRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        uri: String,
        video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) = permissionController.handleMediaPermissionRequest(uri, video, audio, callback) {
        context.isSelected(selectedSessionId())
    }

    override fun onExternalResponse(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        response: WebResponse,
    ): Boolean {
        // GeckoDownloadController owns/always closes the authenticated body once handle() begins.
        // This intentionally matches the raw path, where downloads are not selected-tab gated.
        downloadController.handle(response, context.privateMode)
        return true
    }

    override fun onNewSession(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        uri: String,
    ): GeckoResult<GeckoSession>? {
        if (!canShowUi() || !isAllowedPopupTarget(uri)) return null
        // Gecko itself loads uri into the newly-created session after this callback returns. Use the
        // raw-compatible factory rather than newTab(uri) to avoid issuing a duplicate navigation.
        return GeckoResult.fromValue(openWindowSession(context.privateMode))
    }

    override fun onCloseRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
    ) {
        // This is structural lifetime work, not UI. Resolve by immutable session identity even when
        // the Activity is paused; a missing/stale TabManager mapping simply fails closed.
        closeWindowTab(context.sessionId)
    }

    override fun onLinkedMediaContextMenu(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        element: GeckoSession.ContentDelegate.ContextElement,
    ): Boolean {
        if (!canShowUi()) return false
        contextMenuController.show(element, context.privateMode)
        return true
    }

    private fun canShowUi(): Boolean = !activity.isFinishing && !activity.isDestroyed
}
