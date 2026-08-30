package com.artt.minibrowser

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.BrowserTextField
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserTextFieldSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun placeholderLabelsEditableNodeWithoutDuplicateTextNode() {
        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                BrowserTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Search field",
                )
            }
        }

        composeRule.onNodeWithContentDescription("Search field").fetchSemanticsNode()
        assertTrue(
            composeRule
                .onAllNodesWithText("Search field", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}
