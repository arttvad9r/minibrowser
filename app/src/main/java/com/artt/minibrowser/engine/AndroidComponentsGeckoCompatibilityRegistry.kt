package com.artt.minibrowser.engine

import java.io.Closeable
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

/** Immutable metadata that remains valid when the Activity hosting compatibility UI is recreated. */
internal data class AndroidComponentsGeckoSessionContext(
    val sessionId: String,
    val privateMode: Boolean,
) {
    fun isSelected(selectedSessionId: String?): Boolean = sessionId == selectedSessionId
}

/**
 * Activity-scoped implementation of compatibility UI retained across the A-C ownership boundary.
 * The session context is supplied by the A-C-owned session factory rather than rediscovered from a
 * raw GeckoSession or retained TabManager.
 */
internal interface AndroidComponentsGeckoCompatibilityHost {
    fun promptDelegate(
        context: AndroidComponentsGeckoSessionContext,
    ): GeckoSession.PromptDelegate?

    fun onAndroidPermissionsRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    )

    fun onContentPermissionRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>

    fun onMediaPermissionRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        uri: String,
        video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    )

    /** Returns true after taking ownership of [response.body]. */
    fun onExternalResponse(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        response: WebResponse,
    ): Boolean

    /** Returns a new TabManager-owned popup session, or null when the request must fail closed. */
    fun onNewSession(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        uri: String,
    ): GeckoResult<GeckoSession>? = null

    /** Consumes a close request for this immutable BrowserStore session identity. */
    fun onCloseRequest(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
    ) = Unit

    fun onLinkedMediaContextMenu(
        context: AndroidComponentsGeckoSessionContext,
        session: GeckoSession,
        element: GeckoSession.ContentDelegate.ContextElement,
    ): Boolean
}

/**
 * App-scoped indirection for compatibility UI.
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
            override fun promptDelegate(): GeckoSession.PromptDelegate? =
                current?.promptDelegate(context)

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                current?.onAndroidPermissionsRequest(context, session, permissions, callback)
                    ?: callback.reject()
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                permission: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> = current?.onContentPermissionRequest(context, session, permission)
                ?: GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                current?.onMediaPermissionRequest(context, session, uri, video, audio, callback)
                    ?: callback.reject()
            }

            override fun onExternalResponse(
                session: GeckoSession,
                response: WebResponse,
            ) {
                val consumed = current?.onExternalResponse(context, session, response) == true
                if (!consumed) {
                    runCatching { response.body?.close() }
                }
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String,
            ): GeckoResult<GeckoSession>? = current?.onNewSession(context, session, uri)

            override fun onCloseRequest(session: GeckoSession) {
                // Do not fall through to GeckoEngineSession's BrowserStore WindowRequest: this
                // transitional app has no TabsFeature consumer for it.
                current?.onCloseRequest(context, session)
            }

            override fun onLinkedMediaContextMenu(
                session: GeckoSession,
                element: GeckoSession.ContentDelegate.ContextElement,
            ): Boolean = current?.onLinkedMediaContextMenu(context, session, element) == true
        }
}

/**
 * Wraps the stock delegates installed by GeckoEngineSession after construction. This function is
 * intentionally not wired into the current shadow/raw ownership path; it is the cutover hook for an
 * A-C-owned session factory that captures the underlying GeckoSession through the public
 * geckoSessionProvider constructor argument.
 */
internal fun installAndroidComponentsGeckoCompatibilityDelegates(
    session: GeckoSession,
    compatibility: AndroidComponentsGeckoCompatibilityHandler,
) {
    if (session.promptDelegate !is AndroidComponentsPromptCompatibilityDelegate) {
        // Prompt ownership intentionally moves back to MiniBrowser's Activity-scoped controller.
        // Do not retain A-C's stock delegate: without PromptFeature it would enqueue BrowserStore
        // requests that have no consumer and TabsUseCases would violate current structural ownership.
        session.promptDelegate = AndroidComponentsPromptCompatibilityDelegate(compatibility)
    }
    session.permissionDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsPermissionCompatibilityDelegate) {
            session.permissionDelegate = AndroidComponentsPermissionCompatibilityDelegate(delegate, compatibility)
        }
    }
    session.navigationDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsNewSessionCompatibilityDelegate) {
            session.navigationDelegate = AndroidComponentsNewSessionCompatibilityDelegate(delegate, compatibility)
        }
    }
    session.contentDelegate?.let { delegate ->
        if (delegate !is AndroidComponentsContentCompatibilityDelegate) {
            session.contentDelegate = AndroidComponentsContentCompatibilityDelegate(delegate, compatibility)
        }
    }
}
