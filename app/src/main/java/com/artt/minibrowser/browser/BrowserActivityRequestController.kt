package com.artt.minibrowser.browser

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.mozilla.geckoview.GeckoSession

/**
 * Owns ActivityResult launchers used by Gecko prompts and serializes requests across prompt types.
 *
 * Keeping this lifecycle-bound Android wiring out of MainActivity leaves the Activity responsible
 * for composition and browser lifecycle coordination rather than individual launcher callbacks.
 */
internal class BrowserActivityRequestController(
    private val activity: ComponentActivity,
) {
    private val permissionRequests = ActivityRequestCoordinator<Boolean>()
    private val fileRequests = ActivityRequestCoordinator<Array<Uri>>()

    private var permissionCompletion: ((Boolean) -> Unit)? = null
    private var pendingPermissionRequest: Set<String> = emptySet()
    private var fileCompletion: ((Array<Uri>) -> Unit)? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val requested = pendingPermissionRequest
        pendingPermissionRequest = emptySet()
        val completion = permissionCompletion
        permissionCompletion = null
        completion?.invoke(areRequestedPermissionsSatisfied(requested, grants))
    }

    private val multipleFilePickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        completeFileRequest(uris.toTypedArray())
    }

    private val singleFilePickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        completeFileRequest(uri?.let { arrayOf(it) } ?: emptyArray())
    }

    private val folderPickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        completeFileRequest(uri?.let { arrayOf(it) } ?: emptyArray())
    }

    fun requestPermissions(permissions: Array<String>, callback: (Boolean) -> Unit) {
        permissionRequests.enqueue(
            start = { complete ->
                pendingPermissionRequest = permissions.toSet()
                permissionCompletion = { granted ->
                    callback(granted)
                    complete(granted)
                }
                permissionLauncher.launch(permissions)
            },
            cancel = { callback(false) },
        )
    }

    fun pickFiles(type: Int, mimeTypes: Array<String>, callback: (Array<Uri>) -> Unit) {
        activity.runOnUiThread {
            fileRequests.enqueue(
                start = { complete ->
                    val accepted = acceptedMimeTypes(mimeTypes)
                    fileCompletion = { uris ->
                        callback(uris)
                        complete(uris)
                    }
                    runCatching {
                        when (type) {
                            GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE ->
                                multipleFilePickerLauncher.launch(accepted)
                            GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER ->
                                folderPickerLauncher.launch(null)
                            GeckoSession.PromptDelegate.FilePrompt.Type.SINGLE ->
                                singleFilePickerLauncher.launch(accepted)
                            else ->
                                singleFilePickerLauncher.launch(accepted)
                        }
                    }.onFailure {
                        fileCompletion = null
                        val none = emptyArray<Uri>()
                        callback(none)
                        complete(none)
                    }
                },
                cancel = { callback(emptyArray()) },
            )
        }
    }

    fun cancelAll() {
        permissionRequests.cancelAll()
        fileRequests.cancelAll()
        pendingPermissionRequest = emptySet()
        permissionCompletion = null
        fileCompletion = null
    }

    private fun completeFileRequest(uris: Array<Uri>) {
        val completion = fileCompletion
        fileCompletion = null
        completion?.invoke(uris)
    }
}

internal fun acceptedMimeTypes(mimeTypes: Array<String>): Array<String> = mimeTypes
    .filter { it.isNotBlank() && it.contains('/') }
    .distinct()
    .toTypedArray()
    .let { if (it.isEmpty()) arrayOf("*/*") else it }
