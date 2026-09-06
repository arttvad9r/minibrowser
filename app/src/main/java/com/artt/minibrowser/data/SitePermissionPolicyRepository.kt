package com.artt.minibrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sitePermissionPolicyDataStore by preferencesDataStore("site_permission_policy")

internal enum class SitePermissionPolicyType {
    GEOLOCATION,
    CAMERA,
    MICROPHONE,
    NOTIFICATIONS,
    DRM,
    STORAGE_ACCESS,
    AUTOPLAY_AUDIBLE,
    PERSISTENT_STORAGE,
    XR,
    LOCAL_ACCESS,
}

/** Browser-wide defaults. Per-site Gecko ContentPermission values override these defaults. */
internal data class SitePermissionPolicy(
    val geolocationCanAsk: Boolean = true,
    val cameraCanAsk: Boolean = true,
    val microphoneCanAsk: Boolean = true,
    val notificationsCanAsk: Boolean = false,
    val drmCanAsk: Boolean = true,
    val storageAccessCanAsk: Boolean = true,
    val autoplayAudibleAllowed: Boolean = false,
    val persistentStorageCanAsk: Boolean = false,
    val xrCanAsk: Boolean = false,
    val localAccessCanAsk: Boolean = false,
) {
    fun enabled(type: SitePermissionPolicyType): Boolean = when (type) {
        SitePermissionPolicyType.GEOLOCATION -> geolocationCanAsk
        SitePermissionPolicyType.CAMERA -> cameraCanAsk
        SitePermissionPolicyType.MICROPHONE -> microphoneCanAsk
        SitePermissionPolicyType.NOTIFICATIONS -> notificationsCanAsk
        SitePermissionPolicyType.DRM -> drmCanAsk
        SitePermissionPolicyType.STORAGE_ACCESS -> storageAccessCanAsk
        SitePermissionPolicyType.AUTOPLAY_AUDIBLE -> autoplayAudibleAllowed
        SitePermissionPolicyType.PERSISTENT_STORAGE -> persistentStorageCanAsk
        SitePermissionPolicyType.XR -> xrCanAsk
        SitePermissionPolicyType.LOCAL_ACCESS -> localAccessCanAsk
    }
}

/** Small DataStore-backed policy store shared by the settings UI and Gecko permission delegate. */
internal class SitePermissionPolicyRepository(context: Context) {
    private val context = context.applicationContext

    private object K {
        val geolocation = booleanPreferencesKey("site_geolocation_can_ask")
        val camera = booleanPreferencesKey("site_camera_can_ask")
        val microphone = booleanPreferencesKey("site_microphone_can_ask")
        val notifications = booleanPreferencesKey("site_notifications_can_ask")
        val drm = booleanPreferencesKey("site_drm_can_ask")
        val storageAccess = booleanPreferencesKey("site_storage_access_can_ask")
        val autoplayAudible = booleanPreferencesKey("site_autoplay_audible_allowed")
        val persistentStorage = booleanPreferencesKey("site_persistent_storage_can_ask")
        val xr = booleanPreferencesKey("site_xr_can_ask")
        val localAccess = booleanPreferencesKey("site_local_access_can_ask")
    }

    val policy: Flow<SitePermissionPolicy> = this.context.sitePermissionPolicyDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { stored ->
            SitePermissionPolicy(
                geolocationCanAsk = stored[K.geolocation] ?: true,
                cameraCanAsk = stored[K.camera] ?: true,
                microphoneCanAsk = stored[K.microphone] ?: true,
                notificationsCanAsk = stored[K.notifications] ?: false,
                drmCanAsk = stored[K.drm] ?: true,
                storageAccessCanAsk = stored[K.storageAccess] ?: true,
                autoplayAudibleAllowed = stored[K.autoplayAudible] ?: false,
                persistentStorageCanAsk = stored[K.persistentStorage] ?: false,
                xrCanAsk = stored[K.xr] ?: false,
                localAccessCanAsk = stored[K.localAccess] ?: false,
            )
        }

    suspend fun set(type: SitePermissionPolicyType, enabled: Boolean) {
        context.sitePermissionPolicyDataStore.edit { stored ->
            stored[key(type)] = enabled
        }
    }

    suspend fun replace(policy: SitePermissionPolicy) {
        context.sitePermissionPolicyDataStore.edit { stored ->
            stored[K.geolocation] = policy.geolocationCanAsk
            stored[K.camera] = policy.cameraCanAsk
            stored[K.microphone] = policy.microphoneCanAsk
            stored[K.notifications] = policy.notificationsCanAsk
            stored[K.drm] = policy.drmCanAsk
            stored[K.storageAccess] = policy.storageAccessCanAsk
            stored[K.autoplayAudible] = policy.autoplayAudibleAllowed
            stored[K.persistentStorage] = policy.persistentStorageCanAsk
            stored[K.xr] = policy.xrCanAsk
            stored[K.localAccess] = policy.localAccessCanAsk
        }
    }

    private fun key(type: SitePermissionPolicyType) = when (type) {
        SitePermissionPolicyType.GEOLOCATION -> K.geolocation
        SitePermissionPolicyType.CAMERA -> K.camera
        SitePermissionPolicyType.MICROPHONE -> K.microphone
        SitePermissionPolicyType.NOTIFICATIONS -> K.notifications
        SitePermissionPolicyType.DRM -> K.drm
        SitePermissionPolicyType.STORAGE_ACCESS -> K.storageAccess
        SitePermissionPolicyType.AUTOPLAY_AUDIBLE -> K.autoplayAudible
        SitePermissionPolicyType.PERSISTENT_STORAGE -> K.persistentStorage
        SitePermissionPolicyType.XR -> K.xr
        SitePermissionPolicyType.LOCAL_ACCESS -> K.localAccess
    }
}
