package com.artt.minibrowser.ui

// Дизайн-токены Minibrowser: нейтральная серо-белая палитра без тёплых оттенков,
// единые радиусы и типографика. Единственный источник цветов и форм.

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Цвета (светлая) ---
private val SurfaceLight = Color(0xFFFFFFFF)
private val FieldLight = Color(0xFFF1F2F4)      // поля ввода, счётчик вкладок, подложки
private val PressedLight = Color(0xFFECEEF0)
private val TextPrimaryLight = Color(0xFF161719)
private val TextSecondaryLight = Color(0xFF60646B)
private val BorderLight = Color(0xFFE5E7EA)
private val DividerLight = Color(0xFFE8EAED)
private val Graphite = Color(0xFF25272A)        // «primary»: заполненные кнопки, активный switch

// --- Цвета (тёмная) ---
private val BgDark = Color(0xFF111315)
private val SurfaceDark = Color(0xFF181A1D)
private val FieldDark = Color(0xFF202327)
private val PressedDark = Color(0xFF272A2F)
private val TextPrimaryDark = Color(0xFFF2F3F5)
private val TextSecondaryDark = Color(0xFFA9ADB3)
private val BorderDark = Color(0xFF2C3035)
private val DividerDark = Color(0xFF292D31)

fun neutralLightScheme() = lightColorScheme(
    primary = Graphite,
    onPrimary = Color.White,
    primaryContainer = PressedLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = TextSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = FieldLight,
    onSecondaryContainer = TextPrimaryLight,
    background = Color(0xFFF8F9FA),
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = FieldLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = DividerLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Color(0xFFF5F6F7),
    surfaceContainer = FieldLight,
    surfaceContainerHigh = PressedLight,
    surfaceContainerHighest = PressedLight,
    scrim = Color(0x52000000),
)

fun neutralDarkScheme() = darkColorScheme(
    primary = Color(0xFFC9CCD0),
    onPrimary = Color(0xFF111315),
    primaryContainer = PressedDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = TextSecondaryDark,
    onSecondary = Color(0xFF111315),
    secondaryContainer = FieldDark,
    onSecondaryContainer = TextPrimaryDark,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = FieldDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = DividerDark,
    surfaceContainerLowest = Color(0xFF151719),
    surfaceContainerLow = BgDark,
    surfaceContainer = FieldDark,
    surfaceContainerHigh = PressedDark,
    surfaceContainerHighest = PressedDark,
    scrim = Color(0x66000000),
)

// --- Типографика: системный sans-serif, максимум Regular/Medium/SemiBold ---
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
)

// --- Радиусы ---
object Radius {
    val small = RoundedCornerShape(12.dp)   // мелкие элементы
    val button = RoundedCornerShape(14.dp)  // кнопки, плитки
    val card = RoundedCornerShape(20.dp)    // карточки
    val field = RoundedCornerShape(24.dp)   // адресная строка
    val search = RoundedCornerShape(28.dp)  // домашний поиск
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

@Composable
fun MinibrowserTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) neutralDarkScheme() else neutralLightScheme()
    // M3 MaterialTheme не задаёт LocalContentColor (его давал Surface) —
    // фиксируем цвет текста по умолчанию на onBackground для обеих тем.
    CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
