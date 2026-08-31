package com.artt.minibrowser

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.AddBookmarkSheet
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddBookmarkFieldSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labelsBelongToEditableFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val titleLabel = context.getString(R.string.field_title)
        val addressLabel = context.getString(R.string.field_address)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                AddBookmarkSheet(onDismiss = {}, onAdd = { _, _ -> })
            }
        }

        composeRule.onNodeWithContentDescription(titleLabel).assert(hasSetTextAction())
        composeRule.onNodeWithContentDescription(addressLabel).assert(hasSetTextAction())
    }

    @Test
    fun imeNextMovesToAddressAndDoneSubmits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val titleLabel = context.getString(R.string.field_title)
        val addressLabel = context.getString(R.string.field_address)
        var submitted: Pair<String, String>? = null

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                AddBookmarkSheet(
                    onDismiss = {},
                    onAdd = { url, title -> submitted = url to title },
                )
            }
        }

        val titleField = composeRule.onNodeWithContentDescription(titleLabel)
        val addressField = composeRule.onNodeWithContentDescription(addressLabel)

        titleField.performClick()
        titleField.performTextInput("Example")
        titleField.performImeAction()
        addressField.assertIsFocused()
        addressField.performTextInput("https://example.com")
        addressField.performImeAction()

        composeRule.runOnIdle {
            assertEquals("https://example.com" to "Example", submitted)
        }
    }
}
