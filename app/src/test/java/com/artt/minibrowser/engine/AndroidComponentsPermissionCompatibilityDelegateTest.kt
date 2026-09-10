package com.artt.minibrowser.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidComponentsPermissionCompatibilityDelegateTest {
    @Test
    fun allPermissionCallbacksStayOnMiniBrowserCompatibilityOwner() {
        val session = GeckoSession()
        var stockAndroidCalls = 0
        var stockContentCalls = 0
        var stockMediaCalls = 0
        var compatibilityAndroidCalls = 0
        var compatibilityContentCalls = 0
        var compatibilityMediaCalls = 0

        val stock = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                stockAndroidCalls++
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                stockContentCalls++
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                stockMediaCalls++
            }
        }
        val compatibility = object : AndroidComponentsGeckoCompatibilityHandler {
            override fun onWeekPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.DateTimePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                compatibilityAndroidCalls++
                callback.reject()
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                permission: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                compatibilityContentCalls++
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                compatibilityMediaCalls++
                callback.reject()
            }

            override fun onLinkedMediaContextMenu(
                session: GeckoSession,
                element: GeckoSession.ContentDelegate.ContextElement,
            ): Boolean = false
        }

        val delegate = AndroidComponentsPermissionCompatibilityDelegate(stock, compatibility)
        val contentPermission = object : GeckoSession.PermissionDelegate.ContentPermission() {}
        delegate.onAndroidPermissionsRequest(
            session,
            arrayOf(android.Manifest.permission.CAMERA),
            object : GeckoSession.PermissionDelegate.Callback {},
        )
        delegate.onContentPermissionRequest(session, contentPermission)
        delegate.onMediaPermissionRequest(
            session,
            "https://example.test",
            null,
            null,
            object : GeckoSession.PermissionDelegate.MediaCallback {},
        )

        assertEquals(1, compatibilityAndroidCalls)
        assertEquals(1, compatibilityContentCalls)
        assertEquals(1, compatibilityMediaCalls)
        assertEquals(0, stockAndroidCalls)
        assertEquals(0, stockContentCalls)
        assertEquals(0, stockMediaCalls)
    }
}
