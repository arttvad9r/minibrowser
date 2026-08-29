@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser.ui

// Переиспользуемые примитивы дизайн-системы: favicon, заголовки секций,
// строки настроек/меню, поля, empty states, действия закладки.

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.engine.FaviconFetcher
import com.artt.minibrowser.engine.decodeSampledFavicon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

private object FaviconMemoryCache {
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, bitmap: Bitmap) = synchronized(cache) { cache.put(key, bitmap) }
}

/** Favicon с дисковым кэшем и bounded sampled bitmap cache. */
@Composable
fun Favicon(host: String, iconsDir: File, size: Dp, modifier: Modifier = Modifier) {
    val key = host.trim().lowercase()
    var bmp by remember(key) { mutableStateOf(FaviconMemoryCache.get(key)) }
    LaunchedEffect(key) {
        if (key.isNotBlank() && bmp == null) {
            val loaded = withContext(Dispatchers.IO) {
                val f = FaviconFetcher.fetch(key, iconsDir)
                if (f.exists()) decodeSampledFavicon(f) else null
            }
            if (loaded != null) FaviconMemoryCache.put(key, loaded)
            bmp = loaded
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = bmp,
            animationSpec = tween(MotionTokens.Standard),
            label = "favicon",
        ) { bitmap ->
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), null, Modifier.size(size))
            } else if (host.isNotBlank()) {
                Box(
                    Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        host.removePrefix("www.").take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (size.value * 0.45f).sp,
                    )
                }
            }
        }
    }
}

/** Заголовок секции: слева название, справа необязательное действие («Все ›»). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier
                    .clip(Radius.small)
                    .softClickable(pressedScale = 0.96f, onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { contentDescription = actionLabel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    actionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(
                    AppIcons.ChevronRight,
                    null,
                    Modifier.padding(start = 2.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Пустое состояние: небольшая outline-иконка + заголовок + строка пояснения. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Нейтральное поле ввода без рамки (вставляется в подложку вызывающим кодом). */
@Composable
fun BrowserTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

/** Строка bottom sheet / списка: иконка + подпись + необязательный trailing. */
@Composable
fun SheetRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Действие, которое сейчас невозможно выполнить, не должно раздувать меню серой строкой.
    // Информационные disabled-строки без click-handler (например, «Запуск…») остаются видимыми.
    if (!enabled && onClick != null) return

    val alpha = if (enabled) 1f else 0.52f
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(Radius.small)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        trailing?.invoke()
    }
}

/** Строка с переключателем. */
@Composable
fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .animateContentSize(animationSpec = standardSpatialSpring())
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.scale(0.9f).clearAndSetSemantics { },
        )
    }
}

/** Быстрое действие главного меню: круглая подложка + иконка + подпись. */
@Composable
fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(Radius.button)
            .softClickable(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** Карточка-группа настроек: единая поверхность с bounded ripple по форме. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card),
    ) { content() }
}

/** Строка внутри SettingsGroup. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 54.dp)
            .animateContentSize(animationSpec = standardSpatialSpring())
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Строка выбора: компактная 48dp hit-area, галка появляется без движения layout. */
@Composable
fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(MotionTokens.Quick)) + scaleIn(MotionTokens.StandardSpatial, initialScale = 0.82f),
            exit = fadeOut(tween(MotionTokens.Quick)) + scaleOut(tween(MotionTokens.Quick), targetScale = 0.82f),
        ) {
            Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Bottom sheet: единая поверхность и системная Material-анимация появления/закрытия. */
@Composable
fun BrowserBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Radius.sheet,
    ) {
        Column(
            Modifier
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) { content() }
    }
}

/** Действия над закладкой (долгий тап по плитке / overflow в списке закладок). */
@Composable
fun BookmarkActionsSheet(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var text by remember(bookmark.url) { mutableStateOf(bookmark.title) }
    BrowserBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.animateContentSize(animationSpec = standardSpatialSpring())) {
            if (renaming) {
                Text("Переименовать", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        BrowserTextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = "Название")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onRename(text.trim().ifBlank { bookmark.title }) }) { Text("ОК") }
                }
            } else {
                SheetRow(Icons.Filled.Search, "Открыть", onClick = { onOpen(); onDismiss() })
                SheetRow(Icons.Filled.Edit, "Переименовать", onClick = { renaming = true })
                SheetRow(Icons.Filled.Delete, "Удалить", onClick = { onDelete(); onDismiss() })
            }
        }
    }
}
