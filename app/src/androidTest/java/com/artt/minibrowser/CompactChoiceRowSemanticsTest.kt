package com.artt.minibrowser

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.CompactChoiceRow
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactChoiceRowSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun choiceRowsExposeSelectionStateAndClickAction() {
        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    CompactChoiceRow("Selected", selected = true, onClick = {})
                    CompactChoiceRow("Other", selected = false, onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("Selected").assertIsSelected().assertHasClickAction()
        composeRule.onNodeWithText("Other").assertIsNotSelected().assertHasClickAction()
    }
}
