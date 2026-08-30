package com.artt.minibrowser.browser

/** Owns the ordered cross-feature browser-data clearing transaction. */
internal class BrowserDataClearer(
    private val clearTabPreviews: () -> Unit,
    private val clearHistory: suspend () -> Unit,
    private val clearBookmarks: suspend () -> Unit,
    private val clearFaviconCaches: () -> Unit,
    private val clearWebData: suspend () -> Unit,
) {
    suspend fun clear(withBookmarks: Boolean) {
        clearTabPreviews()
        clearHistory()
        if (withBookmarks) clearBookmarks()
        clearFaviconCaches()
        clearWebData()
    }
}
