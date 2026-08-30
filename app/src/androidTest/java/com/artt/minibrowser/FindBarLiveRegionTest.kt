package com.artt.minibrowser

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        render(FindBarUiState(query = "needle", current = 2, total = 5, resultsReady = true))

        assertPoliteLiveRegion(context.getString(R.string.find_match_position, 2, 5))
    }

    @Test
    fun settledZeroMatchesAreAnnounced() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        render(FindBarUiState(query = "missing", current = 0, total = 0, resultsReady = true))

        assertPoliteLiveRegion(context.getString(R.string.find_no_matches))
    }

    @Test
    fun pendingResultsDoNotExposeStaleCounter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        render(FindBarUiState(query = "new query", current = 2, total = 5, resultsReady = false))

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.find_match_position, 2, 5))
            .assertDoesNotExist()
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
