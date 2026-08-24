package com.artt.minibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.artt.minibrowser.engine.Engine
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = GeckoSession().also { it.open(Engine.runtime) }
        setContent {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    AndroidView(factory = { ctx ->
                        GeckoView(ctx).apply { setSession(session) }
                    }, modifier = Modifier.weight(1f))
                    Button(onClick = { session.loadUri("https://example.com") }) { Text("go") }
                }
            }
        }
    }
}
