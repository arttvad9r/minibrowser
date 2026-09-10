package com.artt.minibrowser.engine

import android.app.Activity
import android.net.Uri
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

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
) : AndroidComponentsGeckoCompatibilityHost {
    private val promptController by lazy(LazyThreadSafetyMode.NONE) {
        GeckoPromptController(activity, pickFiles)
    }
    private val permissionController by lazy(LazyThreadSafetyMode.NONE) {
        // Every transferred-session permission callback supplies BrowserStore identity explicitly.
        GeckoPermissionController(activity, requestPermissions) { false }
    }
    private val contextMenuController by lazy(LazyThreadSafetyMode.NONE) {
        GeckoContextMenuController(
            activity = activity,
            openTab = openTab,
            openBackgroundTab = openBackgroundTab,
        )
    }

    override fun onWeekPrompt(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        if (!canShowUi()) return null
        return promptController.onDateTimePrompt(session, prompt)
    }

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
