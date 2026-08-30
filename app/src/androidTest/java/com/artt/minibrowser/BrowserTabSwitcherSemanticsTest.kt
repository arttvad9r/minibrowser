package com.artt.minibrowser

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.BrowserTabItemUiState
import com.artt.minibrowser.ui.BrowserTabSwitcher
import com.artt.minibrowser.ui.MinibrowserTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserTabSwitcherSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabCardsExposeSelectionAndCloseActionNamesItsTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val currentTitle = "Current tab"
        val otherTitle = "Other tab"
        val closeDescription = context.getString(R.string.close_named_tab_content_description, currentTitle)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                BrowserTabSwitcher(
                    tabs = listOf(
                        BrowserTabItemUiState(
                            id = 1L,
                            url = "https://example.com",
                            title = currentTitle,
                            isPrivate = false,
                        ),
                        BrowserTabItemUiState(
                            id = 2L,
                            url = "https://example.org",
                            title = otherTitle,
                            isPrivate = false,
                        ),
                    ),
                    currentId = 1L,
                    iconsDir = File(context.cacheDir, "tab-switcher-icons"),
                    onSelect = {},
                    onClose = {},
                    onNew = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(currentTitle)
            .assertIsSelected()
            .assertHasClickAction()
        composeRule.onNodeWithText(otherTitle)
            .assertIsNotSelected()
            .assertHasClickAction()
        composeRule
            .onNodeWithContentDescription(closeDescription)
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
