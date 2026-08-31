package com.artt.minibrowser

import com.artt.minibrowser.browser.areRequestedPermissionsSatisfied
import com.artt.minibrowser.browser.isCurrentPermissionRequestTab
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionGrantPolicyTest {
    private val fine = "android.permission.ACCESS_FINE_LOCATION"
    private val coarse = "android.permission.ACCESS_COARSE_LOCATION"
    private val camera = "android.permission.CAMERA"

    @Test fun acceptsApproximateLocationWhenFineIsDenied() {
        assertTrue(
            areRequestedPermissionsSatisfied(
                setOf(fine, coarse),
                mapOf(fine to false, coarse to true),
            ),
        )
    }

    @Test fun rejectsLocationWhenBothLevelsAreDenied() {
        assertFalse(
            areRequestedPermissionsSatisfied(
                setOf(fine, coarse),
                mapOf(fine to false, coarse to false),
            ),
        )
    }

    @Test fun stillRequiresNonLocationPermissions() {
        assertFalse(
            areRequestedPermissionsSatisfied(
                setOf(camera, fine, coarse),
                mapOf(camera to false, fine to false, coarse to true),
            ),
        )
    }

    @Test fun ordinaryPermissionMustBeGranted() {
        assertTrue(areRequestedPermissionsSatisfied(setOf(camera), mapOf(camera to true)))
        assertFalse(areRequestedPermissionsSatisfied(setOf(camera), mapOf(camera to false)))
    }

    @Test fun permissionRequestMustBelongToCurrentTab() {
        assertTrue(isCurrentPermissionRequestTab(requestTabId = 7L, currentTabId = 7L))
        assertFalse(isCurrentPermissionRequestTab(requestTabId = 7L, currentTabId = 8L))
        assertFalse(isCurrentPermissionRequestTab(requestTabId = null, currentTabId = 7L))
        assertFalse(isCurrentPermissionRequestTab(requestTabId = 7L, currentTabId = null))
    }
}
