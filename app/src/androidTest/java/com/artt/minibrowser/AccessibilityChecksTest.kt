package com.artt.minibrowser

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.DownloadsScreenContent
import com.artt.minibrowser.ui.DownloadsScreenUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.SettingsScreen
import com.artt.minibrowser.ui.SettingsScreenUiState
import com.artt.minibrowser.ui.SettingsSearchEngineUiState
import com.artt.minibrowser.ui.StartPage
import com.artt.minibrowser.ui.StartPageBookmarkUiState
import com.artt.minibrowser.ui.StartPageRecentUiState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class AccessibilityChecksTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downloadsEmptyStatePassesAutomatedChecks() {
        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                DownloadsScreenContent(
                    state = DownloadsScreenUiState(
                        downloads = emptyList(),
                        isRestoring = false,
                    ),
                    onBack = {},
                    onClear = {},
                    onOpen = {},
                )
            }
        }

        assertAccessibilityChecks()
    }

    @Test
    fun settingsScreenPassesAutomatedChecks() {
        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                SettingsScreen(
                    state = SettingsScreenUiState(
                        searchEngine = SettingsSearchEngineUiState.Google,
                        theme = 0,
                        adblockEnabled = true,
                        votEnabled = false,
                        translateTarget = "en",
                        adblockStatus = BrowserExtensionUiState.Enabled,
                        votStatus = BrowserExtensionUiState.Disabled,
                        clearDataInProgress = false,
                        clearDataFailed = false,
                    ),
                    onBack = {},
                    onEngine = {},
                    onTheme = {},
                    onAdblock = {},
                    onRetryAdblock = {},
                    onVot = {},
                    onRetryVot = {},
                    onDownloads = {},
                    onClearData = {},
                    onTranslateLang = {},
                )
            }
        }

        assertAccessibilityChecks()
    }

    @Test
    fun populatedStartPagePassesAutomatedChecks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val iconsDir = File(context.cacheDir, "accessibility-icons")

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                StartPage(
                    bookmarks = listOf(
                        StartPageBookmarkUiState(
                            url = "",
                            title = "Saved page",
                            host = "Saved page",
                        ),
                    ),
                    iconsDir = iconsDir,
                    recent = listOf(
                        StartPageRecentUiState(
                            url = "",
                            title = "Recent page",
                        ),
                    ),
                    isPrivate = false,
                    onOpen = {},
                    onAllBookmarks = {},
                    onAllHistory = {},
                    onRefreshRecent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onAdd = { _, _ -> },
                )
            }
        }

        assertAccessibilityChecks()
    }

    private fun assertAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
