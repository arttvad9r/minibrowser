package com.artt.minibrowser

import com.artt.minibrowser.data.SitePermissionPolicy
import com.artt.minibrowser.engine.PermissionAction
import com.artt.minibrowser.engine.contentPermissionAction
import com.artt.minibrowser.engine.permissionHost
import com.artt.minibrowser.engine.resolveContentPermissionValue
import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionPolicyTest {
    @Test fun hardDenyOverridesPersistedAllow() {
        val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        val allow = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        val prompt = GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
        assertEquals(deny, resolveContentPermissionValue(PermissionAction.DENY, allow))
        assertEquals(deny, resolveContentPermissionValue(PermissionAction.DENY, prompt))
        assertEquals(deny, resolveContentPermissionValue(PermissionAction.ALLOW, deny))
        assertEquals(allow, resolveContentPermissionValue(PermissionAction.PROMPT_GEOLOCATION, allow))
        assertEquals(deny, resolveContentPermissionValue(PermissionAction.PROMPT_GEOLOCATION, deny))
        assertEquals(prompt, resolveContentPermissionValue(PermissionAction.PROMPT_GEOLOCATION, prompt))
        assertEquals(allow, resolveContentPermissionValue(PermissionAction.ALLOW, prompt))
    }

    @Test fun mapsDefaultContentPermissionsToExistingBrowserPolicy() {
        assertEquals(PermissionAction.ALLOW, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE))
        assertEquals(PermissionAction.DENY, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE))
        assertEquals(PermissionAction.DENY, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION))
        assertEquals(PermissionAction.DENY, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_TRACKING))
        assertEquals(PermissionAction.DENY, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS))
        assertEquals(PermissionAction.DENY, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS))
        assertEquals(PermissionAction.DENY, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_XR))
        assertEquals(PermissionAction.DENY, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE))
        assertEquals(PermissionAction.PROMPT_GEOLOCATION, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION))
        assertEquals(PermissionAction.PROMPT_DRM, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS))
        assertEquals(PermissionAction.PROMPT_STORAGE_ACCESS, contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS))
        assertEquals(PermissionAction.DENY, contentPermissionAction(Int.MAX_VALUE))
    }

    @Test fun configurableContentPoliciesChangeFutureRequests() {
        val policy = SitePermissionPolicy(
            geolocationCanAsk = false,
            notificationsCanAsk = true,
            drmCanAsk = false,
            storageAccessCanAsk = false,
            autoplayAudibleAllowed = true,
            persistentStorageCanAsk = true,
            xrCanAsk = true,
            localAccessCanAsk = true,
        )

        assertEquals(
            PermissionAction.DENY,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION, policy),
        )
        assertEquals(
            PermissionAction.PROMPT_NOTIFICATIONS,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION, policy),
        )
        assertEquals(
            PermissionAction.DENY,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS, policy),
        )
        assertEquals(
            PermissionAction.DENY,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS, policy),
        )
        assertEquals(
            PermissionAction.ALLOW,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE, policy),
        )
        assertEquals(
            PermissionAction.PROMPT_PERSISTENT_STORAGE,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, policy),
        )
        assertEquals(
            PermissionAction.PROMPT_XR,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_XR, policy),
        )
        assertEquals(
            PermissionAction.PROMPT_LOCAL_DEVICE,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS, policy),
        )
        assertEquals(
            PermissionAction.PROMPT_LOCAL_NETWORK,
            contentPermissionAction(GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS, policy),
        )
    }

    @Test fun permissionHostUsesValidatedIdnPolicy() {
        assertEquals("пример.рф", permissionHost("https://пример.рф/path"))
        assertEquals("example.com", permissionHost("https://user:secret@example.com/private"))
        assertNull(permissionHost("about:blank"))
        assertNull(permissionHost("file:///tmp/private"))
        assertNull(permissionHost("not a uri"))
        assertNull(permissionHost(null))
    }
}
