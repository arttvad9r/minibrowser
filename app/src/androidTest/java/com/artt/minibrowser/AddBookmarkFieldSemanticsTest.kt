package com.artt.minibrowser

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.AddBookmarkSheet
import com.artt.minibrowser.ui.MinibrowserTheme
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
}
