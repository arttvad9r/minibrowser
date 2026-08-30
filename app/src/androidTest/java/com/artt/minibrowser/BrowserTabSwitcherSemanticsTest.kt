package com.artt.minibrowser

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun closeActionNamesItsTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = "Example tab"
        val closeDescription = "${context.getString(R.string.close_tab_content_description)}: $title"

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                BrowserTabSwitcher(
                    tabs = listOf(
                        BrowserTabItemUiState(
                            id = 1L,
                            url = "https://example.com",
                            title = title,
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

        composeRule
            .onNodeWithContentDescription(closeDescription)
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
