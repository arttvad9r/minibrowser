package com.artt.minibrowser.engine

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.artt.minibrowser.R
import com.artt.minibrowser.net.webUriHost
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

internal enum class PermissionAction {
    ALLOW,
    DENY,
    PROMPT_GEOLOCATION,
    PROMPT_DRM,
    PROMPT_STORAGE_ACCESS,
}

internal fun contentPermissionAction(permission: Int): PermissionAction = when (permission) {
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> PermissionAction.ALLOW
    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> PermissionAction.PROMPT_GEOLOCATION
    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> PermissionAction.PROMPT_DRM
    GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> PermissionAction.PROMPT_STORAGE_ACCESS
    else -> PermissionAction.DENY
}

internal fun resolveContentPermissionValue(action: PermissionAction, existingValue: Int): Int = when (action) {
    PermissionAction.DENY -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
    else -> when (existingValue) {
        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        else -> when (action) {
            PermissionAction.ALLOW -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            else -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
        }
    }
}

internal fun permissionHost(uri: String?): String? = uri?.let(::webUriHost)

class GeckoPermissionController(
    private val activity: Activity,
    private val requestPermissions: ((Array<String>, (Boolean) -> Unit) -> Unit)?,
    private val isSessionCurrent: (GeckoSession) -> Boolean,
) : GeckoSession.PermissionDelegate {
    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) {
        if (!canHandle(session)) {
            callback.reject()
            return
        }
        val supported = setOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (permissions.orEmpty().any { it !in supported }) {
            callback.reject()
            return
        }
        val missing = permissions.orEmpty().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (missing.isEmpty()) {
            callback.grant()
            return
        }
        requestPermissions?.invoke(missing) { granted ->
            if (granted && canHandle(session)) callback.grant() else callback.reject()
        } ?: callback.reject()
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> {
        val action = contentPermissionAction(perm.permission)
        val resolvedValue = resolveContentPermissionValue(action, perm.value)
        if (resolvedValue != GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT) {
            return GeckoResult.fromValue(resolvedValue)
        }
        if (!canHandle(session)) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        val result = GeckoResult<Int>()
        val hostName = permissionHost(perm.uri)
        val host = hostName ?: activity.getString(R.string.site_fallback)
        val message = when (action) {
            PermissionAction.PROMPT_GEOLOCATION ->
                activity.getString(R.string.permission_geolocation_message, host)
            PermissionAction.PROMPT_DRM ->
                activity.getString(R.string.permission_drm_message, host)
            PermissionAction.PROMPT_STORAGE_ACCESS -> {
                val thirdParty = permissionHost(perm.thirdPartyOrigin)
                if (thirdParty != null && hostName != null) {
                    activity.getString(R.string.permission_storage_access_message, thirdParty, hostName)
                } else {
                    activity.getString(R.string.permission_storage_access_generic)
                }
            }
            PermissionAction.ALLOW, PermissionAction.DENY -> ""
        }
        activity.runOnUiThread {
            var completed = false
            fun complete(value: Int) {
                if (completed) return
                runCatching { result.complete(value) }.onSuccess { completed = true }
            }

            if (!canHandle(session)) {
                complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                return@runOnUiThread
            }
            runCatching {
                val dialog = AlertDialog.Builder(activity)
                    .setTitle(host)
                    .setMessage(message)
                    .setNegativeButton(activity.getString(R.string.action_deny)) { _, _ ->
                        complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    }
                    .setPositiveButton(activity.getString(R.string.action_allow)) { _, _ ->
                        complete(
                            if (canHandle(session)) {
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                            } else {
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                            },
                        )
                    }
                    .setOnCancelListener {
                        complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    }
                    .create()
                dialog.setOnShowListener { perm.notifyShown() }
                dialog.setOnDismissListener {
                    complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
                dialog.show()
            }.onFailure {
                complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            }
        }
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
        if (!canHandle(session)) {
            callback.reject()
            return
        }
        val camera = video.orEmpty().firstOrNull {
            it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
        }
        val microphone = audio.orEmpty().firstOrNull {
            it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
        }
        val unsupported = (video.orEmpty().asList() + audio.orEmpty().asList()).any {
            it.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA &&
                it.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
        }
        if (unsupported || (camera == null && microphone == null)) {
            callback.reject()
            return
        }
        val host = permissionHost(uri) ?: activity.getString(R.string.site_fallback)
        val message = when {
            camera != null && microphone != null ->
                activity.getString(R.string.permission_camera_microphone_message)
            camera != null -> activity.getString(R.string.permission_camera_message)
            else -> activity.getString(R.string.permission_microphone_message)
        }
        activity.runOnUiThread {
            if (!canHandle(session)) {
                callback.reject()
                return@runOnUiThread
            }
            var actionTaken = false
            runCatching {
                val dialog = AlertDialog.Builder(activity)
                    .setTitle(host)
                    .setMessage(message)
                    .setNegativeButton(activity.getString(R.string.action_deny)) { _, _ ->
                        actionTaken = true
                        callback.reject()
                    }
                    .setPositiveButton(activity.getString(R.string.action_allow)) { _, _ ->
                        actionTaken = true
                        requestAndroidMediaPermissions(session, camera, microphone, callback)
                    }
                    .setOnCancelListener {
                        actionTaken = true
                        callback.reject()
                    }
                    .create()
                dialog.setOnDismissListener {
                    if (!actionTaken) runCatching { callback.reject() }
                }
                dialog.show()
            }.onFailure {
                runCatching { callback.reject() }
            }
        }
    }

    private fun requestAndroidMediaPermissions(
        session: GeckoSession,
        camera: GeckoSession.PermissionDelegate.MediaSource?,
        microphone: GeckoSession.PermissionDelegate.MediaSource?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
        if (!canHandle(session)) {
            callback.reject()
            return
        }
        val needed = buildList {
            if (camera != null) add(Manifest.permission.CAMERA)
            if (microphone != null) add(Manifest.permission.RECORD_AUDIO)
        }.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (needed.isEmpty()) {
            callback.grant(camera, microphone)
        } else {
            requestPermissions?.invoke(needed) { granted ->
                if (granted && canHandle(session)) callback.grant(camera, microphone) else callback.reject()
            } ?: callback.reject()
        }
    }

    private fun canHandle(session: GeckoSession): Boolean = canShowUi() && isSessionCurrent(session)

    private fun canShowUi(): Boolean = !activity.isFinishing && !activity.isDestroyed
}
