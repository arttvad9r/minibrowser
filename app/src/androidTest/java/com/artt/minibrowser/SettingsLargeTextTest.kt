package com.artt.minibrowser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
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
class SettingsLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsKeepBottomActionsReachableWithLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clearDataLabel = context.getString(R.string.settings_clear_data)

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
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
        }

        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(clearDataLabel))
        composeRule.onNodeWithText(clearDataLabel).assertIsDisplayed()
    }
}
