package com.artt.minibrowser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.BrowserBottomSheet
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.SheetRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserBottomSheetLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longSheetKeepsLastActionReachableWithLargeText() {
        val lastAction = "Последнее действие"

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MinibrowserTheme(darkTheme = false) {
                    BrowserBottomSheet(onDismissRequest = {}) {
                        repeat(12) { index ->
                            SheetRow(
                                icon = Icons.Filled.Search,
                                label = if (index == 11) lastAction else "Действие ${index + 1}",
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(lastAction))
        composeRule.onNodeWithText(lastAction).assertIsDisplayed()
    }
}
