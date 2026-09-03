package com.artt.minibrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.InputShield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputShieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyOverlaySpaceDoesNotClickLowerLayerAndTopControlsStillWork() {
        var lowerClicks = 0
        var topClicks = 0

        composeRule.setContent {
            val lower = remember { mutableIntStateOf(0) }
            val top = remember { mutableIntStateOf(0) }
            lowerClicks = lower.intValue
            topClicks = top.intValue

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { lower.intValue++ },
                )
                Box(Modifier.fillMaxSize()) {
                    InputShield()
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(80.dp)
                            .testTag("top-control")
                            .clickable { top.intValue++ },
                    )
                }
            }
        }

        composeRule.onRoot().performTouchInput { click(Offset(8f, 8f)) }
        composeRule.runOnIdle {
            assertEquals(0, lowerClicks)
            assertEquals(0, topClicks)
        }

        composeRule.onNodeWithTag("top-control").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(0, lowerClicks)
            assertEquals(1, topClicks)
        }
    }
}
