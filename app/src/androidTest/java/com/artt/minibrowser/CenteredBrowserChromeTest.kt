package com.artt.minibrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.CenteredBrowserChrome
import com.artt.minibrowser.ui.MinibrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CenteredBrowserChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chromeUsesAvailableCompactWidthAndCapsWideWidth() {
        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                Column {
                    Box(Modifier.width(96.dp)) {
                        CenteredBrowserChrome(maxWidth = 120.dp) {
                            Box(Modifier.fillMaxWidth().testTag("compact-chrome"))
                        }
                    }
                    Box(Modifier.width(180.dp)) {
                        CenteredBrowserChrome(maxWidth = 120.dp) {
                            Box(Modifier.fillMaxWidth().testTag("wide-chrome"))
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("compact-chrome", useUnmergedTree = true)
            .assertWidthIsEqualTo(96.dp)
        composeRule.onNodeWithTag("wide-chrome", useUnmergedTree = true)
            .assertWidthIsEqualTo(120.dp)
    }
}
