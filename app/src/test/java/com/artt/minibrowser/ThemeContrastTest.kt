package com.artt.minibrowser

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.artt.minibrowser.ui.neutralDarkScheme
import com.artt.minibrowser.ui.neutralLightScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun semanticColorPairsMeetAccessibilityContrastMinimums() {
        assertAccessibleContrast(neutralLightScheme())
        assertAccessibleContrast(neutralDarkScheme())
    }

    private fun assertAccessibleContrast(scheme: ColorScheme) {
        assertContrastAtLeast(scheme.onBackground, scheme.background, 4.5)
        assertContrastAtLeast(scheme.onSurface, scheme.surface, 4.5)
        assertContrastAtLeast(scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5)
        assertContrastAtLeast(scheme.onPrimary, scheme.primary, 4.5)
        assertContrastAtLeast(scheme.onSecondary, scheme.secondary, 4.5)
        assertContrastAtLeast(scheme.outline, scheme.surface, 3.0)
        assertContrastAtLeast(scheme.outline, scheme.surfaceVariant, 3.0)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Double) {
        val actual = contrastRatio(foreground, background)
        assertTrue(
            "Expected contrast >= $minimum but was $actual for $foreground on $background",
            actual >= minimum,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) {
            value / 12.92
        } else {
            Math.pow((value + 0.055) / 1.055, 2.4)
        }
    }
}
