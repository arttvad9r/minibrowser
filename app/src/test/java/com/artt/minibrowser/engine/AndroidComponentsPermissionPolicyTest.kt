package com.artt.minibrowser.engine

import com.artt.minibrowser.data.SitePermissionPolicy
import mozilla.components.concept.engine.permission.Permission
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidComponentsPermissionPolicyTest {
    @Test
    fun defaultsMatchExistingBrowserPermissionPolicy() {
        assertEquals(EnginePermissionDecision.Allow, enginePermissionDecision(Permission.ContentAutoPlayInaudible()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentAutoPlayAudible()))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentGeoLocation()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentNotification()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentPersistentStorage()))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentMediaKeySystemAccess()))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentCrossOriginStorageAccess()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentLocalDeviceAccess()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentLocalNetworkAccess()))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentVideoCamera()))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentAudioMicrophone()))
    }

    @Test
    fun configurableDefaultsMapWithoutChangingTheirMeaning() {
        val policy = SitePermissionPolicy(
            geolocationCanAsk = false,
            cameraCanAsk = false,
            microphoneCanAsk = false,
            notificationsCanAsk = true,
            drmCanAsk = false,
            storageAccessCanAsk = false,
            autoplayAudibleAllowed = true,
            persistentStorageCanAsk = true,
            xrCanAsk = true,
            localAccessCanAsk = true,
        )

        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentGeoLocation(), policy))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentVideoCamera(), policy))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentAudioMicrophone(), policy))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentNotification(), policy))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentMediaKeySystemAccess(), policy))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentCrossOriginStorageAccess(), policy))
        assertEquals(EnginePermissionDecision.Allow, enginePermissionDecision(Permission.ContentAutoPlayAudible(), policy))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentPersistentStorage(), policy))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentLocalDeviceAccess(), policy))
        assertEquals(EnginePermissionDecision.Ask, enginePermissionDecision(Permission.ContentLocalNetworkAccess(), policy))
    }

    @Test
    fun unsupportedMediaSourcesStayHardBlocked() {
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentAudioCapture()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentAudioOther()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentVideoCapture()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentVideoScreen()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentVideoOther()))
    }

    @Test
    fun unknownAndNonSitePermissionTypesStayHardBlocked() {
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.Generic("xr")))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.ContentProtectedMediaId()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.AppCamera()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.AppAudio()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.AppLocationCoarse()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.AppLocationFine()))
        assertEquals(EnginePermissionDecision.Block, enginePermissionDecision(Permission.AppLocalNetworkAccess()))
    }
}
