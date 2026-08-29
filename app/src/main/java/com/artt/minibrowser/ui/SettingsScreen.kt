package com.artt.minibrowser.ui

// Настройки: компактные группы; большие списки выбора вынесены в bottom sheet.

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.DownloadsActivity
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.SearchEngine

private val translationLanguages = listOf(
    "Русский" to "ru",
    "English" to "en",
    "Deutsch" to "de",
    "Français" to "fr",
)

private fun translationLanguageLabel(code: String): String =
    translationLanguages.firstOrNull { it.second == code }?.first ?: code.uppercase()

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onEngine: (SearchEngine) -> Unit,
    onTheme: (Int) -> Unit,
    onAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    adblockStatus: ExtensionLoader.Status?,
    votEnabled: Boolean,
    votStatus: ExtensionLoader.Status?,
    onVot: (Boolean) -> Unit,
    onRetryVot: () -> Unit,
    onClearData: (withBookmarks: Boolean) -> Unit,
    onTranslateLang: (String) -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showEnginePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BrowserMotionScreen(onBack = onBack, fromBottom = true) { requestExit ->
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestExit(onBack) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
                Text("Настройки", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .imePadding(),
            ) {
                GroupLabel("Поиск")
                SettingsGroup {
                    SettingsRow(
                        title = "Поисковая система",
                        value = prefs.searchEngine.label,
                        onClick = { showEnginePicker = true },
                        trailing = { PickerChevron() },
                    )
                }

                GroupLabel("Внешний вид")
                ThemeSelector(prefs.theme, onTheme)

                GroupLabel("Перевод")
                SettingsGroup {
                    SettingsRow(
                        title = "Язык перевода",
                        value = translationLanguageLabel(prefs.translateTarget),
                        onClick = { showLanguagePicker = true },
                        trailing = { PickerChevron() },
                    )
                    HorizontalDividerThin()
                    when (votStatus) {
                        null, ExtensionLoader.Status.Installing ->
                            SettingsRow("Перевод видео", subtitle = "Запуск…")
                        ExtensionLoader.Status.Enabled ->
                            ToggleRow(
                                AppIcons.Globe,
                                "Перевод видео",
                                votEnabled,
                                onVot,
                                subtitle = "VOT · перевод видео с поддерживаемых сайтов",
                            )
                        ExtensionLoader.Status.Disabled ->
                            ToggleRow(
                                AppIcons.Globe,
                                "Перевод видео",
                                false,
                                onVot,
                                subtitle = "VOT · перевод видео с поддерживаемых сайтов",
                            )
                        ExtensionLoader.Status.Error ->
                            SettingsRow(
                                "Перевод видео",
                                subtitle = "Ошибка запуска · Нажмите, чтобы повторить",
                                onClick = onRetryVot,
                            )
                    }
                }

                GroupLabel("Файлы")
                SettingsGroup {
                    SettingsRow(
                        "Загрузки",
                        subtitle = "История файлов, скачанных через Minibrowser",
                        onClick = { context.startActivity(Intent(context, DownloadsActivity::class.java)) },
                        trailing = { PickerChevron() },
                    )
                }

                GroupLabel("Конфиденциальность")
                SettingsGroup {
                    when (adblockStatus) {
                        null, ExtensionLoader.Status.Installing ->
                            SettingsRow("Блокировка рекламы", subtitle = "Запуск…")
                        ExtensionLoader.Status.Error ->
                            SettingsRow(
                                "Блокировка рекламы",
                                subtitle = "Ошибка запуска · Нажмите, чтобы повторить",
                                onClick = onRetryAdblock,
                            )
                        ExtensionLoader.Status.Enabled ->
                            ToggleRow(
                                AppIcons.Shield,
                                "Блокировка рекламы",
                                true,
                                onAdblock,
                                subtitle = "Блокирует рекламу и трекеры",
                            )
                        ExtensionLoader.Status.Disabled ->
                            ToggleRow(
                                AppIcons.Shield,
                                "Блокировка рекламы",
                                false,
                                onAdblock,
                                subtitle = "Блокирует рекламу и трекеры",
                            )
                    }
                    HorizontalDividerThin()
                    SettingsRow(
                        "Очистить данные",
                        subtitle = "История, cookies, данные сайтов и кэш",
                        onClick = { showClearDialog = true },
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showEnginePicker) {
        CompactChoiceSheet(onDismissRequest = { showEnginePicker = false }) {
            Text("Поисковая система", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            SearchEngine.entries.forEach { engine ->
                CompactChoiceRow(engine.label, engine == prefs.searchEngine) {
                    onEngine(engine)
                    showEnginePicker = false
                }
            }
        }
    }

    if (showLanguagePicker) {
        CompactChoiceSheet(onDismissRequest = { showLanguagePicker = false }) {
            Text("Язык перевода", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            translationLanguages.forEach { (label, code) ->
                CompactChoiceRow(label, code == prefs.translateTarget) {
                    onTranslateLang(code)
                    showLanguagePicker = false
                }
            }
        }
    }

    if (showClearDialog) {
        var withBookmarks by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить данные") },
            text = {
                Column {
                    Text("Будут удалены:\n• история посещений;\n• cookies и данные сайтов;\n• кэш браузера;\n• кэш иконок.\n\nВозможно, потребуется снова войти в аккаунты на сайтах.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withBookmarks, onCheckedChange = { withBookmarks = it })
                        Text("Также удалить все закладки")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        TabPreviewStore.clear()
                        onClearData(withBookmarks)
                    },
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card),
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .softClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun PickerChevron() {
    Icon(
        AppIcons.ChevronDown,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ThemeSelector(current: Int, onTheme: (Int) -> Unit) {
    val options = listOf("Система" to 0, "Светлая" to 1, "Тёмная" to 2)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val selected = current == value
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = tween(MotionTokens.Standard),
                label = "themeOptionBg",
            )
            val border by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = tween(MotionTokens.Standard),
                label = "themeOptionBorder",
            )
            Text(
                label,
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(Radius.button)
                    .background(bg)
                    .border(1.dp, border, Radius.button)
                    .softClickable(onClick = { onTheme(value) })
                    .semantics { contentDescription = "Тема: $label" },
                textAlign = TextAlign.Center,
                lineHeight = 44.sp,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HorizontalDividerThin() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
