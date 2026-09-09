package com.artt.minibrowser.engine

import java.io.Closeable
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** Immutable metadata that remains valid when the Activity hosting compatibility UI is recreated. */
internal data class AndroidComponentsGeckoSessionContext(
    val sessionId: String,
    val privateMode: Boolean,
) {
    fun isSelected(selectedSessionId: String?): Boolean = sessionId == selectedSessionId
}

/**
 * Activity-scoped implementation of the small compatibility surface that A-C 154 cannot represent
 * losslessly. The session context is supplied by the future A-C-owned session factory rather than
 * rediscovered from a raw GeckoSession or a retained TabManager.
 */
internal interface AndroidComponentsGeckoCompatibilityHost {
    fun onWeekPrompt(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>?

    fun onXrPermission(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>?

    fun onLinkedMediaContextMenu(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        element: GeckoSession.ContentDelegate.ContextElement,
    ): Boolean
}

/**
 * App-scoped indirection for the compatibility UI host.
 *
 * EngineSessions keep a session-bound handler created by [forSession]. That handler captures only
 * immutable tab metadata and resolves the currently bound Activity host on every callback. Closing
 * an older Activity lease cannot clear a replacement binding installed during recreation.
 */
internal class AndroidComponentsGeckoCompatibilityRegistry {
    @Volatile
    private var current: AndroidComponentsGeckoCompatibilityHost? = null

    fun bind(host: AndroidComponentsGeckoCompatibilityHost): Closeable {
        synchronized(this) {
            current = host
        }
        return Closeable {
            synchronized(this) {
                if (current === host) {
                    current = null
                }
            }
        }
    }

    internal fun currentHost(): AndroidComponentsGeckoCompatibilityHost? = current

    fun forSession(context: AndroidComponentsGeckoSessionContext): AndroidComponentsGeckoCompatibilityHandler =
        object : AndroidComponentsGeckoCompatibilityHandler {
            override fun onWeekPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.DateTimePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
                current?.onWeekPrompt(context, session, prompt)

            override fun onXrPermission(
                session: GeckoSession,
                permission: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? = current?.onXrPermission(context, session, permission)

            override fun onLinkedMediaContextMenu(
                session: GeckoSession,
                element: GeckoSession.ContentDelegate.ContextElement,
            ): Boolean = current?.onLinkedMediaContextMenu(context, session, element) == true
        }
}

/**
 * Wraps the stock delegates installed by GeckoEngineSession after construction. This function is
 * intentionally not wired into the current shadow/raw ownership path; it is the cutover hook for a
 * future A-C-owned session factory that can capture the underlying GeckoSession through the public
 * geckoSessionProvider constructor argument.
 */
internal fun installAndroidComponentsGeckoCompatibilityDelegates(
    session: GeckoSession,
    compatibility: AndroidComponentsGeckoCompatibilityHandler,
) {
    session.promptDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsPromptCompatibilityDelegate) {
            session.promptDelegate = AndroidComponentsPromptCompatibilityDelegate(delegate, compatibility)
        }
    }
    session.permissionDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsPermissionCompatibilityDelegate) {
            session.permissionDelegate = AndroidComponentsPermissionCompatibilityDelegate(delegate, compatibility)
        }
    }
    session.contentDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsContentCompatibilityDelegate) {
            session.contentDelegate = AndroidComponentsContentCompatibilityDelegate(delegate, compatibility)
        }
    }
}
