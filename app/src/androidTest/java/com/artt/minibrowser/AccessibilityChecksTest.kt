package com.artt.minibrowser

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserChromeUiState
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
import com.artt.minibrowser.ui.TopBar
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
    fun browserChromePassesAutomatedChecks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                TopBar(
                    state = BrowserChromeUiState(url = "about:blank"),
                    tabCount = 1,
                    bookmarked = false,
                    iconsDir = File(context.cacheDir, "accessibility-icons"),
                    omniboxFocus = remember { FocusRequester() },
                    suggestions = emptyList(),
                    onSuggestionQueryChanged = {},
                    onSubmitQuery = {},
                    adblockStatus = BrowserExtensionUiState.Disabled,
                    onToggleAdblock = {},
                    onRetryAdblock = {},
                    onNavigate = {},
                    onBack = {},
                    onForward = {},
                    onReload = {},
                    onSiteInfo = {},
                    onSwitcher = {},
                    onNewTab = {},
                    onNewPrivateTab = {},
                    onFind = {},
                    onShare = {},
                    onToggleBookmark = {},
                    onBookmarks = {},
                    onHistory = {},
                    onDownloads = {},
                    onSettings = {},
                    onTranslate = {},
                    onToggleDesktop = {},
                )
            }
        }

        assertAccessibilityChecks()
    }

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
        renderSettings(darkTheme = false)
        assertAccessibilityChecks()
    }

    @Test
    fun settingsScreenDoesNotExposeScrollAction() {
        renderSettings(darkTheme = false)
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }

    @Test
    fun settingsDarkThemePassesAutomatedChecks() {
        renderSettings(darkTheme = true)
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

    private fun renderSettings(darkTheme: Boolean) {
        composeRule.setContent {
            MinibrowserTheme(darkTheme = darkTheme) {
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
    }

    private fun assertAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
