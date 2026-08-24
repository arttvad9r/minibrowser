package com.artt.minibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.ui.SettingsScreen
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

enum class Screen { Browser, Settings, History, Bookmarks }

class MainActivity : ComponentActivity() {
    private val settingsRepo by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = GeckoSession().also { it.open(Engine.runtime) }

        setContent {
            val prefs by settingsRepo.prefs.collectAsStateWithLifecycle(Prefs())
            val scope = rememberCoroutineScope()
            val darkTheme = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            var screen by remember { mutableStateOf(Screen.Browser) }

            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                // GeckoView живёт постоянно: повторное setSession на новый view при возврате не требуется.
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                AndroidView(factory = { GeckoView(it).apply { setSession(session) } },
                                    modifier = Modifier.fillMaxSize())
                                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) /* прогресс из задачи 5 */
                            }
                            BottomBar(session, prefs.searchEngine) { screen = Screen.Settings }
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
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(session: GeckoSession, engine: SearchEngine, onSettings: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
            singleLine = true, placeholder = { Text("Поиск или адрес") })
        TextButton(onClick = {
            session.loadUri(buildLoadUri(text, engine)); text = ""
        }) { Text("→") }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Настройки") }
    }
}

@Composable
private fun Placeholder(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        Text("$title — появится позже", style = MaterialTheme.typography.titleLarge)
    }
}
