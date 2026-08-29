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
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val historyId = DownloadHistory.start(name, sourceUrl, mime)
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
                    DownloadHistory.complete(historyId, saved.location, saved.bytes)
                    withContext(Dispatchers.Main) { toast(appContext, "Скачано: $name") }
                },
                onFailure = { error ->
                    DownloadHistory.fail(historyId, error.localizedMessage ?: "Не удалось сохранить файл")
                    withContext(Dispatchers.Main) { toast(appContext, "Не удалось скачать файл") }
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
        val file = uniqueFile(dir, name)
        try {
            val bytes = FileOutputStream(file).use { output -> input.copyTo(output) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            return SavedDownload(Uri.fromFile(file).toString(), bytes)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val initial = File(dir, name)
        if (!initial.exists()) return initial
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(dir, "$stem ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }
}

/** Saves GeckoView's already-authenticated WebResponse instead of issuing a second HTTP request. */
class GeckoDownloadController(
    private val activity: Activity,
    private val requestPermissions: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null,
) {
    fun handle(response: WebResponse) {
        val body = response.body
        if (body == null) {
            toast(activity.applicationContext, "Не удалось получить файл")
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

        val begin = { ensureStorageAccessAndSave(body, name, mime, response.uri) }
        if (response.skipConfirmation) {
            begin()
            return
        }

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                body.closeQuietly()
                return@runOnUiThread
            }
            AlertDialog.Builder(activity)
                .setTitle("Скачать файл?")
                .setMessage(name)
                .setNegativeButton("Отмена") { _, _ -> body.closeQuietly() }
                .setPositiveButton("Скачать") { _, _ -> begin() }
                .setOnCancelListener { body.closeQuietly() }
                .show()
        }
    }

    private fun ensureStorageAccessAndSave(
        body: InputStream,
        name: String,
        mime: String,
        sourceUrl: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            DownloadIo.save(activity.applicationContext, body, name, mime, sourceUrl)
            return
        }

        val requester = requestPermissions
        if (requester == null) {
            body.closeQuietly()
            toast(activity.applicationContext, "Нет доступа к папке Downloads")
            return
        }
        requester(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { granted ->
            if (granted) DownloadIo.save(activity.applicationContext, body, name, mime, sourceUrl)
            else {
                body.closeQuietly()
                toast(activity.applicationContext, "Загрузка отменена: нет доступа к хранилищу")
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
