package com.artt.minibrowser

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.FindBarContent
import com.artt.minibrowser.ui.FindBarUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FindBarLiveRegionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settledMatchCountIsPoliteLiveRegion() {
        render(FindBarUiState(query = "needle", current = 2, total = 5, resultsReady = true))

        assertPoliteLiveRegion("2/5")
    }

    @Test
    fun settledZeroMatchesAreAnnounced() {
        render(FindBarUiState(query = "missing", current = 0, total = 0, resultsReady = true))

        assertPoliteLiveRegion("0/0")
    }

    @Test
    fun pendingResultsDoNotExposeStaleCounter() {
        render(FindBarUiState(query = "new query", current = 2, total = 5, resultsReady = false))

        composeRule.onNodeWithText("2/5").assertDoesNotExist()
    }

    private fun render(state: FindBarUiState) {
        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                FindBarContent(
                    state = state,
                    onQueryChange = {},
                    onPrevious = {},
                    onNext = {},
                    onClose = {},
                )
            }
        }
    }

    private fun assertPoliteLiveRegion(text: String) {
        composeRule.onNodeWithText(text)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }
}
