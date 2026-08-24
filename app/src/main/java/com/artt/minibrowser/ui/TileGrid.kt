@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.engine.FaviconFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TileGrid(
    bookmarks: List<Bookmark>,
    iconsDir: File,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<Bookmark?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(bookmarks) { bm ->
            Column(
                Modifier
                    .combinedClickable(onClick = { onOpen(bm.url) }, onLongClick = { selected = bm })
                    .padding(8.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncIcon(bm.host, iconsDir)
                Spacer(Modifier.height(4.dp))
                Text(bm.title.ifBlank { bm.host }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    val sel = selected
    if (sel != null) {
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            TileActions(sel,
                onClose = { selected = null },
                onOpen = { onOpen(sel.url); selected = null },
                onRename = { t -> onRename(sel.url, t); selected = null },
                onDelete = { onDelete(sel.url); selected = null })
        }
    }
}

@Composable
private fun TileActions(
    bm: Bookmark,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(bm.title) }
    if (renaming) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), singleLine = true)
            TextButton(onClick = { onRename(text.trim().ifBlank { bm.title }) }) { Text("ОК") }
        }
    } else {
        Column(Modifier.padding(bottom = 24.dp)) {
            TextButton(onClick = onOpen) { Text("Открыть") }
            TextButton(onClick = { renaming = true }) { Text("Переименовать") }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

@Composable
private fun AsyncIcon(host: String, iconsDir: File) {
    var bmp by remember(host) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(host) {
        if (host.isNotBlank()) withContext(Dispatchers.IO) {
            val f = FaviconFetcher.fetch(host, iconsDir)
            bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        }
    }
    val b = bmp
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.size(48.dp))
        } else {
            // ponytail: заглушка-кружок; настоящую иконку подгрузит fetch при следующем показе
            Box(
                Modifier.size(48.dp).background(placeholderColor(host), CircleShape),
                contentAlignment = Alignment.Center) {
                Text(host.take(1).uppercase(), color = Color.White, fontSize = 24.sp)
            }
        }
    }
}

private fun placeholderColor(seed: String): Color {
    val hue = ((seed.hashCode() % 360) + 360) % 360
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.6f)))
}
