package com.artt.minibrowser.browser

import com.artt.minibrowser.net.webUriHost
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.StorageController

internal enum class SitePermissionKind {
    Geolocation,
    Notifications,
    PersistentStorage,
    Xr,
    Autoplay,
    Drm,
    Tracking,
    StorageAccess,
    LocalDevice,
    LocalNetwork,
    Other,
}

internal data class SitePermissionItem(
    val permission: GeckoSession.PermissionDelegate.ContentPermission,
    val kind: SitePermissionKind,
    val allowed: Boolean,
)

internal data class SitePermissionGroup(
    val host: String,
    val permissions: List<SitePermissionItem>,
)

/** Thin state/data adapter over GeckoView's supported per-site storage APIs. */
internal class SiteSettingsController(
    private val storageController: StorageController,
) {
    suspend fun load(): Result<List<SitePermissionGroup>> = runCatching {
        storageController.getAllPermissions().awaitValue()
            .orEmpty()
            .asSequence()
            .filterNot { it.privateMode }
            .filter { it.value != GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT }
            .mapNotNull { permission ->
                val host = webUriHost(permission.uri) ?: return@mapNotNull null
                host to SitePermissionItem(
                    permission = permission,
                    kind = permissionKind(permission.permission),
                    allowed = permission.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
                )
            }
            .groupBy({ it.first }, { it.second })
            .map { (host, permissions) ->
                SitePermissionGroup(
                    host = host,
                    permissions = permissions.sortedBy { it.kind.ordinal },
                )
            }
            .sortedBy { it.host }
    }

    fun resetPermission(item: SitePermissionItem) {
        storageController.setPermission(
            item.permission,
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
        )
    }

    suspend fun clearSiteData(host: String): Result<Unit> = runCatching {
        storageController.clearDataFromHost(host, StorageController.ClearFlags.SITE_DATA)
            .awaitValue()
            .let { Unit }
    }
}

private fun permissionKind(permission: Int): SitePermissionKind = when (permission) {
    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> SitePermissionKind.Geolocation
    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> SitePermissionKind.Notifications
    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> SitePermissionKind.PersistentStorage
    GeckoSession.PermissionDelegate.PERMISSION_XR -> SitePermissionKind.Xr
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
    -> SitePermissionKind.Autoplay
    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> SitePermissionKind.Drm
    GeckoSession.PermissionDelegate.PERMISSION_TRACKING -> SitePermissionKind.Tracking
    GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> SitePermissionKind.StorageAccess
    GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> SitePermissionKind.LocalDevice
    GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> SitePermissionKind.LocalNetwork
    else -> SitePermissionKind.Other
}

private suspend fun <T> GeckoResult<T>.awaitValue(): T? = suspendCancellableCoroutine { continuation ->
    accept(
        { value ->
            if (continuation.isActive) continuation.resume(value)
        },
        { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error ?: IllegalStateException("Gecko operation failed"))
            }
        },
    )
}
