package com.artt.minibrowser

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.SettingsScreen
import com.artt.minibrowser.ui.SettingsScreenUiState
import com.artt.minibrowser.ui.SettingsSearchEngineUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsClearDataSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearBookmarksOptionIsOneLabeledToggleTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clearData = context.getString(R.string.settings_clear_data)
        val clearBookmarks = context.getString(R.string.settings_clear_bookmarks)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                SettingsScreen(
                    state = settingsState(clearDataFailed = false),
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

        composeRule.onNodeWithText(clearData).performClick()

        val toggleState = SemanticsProperties.ToggleableState
        val option = composeRule.onNodeWithText(clearBookmarks)
        option
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(toggleState, ToggleableState.Off))
            .performClick()
            .assert(SemanticsMatcher.expectValue(toggleState, ToggleableState.On))
    }

    @Test
    fun clearDataFailureIsAnnouncedAsPoliteLiveRegion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val failure = context.getString(R.string.settings_clear_data_failed)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                SettingsScreen(
                    state = settingsState(clearDataFailed = true),
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

        composeRule.onNodeWithText(failure)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    @Test
    fun extensionFailuresArePoliteLiveRegionsWithRetryActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val adblock = context.getString(R.string.settings_adblock)
        val videoTranslation = context.getString(R.string.settings_video_translation)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                SettingsScreen(
                    state = settingsState(
                        clearDataFailed = false,
                        adblockStatus = BrowserExtensionUiState.Error,
                        votStatus = BrowserExtensionUiState.Error,
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

        val politeLiveRegion = SemanticsMatcher.expectValue(
            SemanticsProperties.LiveRegion,
            LiveRegionMode.Polite,
        )
        composeRule.onNodeWithText(adblock)
            .assertHasClickAction()
            .assert(politeLiveRegion)
        composeRule.onNodeWithText(videoTranslation)
            .assertHasClickAction()
            .assert(politeLiveRegion)
    }

    private fun settingsState(
        clearDataFailed: Boolean,
        adblockStatus: BrowserExtensionUiState = BrowserExtensionUiState.Enabled,
        votStatus: BrowserExtensionUiState = BrowserExtensionUiState.Enabled,
    ) = SettingsScreenUiState(
        searchEngine = SettingsSearchEngineUiState.Yandex,
        theme = 0,
        adblockEnabled = true,
        votEnabled = true,
        translateTarget = "ru",
        adblockStatus = adblockStatus,
        votStatus = votStatus,
        clearDataInProgress = false,
        clearDataFailed = clearDataFailed,
    )
}
