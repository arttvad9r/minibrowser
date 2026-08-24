package com.artt.minibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.buildLoadUri
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = GeckoSession().also { it.open(Engine.runtime) }

        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AndroidView(factory = { GeckoView(it).apply { setSession(session) } },
                            modifier = Modifier.fillMaxSize())
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) /* прогресс из задачи 5 */
                    }
                    BottomBar(session)
                }
            }
        }
    }
}

@Composable
private fun BottomBar(session: GeckoSession) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
            singleLine = true, placeholder = { Text("Поиск или адрес") })
        TextButton(onClick = {
            session.loadUri(buildLoadUri(text, SearchEngine.YANDEX)); text = ""
        }) { Text("→") }
    }
}
