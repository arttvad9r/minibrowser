package com.artt.minibrowser

import android.text.format.Formatter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.DownloadFailureUiState
import com.artt.minibrowser.ui.DownloadItemUiState
import com.artt.minibrowser.ui.DownloadStatusUiState
import com.artt.minibrowser.ui.DownloadsScreenContent
import com.artt.minibrowser.ui.DownloadsScreenUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import java.text.DateFormat
import java.util.Date
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsStatusSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downloadStatusesArePoliteLiveRegionsWithMeaningfulDescriptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val finishedAt = 1_700_000_000_000L
        val completedBytes = 1_024L
        val completedDetails = context.getString(
            R.string.download_completed_subtitle,
            Formatter.formatShortFileSize(context, completedBytes),
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(finishedAt)),
        )
        val downloadingDescription = context.getString(
            R.string.download_status_downloading_accessibility,
            "active.pdf",
        )
        val completedDescription = context.getString(
            R.string.download_status_completed_accessibility,
            "done.pdf",
            completedDetails,
        )
        val failure = context.getString(R.string.download_save_error)
        val failedDescription = context.getString(
            R.string.download_status_failed_accessibility,
            "failed.pdf",
            failure,
        )

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                DownloadsScreenContent(
                    state = DownloadsScreenUiState(
                        downloads = listOf(
                            downloadItem(
                                id = "active",
                                name = "active.pdf",
                                status = DownloadStatusUiState.Downloading,
                            ),
                            downloadItem(
                                id = "done",
                                name = "done.pdf",
                                status = DownloadStatusUiState.Completed,
                                finishedAt = finishedAt,
                                bytes = completedBytes,
                                canOpen = true,
                            ),
                            downloadItem(
                                id = "failed",
                                name = "failed.pdf",
                                status = DownloadStatusUiState.Failed,
                                failureReason = DownloadFailureUiState.SaveFailed,
                            ),
                        ),
                        isRestoring = false,
                    ),
                    onBack = {},
                    onClear = {},
                    onOpen = {},
                )
            }
        }

        assertPoliteLiveRegion(downloadingDescription)
        assertPoliteLiveRegion(completedDescription)
        assertPoliteLiveRegion(failedDescription)
    }

    private fun downloadItem(
        id: String,
        name: String,
        status: DownloadStatusUiState,
        finishedAt: Long? = null,
        bytes: Long = 0L,
        canOpen: Boolean = false,
        failureReason: DownloadFailureUiState = DownloadFailureUiState.Unknown,
    ) = DownloadItemUiState(
        id = id,
        name = name,
        sourceUrl = "https://example.com",
        status = status,
        startedAt = 1_699_999_000_000L,
        finishedAt = finishedAt,
        bytes = bytes,
        canOpen = canOpen,
        failureReason = failureReason,
    )

    private fun assertPoliteLiveRegion(description: String) {
        composeRule.onNodeWithContentDescription(description)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }
}
