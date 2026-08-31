package com.artt.minibrowser

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartPageErrorBannerSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorBannerIsPoliteLiveRegionAndKeepsActionClickable() {
        val message = "Start page failed"
        val action = "Retry"
        var actions = 0

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                StartPageErrorBanner(
                    message = message,
                    actionLabel = action,
                    onAction = { actions += 1 },
                )
            }
        }

        composeRule.onNodeWithText(message)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )

        composeRule.onNodeWithText(action).performClick()
        composeRule.runOnIdle { assertEquals(1, actions) }
    }
}
