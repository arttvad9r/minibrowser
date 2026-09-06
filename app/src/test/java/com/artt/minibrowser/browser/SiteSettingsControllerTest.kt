package com.artt.minibrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import org.mozilla.geckoview.GeckoSession

class SiteSettingsControllerTest {
    @Test
    fun autoplayPermissionsRemainDistinct() {
        assertEquals(
            SitePermissionKind.AutoplayAudible,
            sitePermissionKind(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE),
        )
        assertEquals(
            SitePermissionKind.AutoplayInaudible,
            sitePermissionKind(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE),
        )
    }

    @Test
    fun commonPermissionsMapToUserFacingKinds() {
        assertEquals(
            SitePermissionKind.Geolocation,
            sitePermissionKind(GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION),
        )
        assertEquals(
            SitePermissionKind.Notifications,
            sitePermissionKind(GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION),
        )
        assertEquals(
            SitePermissionKind.StorageAccess,
            sitePermissionKind(GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS),
        )
    }
}
