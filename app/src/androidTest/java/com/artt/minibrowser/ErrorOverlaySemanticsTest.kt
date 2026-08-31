package com.artt.minibrowser

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.ErrorOverlay
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ErrorOverlaySemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pageErrorIsPoliteLiveRegionWithErrorDetailsAndRetryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.page_error_network)
        val hint = context.getString(R.string.page_error_hint)
        val retry = context.getString(R.string.action_retry)
        var retries = 0

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                ErrorOverlay(message) { retries += 1 }
            }
        }

        composeRule.onNodeWithText(message)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    hint,
                ),
            )

        composeRule.onNodeWithText(retry).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }
}
