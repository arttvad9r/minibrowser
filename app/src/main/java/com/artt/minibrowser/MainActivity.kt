@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.ui.SettingsScreen
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import java.io.File

enum class Screen { Browser, Settings, History, Bookmarks }

class MainActivity : ComponentActivity() {
    private val settingsRepo by lazy { SettingsRepository(this) }
    private lateinit var tabManager: TabManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabManager = TabManager(Engine.runtime, File(filesDir, "tabs"))

        setContent {
            val prefs by settingsRepo.prefs.collectAsStateWithLifecycle(Prefs())
            val scope = rememberCoroutineScope()
            val darkTheme = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            var screen by remember { mutableStateOf(Screen.Browser) }
            var showTray by remember { mutableStateOf(false) }

            val tabs by tabManager.tabs.collectAsStateWithLifecycle()
            val currentId by tabManager.currentId.collectAsStateWithLifecycle()
            val currentTab = tabs.firstOrNull { it.id == currentId }
            val currentSession = currentTab?.session

            BackHandler(enabled = screen != Screen.Browser) { screen = Screen.Browser }

            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                AndroidView(
                                    factory = { ctx -> GeckoView(ctx) },
                                    update = { v ->
                                        if (v.session !== currentSession) {
                                            v.releaseSession()
                                            currentSession?.let(v::setSession)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize())
                                if (currentTab != null && currentTab.progress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { currentTab.progress },
                                        Modifier.fillMaxWidth().height(2.dp))
                                }
                            }
                            BottomBar(
                                currentTab,
                                prefs.searchEngine,
                                tabCount = tabs.size,
                                onNavigate = { uri ->
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                                onTray = { showTray = true },
                                onSettings = { screen = Screen.Settings })
                        }
                        if (screen == Screen.Settings) {
                            Surface(Modifier.fillMaxSize()) {
                                SettingsScreen(
                                    prefs,
                                    onBack = { screen = Screen.Browser },
                                    onEngine = { e -> scope.launch { settingsRepo.setSearchEngine(e) } },
                                    onTheme = { t -> scope.launch { settingsRepo.setTheme(t) } },
                                    onAdblock = { b -> scope.launch { settingsRepo.setAdblock(b) } },
                                    onHomepage = { u -> scope.launch { settingsRepo.setHomepage(u) } },
                                )
                            }
                        }
                        if (screen == Screen.History) Surface(Modifier.fillMaxSize()) { Placeholder("История") { screen = Screen.Browser } }
                        if (screen == Screen.Bookmarks) Surface(Modifier.fillMaxSize()) { Placeholder("Закладки") { screen = Screen.Browser } }
                        if (showTray) {
                            ModalBottomSheet(onDismissRequest = { showTray = false }) {
                                TabTray(
                                    tabs, currentId,
                                    onSelect = { tabManager.select(it); showTray = false },
                                    onClose = { tabManager.closeTab(it) },
                                    onNew = { showTray = false; tabManager.newTab(null) })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::tabManager.isInitialized) tabManager.persist()
    }
}

@Composable
private fun BottomBar(
    tab: Tab?,
    engine: SearchEngine,
    tabCount: Int,
    onNavigate: (String) -> Unit,
    onTray: () -> Unit,
    onSettings: () -> Unit,
) {
    // Пока поле в фокусе — текст пользователя, иначе живой URL текущей вкладки.
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val shown = if (focused) text else (tab?.url ?: "")
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = shown,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) text = ""
            },
            singleLine = true, placeholder = { Text("Поиск или адрес") })
        TextButton(onClick = {
            if (shown.isNotBlank()) onNavigate(buildLoadUri(shown, engine))
            text = ""
        }) { Text("→") }
        IconButton(onClick = onTray, modifier = Modifier.semantics { contentDescription = "Вкладки" }) {
            Text("$tabCount", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Настройки") }
    }
}

@Composable
private fun TabTray(
    tabs: List<Tab>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(8.dp).verticalScroll(rememberScrollState())) {
        tabs.forEach { tab ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(tab.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tab.title.ifBlank { tab.url.ifBlank { "Новая вкладка" } },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (tab.id == currentId) FontWeight.Bold else null)
                    if (tab.url.isNotBlank()) {
                        Text(tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.semantics { contentDescription = "Закрыть вкладку" }) {
                    Icon(Icons.Filled.Close, "Закрыть вкладку")
                }
            }
        }
        TextButton(onClick = onNew) { Text("+ Новая вкладка") }
    }
}

@Composable
private fun Placeholder(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        Text("$title — появится позже", style = MaterialTheme.typography.titleLarge)
    }
}
