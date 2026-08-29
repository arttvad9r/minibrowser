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

    BrowserMotionScreen(onBack = onBack) { requestExit ->
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
                        clearFaviconCaches(java.io.File(context.filesDir, "icons"))
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
private fun PickerChevron() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(6.dp))
        Icon(
            AppIcons.ChevronRight,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HorizontalDividerThin() {
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Выбор темы: выбранная карточка меняет поверхность/обводку коротко, без spring. */
@Composable
private fun ThemeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeOption("Системная", AppIcons.SystemTheme, 0, selected, onSelect, Modifier.weight(1f))
        ThemeOption("Светлая", AppIcons.Sun, 1, selected, onSelect, Modifier.weight(1f))
        ThemeOption("Тёмная", AppIcons.Moon, 2, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    value: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = value == selected
    val background by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(MotionTokens.Standard),
        label = "themeBackground",
    )
    val border by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(MotionTokens.Standard),
        label = "themeBorder",
    )

    Column(
        modifier
            .clip(Radius.button)
            .background(background)
            .border(width = 1.dp, color = border, shape = Radius.button)
            .softClickable(pressedScale = 0.96f) { onSelect(value) }
            .padding(vertical = 11.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
