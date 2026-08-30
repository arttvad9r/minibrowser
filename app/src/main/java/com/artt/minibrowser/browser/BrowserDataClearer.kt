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
        // Clear the Activity-owned browser host before any database suspension. If the Activity is
        // recreated while this transaction is running, the old TabManager may be closed before a
        // later continuation resumes. Starting Gecko/tab cleanup here prevents that rotation race.
        clearWebData()
        clearHistory()
        if (withBookmarks) clearBookmarks()
        clearFaviconCaches()
    }
}
