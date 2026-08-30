package com.artt.minibrowser

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.ThemeSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSelectorSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeOptionsExposeSelectionStateAndClickAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val system = context.getString(R.string.theme_system)
        val light = context.getString(R.string.theme_light)
        val dark = context.getString(R.string.theme_dark)

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                ThemeSelector(selected = 2, onSelect = {})
            }
        }

        composeRule.onNodeWithText(system).assertIsNotSelected().assertHasClickAction()
        composeRule.onNodeWithText(light).assertIsNotSelected().assertHasClickAction()
        composeRule.onNodeWithText(dark).assertIsSelected().assertHasClickAction()
    }
}
