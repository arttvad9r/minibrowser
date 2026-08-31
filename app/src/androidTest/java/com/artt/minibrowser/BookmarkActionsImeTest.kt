package com.artt.minibrowser

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BookmarkActionsSheet
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkActionsImeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renameActionFocusesEditorAndDoneUsesNormalizedTitle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val renameLabel = context.getString(R.string.action_rename)
        val titleLabel = context.getString(R.string.field_title)
        var renamed: String? = null

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                BookmarkActionsSheet(
                    bookmarkKey = "https://example.com",
                    bookmarkTitle = "  Old title  ",
                    onDismiss = {},
                    onOpen = {},
                    onRename = { renamed = it },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(renameLabel).performClick()
        val titleField = composeRule.onNodeWithContentDescription(titleLabel)
        titleField.assertIsFocused()
        titleField.performImeAction()

        composeRule.runOnIdle {
            assertEquals("Old title", renamed)
        }
    }
}
