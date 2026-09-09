package com.artt.minibrowser.engine

import java.io.Closeable
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * App-scoped indirection for the small compatibility surface that still needs an Activity host.
 *
 * The registry itself is safe to retain from an Engine/EngineSession. A browser Activity binds its
 * host implementation for its lifetime and closes the returned lease on destruction. Identity-based
 * unbinding prevents an older Activity from clearing a newer Activity's replacement binding during
 * recreation.
 */
internal class AndroidComponentsGeckoCompatibilityRegistry : AndroidComponentsGeckoCompatibilityHandler {
    @Volatile
    private var current: AndroidComponentsGeckoCompatibilityHandler? = null

    fun bind(handler: AndroidComponentsGeckoCompatibilityHandler): Closeable {
        synchronized(this) {
            current = handler
        }
        return Closeable {
            synchronized(this) {
                if (current === handler) {
                    current = null
                }
            }
        }
    }

    internal fun currentHandler(): AndroidComponentsGeckoCompatibilityHandler? = current

    override fun onWeekPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        current?.onWeekPrompt(session, prompt)

    override fun onXrPermission(
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>? = current?.onXrPermission(session, permission)

    override fun onLinkedMediaContextMenu(
        session: GeckoSession,
        element: GeckoSession.ContentDelegate.ContextElement,
    ): Boolean = current?.onLinkedMediaContextMenu(session, element) == true
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
