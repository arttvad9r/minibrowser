package com.artt.minibrowser.engine

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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

class GeckoPermissionController(
    private val activity: Activity,
    private val requestPermissions: ((Array<String>, (Boolean) -> Unit) -> Unit)?,
) : GeckoSession.PermissionDelegate {
    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) {
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
            if (granted) callback.grant() else callback.reject()
        } ?: callback.reject()
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> {
        if (perm.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ||
            perm.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        ) {
            return GeckoResult.fromValue(perm.value)
        }
        val result = GeckoResult<Int>()
        val action = contentPermissionAction(perm.permission)
        if (action == PermissionAction.ALLOW || action == PermissionAction.DENY) {
            return GeckoResult.fromValue(if (action == PermissionAction.ALLOW) {
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            } else {
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            })
        }
        val host = hostOfPermission(perm.uri)
        val topHost = hostOfPermission(perm.uri)
        val message = when (action) {
            PermissionAction.PROMPT_GEOLOCATION -> "$host хочет получить доступ к местоположению."
            PermissionAction.PROMPT_DRM -> "$host хочет использовать защищённое DRM-медиа."
            PermissionAction.PROMPT_STORAGE_ACCESS -> {
                val thirdParty = hostOfPermission(perm.thirdPartyOrigin)
                if (thirdParty != "Сайт" && topHost != "Сайт") {
                    "$thirdParty хочет получить доступ к cookies на $topHost."
                } else {
                    "Стороннее содержимое хочет получить доступ к cookies этого сайта."
                }
            }
        }
        activity.runOnUiThread {
            perm.notifyShown()
            AlertDialog.Builder(activity)
                .setTitle(host)
                .setMessage(message)
                .setNegativeButton("Запретить") { _, _ -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
                .setPositiveButton("Разрешить") { _, _ -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) }
                .setOnCancelListener { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
                .show()
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
        val camera = video.orEmpty().firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA }
        val microphone = audio.orEmpty().firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
        val unsupported = (video.orEmpty().asList() + audio.orEmpty().asList()).any {
            it.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA &&
                it.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
        }
        if (unsupported || (camera == null && microphone == null)) {
            callback.reject()
            return
        }
        val host = runCatching { android.net.Uri.parse(uri).host }.getOrNull().orEmpty().ifBlank { "Сайт" }
        val requested = buildList {
            if (camera != null) add("камере")
            if (microphone != null) add("микрофону")
        }.joinToString(" и ")
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(host)
                .setMessage("Разрешить доступ к $requested?")
                .setNegativeButton("Запретить") { _, _ -> callback.reject() }
                .setPositiveButton("Разрешить") { _, _ -> requestAndroidMediaPermissions(camera, microphone, callback) }
                .setOnCancelListener { callback.reject() }
                .show()
        }
    }

    private fun requestAndroidMediaPermissions(
        camera: GeckoSession.PermissionDelegate.MediaSource?,
        microphone: GeckoSession.PermissionDelegate.MediaSource?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
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
                if (granted) callback.grant(camera, microphone) else callback.reject()
            } ?: callback.reject()
        }
    }

    private fun hostOfPermission(uri: String?): String = runCatching {
        android.net.Uri.parse(uri.orEmpty()).host
    }.getOrNull().orEmpty().ifBlank { "Сайт" }

}
