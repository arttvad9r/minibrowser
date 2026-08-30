package com.artt.minibrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.artt.minibrowser.engine.FaviconRepository
import java.io.File

/** Small UI adapter over the engine-owned favicon loader/cache. */
@Composable
fun Favicon(
    source: String,
    iconsDir: File,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val request = remember(source) { FaviconRepository.request(source) }
    val displayHost = remember(source) {
        hostOf(source).ifBlank { hostOf("https://${source.trim()}") }.ifBlank { source.trim() }
    }
    val cacheGeneration by FaviconRepository.generation.collectAsState()
    var bitmap by remember(request.key, cacheGeneration) {
        mutableStateOf(FaviconRepository.cached(request))
    }

    LaunchedEffect(request, iconsDir, cacheGeneration) {
        if (bitmap == null && request.origin != null) {
            bitmap = FaviconRepository.load(
                request = request,
                iconsDir = iconsDir,
                expectedGeneration = cacheGeneration,
            )
        }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(currentBitmap.asImageBitmap(), null, Modifier.size(size))
        } else if (displayHost.isNotBlank()) {
            Box(
                Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    displayHost.removePrefix("www.").take(1).uppercase(),
                    modifier = Modifier.clearAndSetSemantics { },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (size.value * 0.45f).sp,
                )
            }
        }
    }
}
