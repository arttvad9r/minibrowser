package com.artt.minibrowser.ui

// Настройки: компактные группы; большие списки выбора вынесены в bottom sheet.

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.R
import com.artt.minibrowser.data.LocalBackupController
import java.time.LocalDate
import kotlinx.coroutines.launch

private val translationLanguageCodes = listOf("ru", "en", "de", "fr")

@Composable
private fun searchEngineLabel(engine: SettingsSearchEngineUiState): String = when (engine) {
    SettingsSearchEngineUiState.Google -> stringResource(R.string.search_engine_google)
    SettingsSearchEngineUiState.DuckDuckGo -> stringResource(R.string.search_engine_duckduckgo)
    SettingsSearchEngineUiState.Yandex -> stringResource(R.string.search_engine_yandex)
    SettingsSearchEngineUiState.Bing -> stringResource(R.string.search_engine_bing)
}

@Composable
private fun translationLanguageLabel(code: String): String = when (code) {
    "ru" -> stringResource(R.string.language_russian)
    "en" -> stringResource(R.string.language_english)
    "de" -> stringResource(R.string.language_german)
    "fr" -> stringResource(R.string.language_french)
    else -> code.uppercase()
}

@Composable
internal fun SettingsScreen(
    state: SettingsScreenUiState,
    onBack: () -> Unit,
    onEngine: (SettingsSearchEngineUiState) -> Unit,
    onTheme: (Int) -> Unit,
    onAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    onVot: (Boolean) -> Unit,
    onRetryVot: () -> Unit,
    onDownloads: () -> Unit,
    onClearData: (withBookmarks: Boolean) -> Unit,
    onTranslateLang: (String) -> Unit,
    backEnabled: Boolean = true,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showEnginePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSiteSettings by remember { mutableStateOf(false) }
    var exportSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var importSucceeded by remember { mutableStateOf<Boolean?>(null) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val backupController = remember(context.applicationContext) {
        LocalBackupController(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                exportSucceeded = backupController.export(uri).isSuccess
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupController.import(uri).fold(
                    onSuccess = { restored ->
                        // DataStore restore updates the UI preferences. Extension enable/disable is
                        // also an active Gecko runtime side effect, so converge it immediately instead
                        // of requiring an app restart after importing a backup.
                        onAdblock(restored.adblockEnabled)
                        onVot(restored.votEnabled)
                        importSucceeded = true
                    },
                    onFailure = { importSucceeded = false },
                )
            }
        }
    }

    BrowserMotionScreen(onBack = onBack, fromBottom = true, backEnabled = backEnabled) { requestExit ->
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
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                ) {
                    GroupLabel(stringResource(R.string.settings_group_search))
                    SettingsGroup {
                        SettingsRow(
                            title = stringResource(R.string.settings_search_engine),
                            value = searchEngineLabel(state.searchEngine),
                            onClick = { showEnginePicker = true },
                            trailing = { PickerChevron() },
                        )
                    }

                    GroupLabel(stringResource(R.string.settings_group_appearance))
                    ThemeSelector(state.theme, onTheme)

                    GroupLabel(stringResource(R.string.settings_group_translation))
                    SettingsGroup {
                        SettingsRow(
                            title = stringResource(R.string.settings_translation_language),
                            value = translationLanguageLabel(state.translateTarget),
                            onClick = { showLanguagePicker = true },
                            trailing = { PickerChevron() },
                        )
                        HorizontalDividerThin()
                        if (state.votStatus == BrowserExtensionUiState.Error) {
                            SettingsRow(
                                stringResource(R.string.settings_video_translation),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                subtitle = stringResource(R.string.settings_extension_retry_subtitle),
                                onClick = onRetryVot,
                            )
                        } else {
                            ToggleRow(
                                AppIcons.Globe,
                                stringResource(R.string.settings_video_translation),
                                state.votEnabled,
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
                            onClick = onDownloads,
                            trailing = { PickerChevron() },
                        )
                        HorizontalDividerThin()
                        SettingsRow(
                            stringResource(R.string.settings_backup_export),
                            subtitle = when (exportSucceeded) {
                                true -> stringResource(R.string.settings_backup_exported)
                                false -> stringResource(R.string.settings_backup_failed)
                                null -> stringResource(R.string.settings_backup_export_subtitle)
                            },
                            onClick = {
                                exportSucceeded = null
                                exportBackupLauncher.launch("minibrowser-backup-${LocalDate.now()}.json")
                            },
                        )
                        HorizontalDividerThin()
                        SettingsRow(
                            stringResource(R.string.settings_backup_import),
                            subtitle = when (importSucceeded) {
                                true -> stringResource(R.string.settings_backup_imported)
                                false -> stringResource(R.string.settings_backup_failed)
                                null -> stringResource(R.string.settings_backup_import_subtitle)
                            },
                            onClick = {
                                importSucceeded = null
                                showImportDialog = true
                            },
                        )
                    }

                    GroupLabel(stringResource(R.string.settings_group_privacy))
                    SettingsGroup {
                        if (state.adblockStatus == BrowserExtensionUiState.Error) {
                            SettingsRow(
                                stringResource(R.string.settings_adblock),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                subtitle = stringResource(R.string.settings_extension_retry_subtitle),
                                onClick = onRetryAdblock,
                            )
                        } else {
                            ToggleRow(
                                AppIcons.Shield,
                                stringResource(R.string.settings_adblock),
                                state.adblockEnabled,
                                onAdblock,
                                subtitle = stringResource(R.string.settings_adblock_subtitle),
                            )
                        }
                        HorizontalDividerThin()
                        SettingsRow(
                            stringResource(R.string.settings_site_settings),
                            subtitle = stringResource(R.string.settings_site_settings_subtitle),
                            onClick = { showSiteSettings = true },
                            trailing = { PickerChevron() },
                        )
                        HorizontalDividerThin()
                        SettingsRow(
                            stringResource(R.string.settings_clear_data),
                            modifier = if (state.clearDataFailed) {
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            } else {
                                Modifier
                            },
                            subtitle = when {
                                state.clearDataInProgress -> stringResource(R.string.settings_clear_data_in_progress)
                                state.clearDataFailed -> stringResource(R.string.settings_clear_data_failed)
                                else -> stringResource(R.string.settings_clear_data_subtitle)
                            },
                            onClick = if (state.clearDataInProgress) null else ({ showClearDialog = true }),
                        )
                    }
                }
            }
        }
    }

    if (showEnginePicker) {
        CompactChoiceSheet(onDismissRequest = { showEnginePicker = false }) { dismiss ->
            Text(stringResource(R.string.settings_search_engine), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            SettingsSearchEngineUiState.entries.forEach { engine ->
                CompactChoiceRow(searchEngineLabel(engine), engine == state.searchEngine) {
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
                CompactChoiceRow(label, code == state.translateTarget) {
                    onTranslateLang(code)
                    dismiss()
                }
            }
        }
    }

    if (showSiteSettings) {
        SiteSettingsSheet(onDismiss = { showSiteSettings = false })
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_backup_import_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importBackupLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                    },
                ) {
                    Text(stringResource(R.string.action_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearDialog) {
        var withBookmarks by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_clear_data_dialog_message))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = withBookmarks,
                                role = Role.Checkbox,
                                onValueChange = { withBookmarks = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = withBookmarks,
                            onCheckedChange = null,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
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
        Modifier
            .padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
            .semantics { heading() },
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
internal fun ThemeSelector(selected: Int, onSelect: (Int) -> Unit) {
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
    val border = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier
            .clip(Radius.button)
            .background(background)
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = border, shape = Radius.button)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = { onSelect(value) },
            )
            .padding(vertical = 11.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                null,
                Modifier.align(Alignment.TopEnd).padding(end = 8.dp).size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
