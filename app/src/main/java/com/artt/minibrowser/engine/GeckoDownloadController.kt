package com.artt.minibrowser.engine

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.artt.minibrowser.R
import com.artt.minibrowser.data.DownloadHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private data class SavedDownload(val location: String, val bytes: Long)

internal fun shouldPersistDownloadHistory(isPrivate: Boolean): Boolean = !isPrivate

/** Writes one legacy download and guarantees that a failed copy never leaves a partial file. */
internal fun writeLegacyDownload(file: File, input: InputStream): Long {
    try {
        return FileOutputStream(file).use { output -> input.copyTo(output) }
    } catch (error: Throwable) {
        file.delete()
        throw error
    }
}

/**
 * Atomically reserves a unique public-Downloads filename. Checking exists() before opening is not
 * sufficient: two simultaneous downloads can otherwise select the same path and truncate each
 * other. createNewFile() is the reservation step; writeLegacyDownload() then owns that file.
 */
internal fun reserveUniqueDownloadFile(dir: File, name: String): File {
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    var index = 0
    while (true) {
        val candidate = if (index == 0) File(dir, name) else File(dir, "$stem ($index)$extension")
        if (candidate.createNewFile()) return candidate
        index++
    }
}

/**
 * Download transfers can outlive an Activity (for example during recreation). Keep only the
 * application context in this process-level scope; the Activity-owned controller is used solely
 * for confirmation dialogs and runtime permission requests before the transfer starts.
 */
private object DownloadIo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun save(
        context: Context,
        body: InputStream,
        name: String,
        mime: String,
        sourceUrl: String,
        persistHistory: Boolean,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            // History restore is lazy so ordinary browser startup never touches downloads.json.
            // init() only schedules IO; start() can run immediately and its live entry wins when
            // the older persisted snapshot is merged later.
            if (persistHistory) DownloadHistory.init(appContext)
            val historyId = if (persistHistory) DownloadHistory.start(name, sourceUrl, mime) else null
            val result = runCatching {
                body.use { input ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToMediaStore(appContext, input, name, mime)
                    } else {
                        saveToLegacyDownloads(appContext, input, name, mime)
                    }
                }
            }
            result.fold(
                onSuccess = { saved ->
                    historyId?.let { DownloadHistory.complete(it, saved.location, saved.bytes) }
                    withContext(Dispatchers.Main) {
                        toast(appContext, appContext.getString(R.string.download_saved, name))
                    }
                },
                onFailure = {
                    historyId?.let { DownloadHistory.fail(it) }
                    withContext(Dispatchers.Main) {
                        toast(appContext, appContext.getString(R.string.download_failed_toast))
                    }
                },
            )
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(
        context: Context,
        input: InputStream,
        name: String,
        mime: String,
    ): SavedDownload {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create download")
        try {
            val bytes = resolver.openOutputStream(uri, "w")?.use { output -> input.copyTo(output) }
                ?: error("Unable to open download output")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            return SavedDownload(uri.toString(), bytes)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDownloads(
        context: Context,
        input: InputStream,
        name: String,
        mime: String,
    ): SavedDownload {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) error("Unable to create Downloads directory")
        val file = reserveUniqueDownloadFile(dir, name)
        val bytes = writeLegacyDownload(file, input)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return SavedDownload(Uri.fromFile(file).toString(), bytes)
    }
}

/** Saves GeckoView's already-authenticated WebResponse instead of issuing a second HTTP request. */
class GeckoDownloadController(
    private val activity: Activity,
    private val requestPermissions: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null,
) {
    fun handle(response: WebResponse, isPrivate: Boolean = false) {
        val body = response.body
        if (body == null) {
            toast(activity.applicationContext, activity.getString(R.string.download_response_missing))
            return
        }

        val fallback = runCatching { Uri.parse(response.uri).lastPathSegment }
            .getOrNull()
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: "download"
        val name = parseFilename(response.headers.header("Content-Disposition"), fallback)
        val mime = response.headers.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream"
        val persistHistory = shouldPersistDownloadHistory(isPrivate)

        val begin = { ensureStorageAccessAndSave(body, name, mime, response.uri, persistHistory) }
        if (response.skipConfirmation) {
            begin()
            return
        }

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                body.closeQuietly()
                return@runOnUiThread
            }
            runCatching {
                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.download_confirm_title))
                    .setMessage(name)
                    .setNegativeButton(activity.getString(R.string.action_cancel)) { _, _ -> body.closeQuietly() }
                    .setPositiveButton(activity.getString(R.string.action_download)) { _, _ ->
                        runCatching(begin).onFailure { body.closeQuietly() }
                    }
                    .setOnCancelListener { body.closeQuietly() }
                    .show()
            }.onFailure {
                // Activity/window state can change after the isDestroyed check but before show().
                // This body owns Gecko's authenticated response stream, so always close it if no
                // transfer can take ownership.
                body.closeQuietly()
            }
        }
    }

    private fun ensureStorageAccessAndSave(
        body: InputStream,
        name: String,
        mime: String,
        sourceUrl: String,
        persistHistory: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            DownloadIo.save(activity.applicationContext, body, name, mime, sourceUrl, persistHistory)
            return
        }

        val requester = requestPermissions
        if (requester == null) {
            body.closeQuietly()
            toast(activity.applicationContext, activity.getString(R.string.downloads_folder_permission_error))
            return
        }
        requester(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { granted ->
            if (granted) {
                DownloadIo.save(activity.applicationContext, body, name, mime, sourceUrl, persistHistory)
            } else {
                body.closeQuietly()
                toast(activity.applicationContext, activity.getString(R.string.download_storage_permission_denied))
            }
        }
    }

    private fun InputStream.closeQuietly() {
        runCatching { close() }
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
}
