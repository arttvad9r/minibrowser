package com.artt.minibrowser.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** Data boundary for the app-owned download history store. */
internal class DownloadsRepository(context: Context) {
    private val applicationContext = context.applicationContext

    val downloads: StateFlow<List<BrowserDownload>> get() = DownloadHistory.items
    val restoreCompleted: StateFlow<Boolean> get() = DownloadHistory.restoreCompleted

    fun initialize() {
        DownloadHistory.init(applicationContext)
    }

    fun clear() {
        DownloadHistory.clear()
    }
}
