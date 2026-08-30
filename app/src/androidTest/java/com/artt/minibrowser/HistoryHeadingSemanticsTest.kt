package com.artt.minibrowser

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.HistoryItemUiState
import com.artt.minibrowser.ui.HistoryScreenContent
import com.artt.minibrowser.ui.HistoryScreenUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryHeadingSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyDayGroupIsAccessibilityHeading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = context.getString(R.string.history_today)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                HistoryScreenContent(
                    state = HistoryScreenUiState.Content(
                        entries = listOf(
                            HistoryItemUiState(
                                url = "https://example.com",
                                title = "Example",
                                visitedAt = System.currentTimeMillis(),
                            ),
                        ),
                    ),
                    iconsDir = File(context.cacheDir, "history-heading-icons"),
                    onBack = {},
                    onOpen = {},
                    onClear = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(today)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }
}
