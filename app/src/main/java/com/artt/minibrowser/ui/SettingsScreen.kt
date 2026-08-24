package com.artt.minibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.engine.SearchEngine

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onEngine: (SearchEngine) -> Unit,
    onTheme: (Int) -> Unit,
    onAdblock: (Boolean) -> Unit,
    onHomepage: (String) -> Unit,
    onClearData: (withBookmarks: Boolean) -> Unit,
) {
    var homepage by remember { mutableStateOf(prefs.homepage) }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle("Поисковик")
            SearchEngine.entries.forEach { e ->
                RadioRow(e.label, e == prefs.searchEngine) { onEngine(e) }
            }
            SectionTitle("Тема")
            listOf("Системная" to 0, "Светлая" to 1, "Тёмная" to 2).forEach { (label, v) ->
                RadioRow(label, v == prefs.theme) { onTheme(v) }
            }
            SectionTitle("Домашняя страница")
            OutlinedTextField(
                value = homepage,
                onValueChange = { homepage = it; onHomepage(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("example.com") },
            )
            SectionTitle("Прочее")
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = prefs.adblockEnabled, role = Role.Switch) { onAdblock(!prefs.adblockEnabled) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Блокировка рекламы", Modifier.weight(1f))
                Switch(checked = prefs.adblockEnabled, onCheckedChange = null)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Очистить данные (история, кэш иконок)", Modifier.weight(1f))
                TextButton(onClick = { showClearDialog = true }) { Text("Очистить") }
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
                    Text("Будут удалены история посещений и кэш иконок.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withBookmarks, onCheckedChange = { withBookmarks = it })
                        Text("Также удалить все закладки")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearData(withBookmarks) }) {
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
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
