package com.artt.minibrowser.engine

import com.artt.minibrowser.data.SitePermissionPolicy
import mozilla.components.concept.engine.permission.Permission

/**
 * Engine-neutral result for MiniBrowser's browser-wide site-permission defaults.
 *
 * This is intentionally only a policy seam. It does not consume, grant or reject an Android
 * Components PermissionRequest and is not wired to SitePermissionsFeature yet. Raw GeckoSession
 * permission delegates remain the sole live permission owner until EngineSession ownership moves.
 */
internal enum class EnginePermissionDecision {
    Allow,
    Block,
    Ask,
}

/**
 * Maps Android Components permission types onto MiniBrowser's existing permission defaults.
 *
 * Media parity is deliberately stricter than SitePermissionsRules: MiniBrowser currently accepts
 * only the physical camera and microphone sources. Screen/audio capture, other media sources and
 * unknown Generic permissions stay blocked instead of inheriting the camera/microphone rule.
 *
 * Android Components 154.0.1 has no explicit XR Permission type. Gecko's PERMISSION_XR therefore
 * becomes Permission.Generic before it reaches the concept-engine layer, losing its identity. XR
 * must remain on the raw compatibility path during ownership cutover rather than using this mapper.
 */
internal fun enginePermissionDecision(
    permission: Permission,
    policy: SitePermissionPolicy = SitePermissionPolicy(),
): EnginePermissionDecision = when (permission) {
    is Permission.ContentAutoPlayInaudible -> EnginePermissionDecision.Allow
    is Permission.ContentAutoPlayAudible ->
        if (policy.autoplayAudibleAllowed) EnginePermissionDecision.Allow else EnginePermissionDecision.Block

    is Permission.ContentGeoLocation -> askOrBlock(policy.geolocationCanAsk)
    is Permission.ContentNotification -> askOrBlock(policy.notificationsCanAsk)
    is Permission.ContentPersistentStorage -> askOrBlock(policy.persistentStorageCanAsk)
    is Permission.ContentMediaKeySystemAccess -> askOrBlock(policy.drmCanAsk)
    is Permission.ContentCrossOriginStorageAccess -> askOrBlock(policy.storageAccessCanAsk)
    is Permission.ContentLocalDeviceAccess,
    is Permission.ContentLocalNetworkAccess,
    -> askOrBlock(policy.localAccessCanAsk)

    is Permission.ContentVideoCamera -> askOrBlock(policy.cameraCanAsk)
    is Permission.ContentAudioMicrophone -> askOrBlock(policy.microphoneCanAsk)

    // Preserve the raw delegate's hard rejection of unsupported capture/source types and unknowns.
    is Permission.ContentAudioCapture,
    is Permission.ContentAudioOther,
    is Permission.ContentVideoCapture,
    is Permission.ContentVideoScreen,
    is Permission.ContentVideoOther,
    is Permission.ContentProtectedMediaId,
    is Permission.Generic,
    is Permission.AppCamera,
    is Permission.AppAudio,
    is Permission.AppLocationCoarse,
    is Permission.AppLocationFine,
    is Permission.AppLocalNetworkAccess,
    -> EnginePermissionDecision.Block
}

private fun askOrBlock(canAsk: Boolean): EnginePermissionDecision =
    if (canAsk) EnginePermissionDecision.Ask else EnginePermissionDecision.Block
