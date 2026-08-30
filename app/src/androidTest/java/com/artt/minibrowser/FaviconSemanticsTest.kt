package com.artt.minibrowser

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.Favicon
import com.artt.minibrowser.ui.MinibrowserTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaviconSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fallbackInitialIsDecorative() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MinibrowserTheme(darkTheme = false) {
                Favicon(
                    source = "invalid host with spaces",
                    iconsDir = File(context.cacheDir, "favicon-semantics"),
                    size = 32.dp,
                )
            }
        }

        assertTrue(
            composeRule
                .onAllNodesWithText("I", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}
