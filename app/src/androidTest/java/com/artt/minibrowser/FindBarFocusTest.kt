package com.artt.minibrowser

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.FindBarContent
import com.artt.minibrowser.ui.FindBarUiState
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FindBarFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun findFieldReceivesFocusWhenBarOpens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                FindBarContent(
                    state = FindBarUiState(),
                    onQueryChange = {},
                    onPrevious = {},
                    onNext = {},
                    onClose = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.find_on_page))
            .assertIsFocused()
    }
}
