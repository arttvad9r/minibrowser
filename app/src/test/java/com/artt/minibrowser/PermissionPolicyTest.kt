package com.artt.minibrowser

import com.artt.minibrowser.engine.PermissionAction
import com.artt.minibrowser.engine.contentPermissionAction
import com.artt.minibrowser.engine.resolveContentPermissionValue
import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun mapsContentPermissionsToExplicitActions() {
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
}
