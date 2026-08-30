package com.artt.minibrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isHiddenFromAccessibility
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPageAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun occludedBrowserPageIsHiddenFromAccessibility() {
        render(hidden = true)

        composeRule.onNodeWithTag(CHILD_TAG, useUnmergedTree = true)
            .assert(hasAnyAncestor(isHiddenFromAccessibility()))
    }

    @Test
    fun visibleBrowserPageRemainsAccessible() {
        render(hidden = false)

        composeRule.onNodeWithTag(CHILD_TAG, useUnmergedTree = true)
            .assert(!hasAnyAncestor(isHiddenFromAccessibility()))
    }

    @Test
    fun fullScreenDestinationExposesPaneTitle() {
        val title = "History"

        composeRule.setContent {
            Box(Modifier.accessibilityPane(title)) {
                Text("Destination", Modifier.testTag(PANE_CHILD_TAG))
            }
        }

        composeRule.onNodeWithTag(PANE_CHILD_TAG, useUnmergedTree = true)
            .assert(
                hasAnyAncestor(
                    SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title),
                ),
            )
    }

    private fun render(hidden: Boolean) {
        composeRule.setContent {
            Box(Modifier.hideFromAccessibilityWhen(hidden)) {
                Text("Browser page", Modifier.testTag(CHILD_TAG))
            }
        }
    }

    private companion object {
        const val CHILD_TAG = "browser-page-child"
        const val PANE_CHILD_TAG = "browser-pane-child"
    }
}
