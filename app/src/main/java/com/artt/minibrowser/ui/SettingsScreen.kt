package com.artt.minibrowser.ui

// Настройки: компактные группы; большие списки выбора вынесены в bottom sheet.

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.MotionDownloadsScreen
import com.artt.minibrowser.R
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.SearchEngine

private val translationLanguageCodes = listOf("ru", "en", "de", "fr")

@Composable
private fun translationLanguageLabel(code: String): String = when (code) {
    "ru" -> stringResource(R.string.language_russian)
    "en" -> stringResource(R.string.language_english)
    "de" -> stringResource(R.string.language_german)
    "fr" -> stringResource(R.string.language_french)
    else -> code.uppercase()
}

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
    clearDataInProgress: Boolean,
    clearDataFailed: Boolean,
    onTranslateLang: (String) -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showEnginePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }

    BrowserMotionScreen(onBack = onBack, fromBottom = true) { requestExit ->
        CenteredSinglePane(maxWidth = 720.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { requestExit(onBack) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .imePadding(),
                ) {
                    GroupLabel(stringResource(R.string.settings_group_search))
                    SettingsGroup {
                        SettingsRow(
                            title = stringResource(R.string.settings_search_engine),
                            value = prefs.searchEngine.label,
                            onClick = { showEnginePicker = true },
                            trailing = { PickerChevron() },
                        )
                    }

                    GroupLabel(stringResource(R.string.settings_group_appearance))
                    ThemeSelector(prefs.theme, onTheme)

                    GroupLabel(stringResource(R.string.settings_group_translation))
                    SettingsGroup {
                        SettingsRow(
                            title = stringResource(R.string.settings_translation_language),
                            value = translationLanguageLabel(prefs.translateTarget),
                            onClick = { showLanguagePicker = true },
                            trailing = { PickerChevron() },
                        )
                        HorizontalDividerThin()
                        if (votStatus == ExtensionLoader.Status.Error) {
                            SettingsRow(
                                stringResource(R.string.settings_video_translation),
                                subtitle = stringResource(R.string.settings_extension_retry_subtitle),
                                onClick = onRetryVot,
                            )
                        } else {
                            ToggleRow(
                                AppIcons.Globe,
                                stringResource(R.string.settings_video_translation),
                                votEnabled,
                                onVot,
                                subtitle = stringResource(R.string.settings_video_translation_subtitle),
                            )
                        }
                    }

                    GroupLabel(stringResource(R.string.settings_group_files))
                    SettingsGroup {
                        SettingsRow(
                            stringResource(R.string.downloads_title),
                            subtitle = stringResource(R.string.settings_downloads_subtitle),
                            onClick = { showDownloads = true },
                            trailing = { PickerChevron() },
                        )
                    }

                    GroupLabel(stringResource(R.string.settings_group_privacy))
                    SettingsGroup {
                        if (adblockStatus == ExtensionLoader.Status.Error) {
                            SettingsRow(
                                stringResource(R.string.settings_adblock),
                                subtitle = stringResource(R.string.settings_extension_retry_subtitle),
                                onClick = onRetryAdblock,
                            )
                        } else {
                            ToggleRow(
                                AppIcons.Shield,
                                stringResource(R.string.settings_adblock),
                                prefs.adblockEnabled,
                                onAdblock,
                                subtitle = stringResource(R.string.settings_adblock_subtitle),
                            )
                        }
                        HorizontalDividerThin()
                        SettingsRow(
                            stringResource(R.string.settings_clear_data),
                            subtitle = when {
                                clearDataInProgress -> stringResource(R.string.settings_clear_data_in_progress)
                                clearDataFailed -> stringResource(R.string.settings_clear_data_failed)
                                else -> stringResource(R.string.settings_clear_data_subtitle)
                            },
                            onClick = if (clearDataInProgress) null else ({ showClearDialog = true }),
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showEnginePicker) {
        CompactChoiceSheet(onDismissRequest = { showEnginePicker = false }) { dismiss ->
            Text(stringResource(R.string.settings_search_engine), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            SearchEngine.entries.forEach { engine ->
                CompactChoiceRow(engine.label, engine == prefs.searchEngine) {
                    onEngine(engine)
                    dismiss()
                }
            }
        }
    }

    if (showLanguagePicker) {
        CompactChoiceSheet(onDismissRequest = { showLanguagePicker = false }) { dismiss ->
            Text(stringResource(R.string.settings_translation_language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            translationLanguageCodes.forEach { code ->
                val label = translationLanguageLabel(code)
                CompactChoiceRow(label, code == prefs.translateTarget) {
                    onTranslateLang(code)
                    dismiss()
                }
            }
        }
    }

    if (showClearDialog) {
        var withBookmarks by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_clear_data_dialog_message))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withBookmarks, onCheckedChange = { withBookmarks = it })
                        Text(stringResource(R.string.settings_clear_bookmarks))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearData(withBookmarks)
                    },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDownloads) {
        MotionDownloadsScreen(onBack = { showDownloads = false })
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

/** Выбор темы без отдельной цветовой анимации: состояние меняется одним кадром. */
@Composable
private fun ThemeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeOption(
            stringResource(R.string.theme_system),
            AppIcons.SystemTheme,
            0,
            selected,
            onSelect,
            Modifier.weight(1f),
        )
        ThemeOption(
            stringResource(R.string.theme_light),
            AppIcons.Sun,
            1,
            selected,
            onSelect,
            Modifier.weight(1f),
        )
        ThemeOption(
            stringResource(R.string.theme_dark),
            AppIcons.Moon,
            2,
            selected,
            onSelect,
            Modifier.weight(1f),
        )
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
    val background = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    val border = if (isSelected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier
            .clip(Radius.button)
            .background(background)
            .border(width = 1.dp, color = border, shape = Radius.button)
            .softClickable { onSelect(value) }
            .padding(vertical = 11.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
