package com.artt.minibrowser.engine

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

class GeckoPermissionController(
    private val activity: Activity,
    private val requestPermissions: ((Array<String>, (Boolean) -> Unit) -> Unit)?,
) : GeckoSession.PermissionDelegate {
    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) {
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

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> {
        val result = GeckoResult<Int>()
        val host = runCatching { android.net.Uri.parse(perm.uri).host }.getOrNull().orEmpty().ifBlank { "Сайт" }
        val label = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "местоположение"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "уведомления"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "защищённое медиа"
            else -> "доступ к данным"
        }
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(host)
                .setMessage("Разрешить сайту доступ: $label?")
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

}
