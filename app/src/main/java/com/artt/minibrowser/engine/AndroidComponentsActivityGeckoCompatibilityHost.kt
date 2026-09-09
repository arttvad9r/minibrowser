package com.artt.minibrowser.engine

import android.app.Activity
import android.net.Uri
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Activity-scoped UI implementation for the three lossy GeckoView callbacks retained at cutover.
 *
 * Tab/session ownership stays outside this class. Callers provide BrowserStore selection and tab
 * opening functions, so linked-media actions do not fall back to MainActivity/TabManager once the
 * surrounding EngineSession is A-C-owned.
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
        // The A-C compatibility path supplies its own BrowserStore session-id predicate below.
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

    override fun onXrPermission(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> = permissionController.handleContentPermissionRequest(permission) {
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
