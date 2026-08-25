package com.artt.minibrowser.ui

// Кастомные outline-иконки (стиль Material Symbols Rounded / Lucide: тонкий штрих,
// скруглённые концы). Только те, чего нет в androidx.core-наборе Icons.Outlined.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.s(width: Float = 1.7f, block: PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )

    /** Звезда-контур (закладки). */
    val Star: ImageVector by lazy {
        icon("StarOutline") {
            s {
                // 5-конечная звезда, полигон
                moveTo(12f, 2.6f)
                lineTo(14.9f, 8.5f)
                lineTo(21.3f, 9.4f)
                lineTo(16.65f, 13.9f)
                lineTo(17.75f, 20.3f)
                lineTo(12f, 17.3f)
                lineTo(6.25f, 20.3f)
                lineTo(7.35f, 13.9f)
                lineTo(2.7f, 9.4f)
                lineTo(9.1f, 8.5f)
                close()
            }
        }
    }

    /** Часы со стрелкой — история. */
    val History: ImageVector by lazy {
        icon("History") {
            s {
                moveTo(3.1f, 10.2f)
                curveTo(3.9f, 5.9f, 7.6f, 2.9f, 12f, 3f)
                curveToRelative(5f, 0.05f, 9f, 4.1f, 9f, 9f)
                curveToRelative(0f, 5f, -4f, 9f, -9f, 9f)
                curveToRelative(-3.3f, 0f, -6.2f, -1.8f, -7.8f, -4.5f)
                moveTo(3.1f, 10.2f)
                lineTo(3.2f, 4.6f)
                moveTo(3.1f, 10.2f)
                lineTo(8.6f, 10f)
            }
            s { moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f) }
        }
    }

    /** Щит — блокировка рекламы. */
    val Shield: ImageVector by lazy {
        icon("Shield") {
            s {
                moveTo(12f, 3f)
                lineTo(19f, 5.8f)
                lineTo(19f, 11f)
                curveTo(19f, 15.8f, 16.2f, 18.9f, 12f, 20.8f)
                curveTo(7.8f, 18.9f, 5f, 15.8f, 5f, 11f)
                lineTo(5f, 5.8f)
                close()
            }
        }
    }

    /** Загрузка. */
    val Download: ImageVector by lazy {
        icon("Download") {
            s { moveTo(20f, 15f); lineTo(20f, 18f); curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f); lineTo(6f, 20f); curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f); lineTo(4f, 15f) }
            s { moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f) }
            s { moveTo(12f, 15f); lineTo(12f, 3.5f) }
        }
    }

    /** Глобус — перевод страницы / язык. */
    val Globe: ImageVector by lazy {
        icon("Globe") {
            s { moveTo(12f, 3f); curveTo(7f, 3f, 3f, 7f, 3f, 12f); curveTo(3f, 17f, 7f, 21f, 12f, 21f); curveTo(17f, 21f, 21f, 17f, 21f, 12f); curveTo(21f, 7f, 17f, 3f, 12f, 3f); close() }
            s { moveTo(3f, 12f); lineTo(21f, 12f) }
            s {
                moveTo(12f, 3f)
                curveTo(9.5f, 5.5f, 8.2f, 8.6f, 8.2f, 12f)
                curveTo(8.2f, 15.4f, 9.5f, 18.5f, 12f, 21f)
                curveTo(14.5f, 18.5f, 15.8f, 15.4f, 15.8f, 12f)
                curveTo(15.8f, 8.6f, 14.5f, 5.5f, 12f, 3f)
                close()
            }
        }
    }

    /** Монитор — версия для ПК. */
    val Desktop: ImageVector by lazy {
        icon("Desktop") {
            s {
                moveTo(4.5f, 4f); lineTo(19.5f, 4f)
                curveTo(20.6f, 4f, 21.5f, 4.9f, 21.5f, 6f)
                lineTo(21.5f, 14.5f)
                curveTo(21.5f, 15.6f, 20.6f, 16.5f, 19.5f, 16.5f)
                lineTo(4.5f, 16.5f)
                curveTo(3.4f, 16.5f, 2.5f, 15.6f, 2.5f, 14.5f)
                lineTo(2.5f, 6f)
                curveTo(2.5f, 4.9f, 3.4f, 4f, 4.5f, 4f)
                close()
            }
            s { moveTo(12f, 16.5f); lineTo(12f, 20.5f) }
            s { moveTo(8f, 20.5f); lineTo(16f, 20.5f) }
        }
    }

    /** Шеврон вправо («Все ›»). */
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            s { moveTo(9f, 5.5f); lineTo(15.5f, 12f); lineTo(9f, 18.5f) }
        }
    }

    /** Шеврон вниз (закрытие переключателя вкладок). */
    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            s { moveTo(5.5f, 9f); lineTo(12f, 15.5f); lineTo(18.5f, 9f) }
        }
    }

    /** Солнце — светлая тема. */
    val Sun: ImageVector by lazy {
        icon("Sun") {
            s { moveTo(12f, 8f); curveTo(9.8f, 8f, 8f, 9.8f, 8f, 12f); curveTo(8f, 14.2f, 9.8f, 16f, 12f, 16f); curveTo(14.2f, 16f, 16f, 14.2f, 16f, 12f); curveTo(16f, 9.8f, 14.2f, 8f, 12f, 8f); close() }
            s {
                moveTo(12f, 2.5f); lineTo(12f, 4.5f)
                moveTo(12f, 19.5f); lineTo(12f, 21.5f)
                moveTo(2.5f, 12f); lineTo(4.5f, 12f)
                moveTo(19.5f, 12f); lineTo(21.5f, 12f)
                moveTo(5.3f, 5.3f); lineTo(6.7f, 6.7f)
                moveTo(17.3f, 17.3f); lineTo(18.7f, 18.7f)
                moveTo(18.7f, 5.3f); lineTo(17.3f, 6.7f)
                moveTo(6.7f, 17.3f); lineTo(5.3f, 18.7f)
            }
        }
    }

    /** Луна — тёмная тема. */
    val Moon: ImageVector by lazy {
        icon("Moon") {
            s {
                moveTo(20.5f, 13.2f)
                curveTo(19.5f, 17.6f, 15.4f, 20.7f, 10.8f, 20.1f)
                curveTo(6.2f, 19.5f, 2.9f, 15.4f, 3.2f, 10.8f)
                curveTo(3.5f, 6.2f, 7.2f, 2.7f, 11.8f, 2.7f)
                curveTo(10.2f, 5.5f, 10.5f, 9.1f, 12.7f, 11.6f)
                curveTo(14.9f, 14.1f, 18.4f, 14.8f, 21.4f, 13.4f)
                close()
            }
        }
    }

    /** Половина круга — системная тема. */
    val SystemTheme: ImageVector by lazy {
        icon("SystemTheme") {
            s { moveTo(12f, 3.2f); curveTo(7.1f, 3.2f, 3.2f, 7.1f, 3.2f, 12f); curveTo(3.2f, 16.9f, 7.1f, 20.8f, 12f, 20.8f); curveTo(16.9f, 20.8f, 20.8f, 16.9f, 20.8f, 12f); curveTo(20.8f, 7.1f, 16.9f, 3.2f, 12f, 3.2f); close() }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4.7f)
                arcTo(7.3f, 7.3f, 0f, false, true, 12f, 19.3f)
                close()
            }
        }
    }

    /** Очки инкогнито — приватная вкладка. */
    val Incognito: ImageVector by lazy {
        icon("Incognito") {
            s {
                moveTo(4.6f, 8.2f); lineTo(6.4f, 3.9f)
                curveTo(6.8f, 3f, 7.7f, 2.4f, 8.7f, 2.4f)
                lineTo(15.3f, 2.4f)
                curveTo(16.3f, 2.4f, 17.2f, 3f, 17.6f, 3.9f)
                lineTo(19.4f, 8.2f)
            }
            s { moveTo(2.8f, 8.2f); lineTo(21.2f, 8.2f) }
            s { moveTo(9.9f, 14.6f); curveTo(9.9f, 13.7f, 10.6f, 13f, 11.5f, 13f); curveTo(12.4f, 13f, 13.1f, 13.7f, 13.1f, 14.6f) }
            s { moveTo(3.5f, 17.5f); curveTo(3.5f, 19.4f, 5f, 21f, 7f, 21f); curveTo(9f, 21f, 10.5f, 19.4f, 10.5f, 17.5f); curveTo(10.5f, 15.6f, 9f, 14f, 7f, 14f); curveTo(5f, 14f, 3.5f, 15.6f, 3.5f, 17.5f); close() }
            s { moveTo(13.5f, 17.5f); curveTo(13.5f, 19.4f, 15f, 21f, 17f, 21f); curveTo(19f, 21f, 20.5f, 19.4f, 20.5f, 17.5f); curveTo(20.5f, 15.6f, 19f, 14f, 17f, 14f); curveTo(15f, 14f, 13.5f, 15.6f, 13.5f, 17.5f); close() }
        }
    }
}
