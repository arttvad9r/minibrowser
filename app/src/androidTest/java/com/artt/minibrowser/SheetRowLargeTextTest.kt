package com.artt.minibrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.SheetRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SheetRowLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sheetRowExpandsInsteadOfClippingLargeText() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MinibrowserTheme(darkTheme = false) {
                    Box(Modifier.width(180.dp)) {
                        SheetRow(
                            icon = Icons.Filled.Search,
                            label = "Очень длинное действие меню браузера",
                            modifier = Modifier.testTag("sheet-row"),
                            onClick = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("sheet-row").assertHeightIsAtLeast(53.dp)
    }
}
