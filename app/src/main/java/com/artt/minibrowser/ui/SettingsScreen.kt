package com.artt.minibrowser.ui

// Настройки: карточки-группы вместо плоского списка radio buttons.
// Выбор — compact selection list; тема — визуальный selector из трёх карточек.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.ExtensionLoader

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onEngine: (SearchEngine) -> Unit,
    onTheme: (Int) -> Unit,
    onAdblock: (Boolean) -> Unit,
    adblockStatus: ExtensionLoader.Status?,
    onClearData: (withBookmarks: Boolean) -> Unit,
    onTranslateLang: (String) -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
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
                SearchEngine.entries.forEach { e ->
                    ChoiceRow(e.label, e == prefs.searchEngine) { onEngine(e) }
                }
            }

            GroupLabel("Внешний вид")
            ThemeSelector(prefs.theme, onTheme)

            GroupLabel("Перевод")
            SettingsGroup {
                listOf("Русский" to "ru", "English" to "en", "Deutsch" to "de", "Français" to "fr")
                    .forEach { (label, lang) ->
                        ChoiceRow(label, lang == prefs.translateTarget) { onTranslateLang(lang) }
                    }
            }

            GroupLabel("Конфиденциальность")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    when (adblockStatus) {
                        null, ExtensionLoader.Status.Installing -> SettingsRow("Блокировка рекламы", subtitle = "Запуск…")
                        ExtensionLoader.Status.Error -> SettingsRow("Блокировка рекламы", subtitle = "Ошибка запуска", onClick = { onAdblock(true) })
                        ExtensionLoader.Status.Enabled -> ToggleRow(AppIcons.Shield, "Блокировка рекламы", true, onAdblock, subtitle = "Блокирует рекламу и трекеры")
                        ExtensionLoader.Status.Disabled -> ToggleRow(AppIcons.Shield, "Блокировка рекламы", false, onAdblock, subtitle = "Блокирует рекламу и трекеры")
                    }
                }
                HorizontalDividerThin()
                SettingsRow("Очистить данные", subtitle = "История и кэш иконок", onClick = { showClearDialog = true })
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showClearDialog) {
        var withBookmarks by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить данные") },
            text = {
                Column {
                    Text("Будут удалены история посещений и кэш иконок.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withBookmarks, onCheckedChange = { withBookmarks = it })
                        Text("Также удалить все закладки")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearData(withBookmarks) }) { Text("Очистить") }
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

/** Выбор темы: три визуальные карточки, выбранная — тонкая графитовая обводка. */
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
    val isSel = value == selected
    Column(
        modifier
            .clip(Radius.button)
            .background(if (isSel) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (isSel) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                shape = Radius.button,
            )
            .clickable { onSelect(value) }
            .padding(vertical = 10.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
