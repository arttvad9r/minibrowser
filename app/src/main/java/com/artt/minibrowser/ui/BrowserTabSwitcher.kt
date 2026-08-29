package com.artt.minibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.engine.Tab
import java.io.File

/**
 * Stable two-column tab overview.
 *
 * The background stays opaque for the whole transition, so a changing Gecko surface is never
 * blended through the overview. Cards themselves do not run independent spring/scale/color
 * animations: there is one short content fade for the screen, and that is all.
 */
@Composable
fun BrowserTabSwitcher(
    tabs: List<Tab>,
    currentId: Long?,
    iconsDir: File,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var switchTarget by remember { mutableStateOf<Long?>(null) }
    var activeAnimations by remember { mutableIntStateOf(0) }
    val overviewCurrentId = remember { currentId }

    HighFrameRateDuringMotion(activeAnimations > 0)

    suspend fun animateReveal(target: Float) {
        activeAnimations++
        try {
            reveal.animateTo(target, animationSpec = tween(MotionTokens.Quick))
        } finally {
            activeAnimations--
        }
    }

    LaunchedEffect(Unit) { animateReveal(1f) }

    LaunchedEffect(pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        animateReveal(0f)
        pendingAction = null
        action()
    }

    // A GeckoSession swap can do synchronous UI-thread work. Keep the overview fully opaque while
    // it happens, let Compose/AndroidView apply the new session for two actual display frames, and
    // only then run the cheap overlay fade. withFrameNanos follows 60/90/120/144 Hz automatically.
    LaunchedEffect(switchTarget, currentId) {
        val target = switchTarget ?: return@LaunchedEffect
        if (currentId != target) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        animateReveal(0f)
        switchTarget = null
        onDismiss()
    }

    fun requestExit(action: () -> Unit) {
        if (pendingAction != null || switchTarget != null) return
        pendingAction = action
    }

    fun activateAndExit(id: Long) {
        if (pendingAction != null || switchTarget != null) return
        switchTarget = id
        onSelect(id)
    }

    val inputEnabled = pendingAction == null && switchTarget == null
    BackHandler(enabled = inputEnabled) { requestExit(onDismiss) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = reveal.value },
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { requestExit(onDismiss) },
                    enabled = inputEnabled,
                    modifier = Modifier.semantics { contentDescription = "Закрыть переключатель вкладок" },
                ) {
                    Icon(AppIcons.ChevronDown, null)
                }
                Text(
                    "${tabs.size} ${tabsPlural(tabs.size)}",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { requestExit(onNew) },
                    enabled = inputEnabled,
                    modifier = Modifier.semantics { contentDescription = "Новая вкладка" },
                ) {
                    Icon(Icons.Filled.Add, null)
                }
            }

            when (tabs.size) {
                0 -> EmptyState(AppIcons.Globe, "Нет открытых вкладок")
                1 -> {
                    val tab = tabs.first()
                    Box(
                        Modifier.fillMaxSize().padding(top = 8.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        BrowserTabCard(
                            tab = tab,
                            isCurrent = tab.id == overviewCurrentId,
                            iconsDir = iconsDir,
                            modifier = Modifier.width(224.dp),
                            onSelect = { activateAndExit(tab.id) },
                            onClose = { if (inputEnabled) onClose(tab.id) },
                        )
                    }
                }
                else -> {
                    val initialIndex = tabs.indexOfFirst { it.id == overviewCurrentId }.coerceAtLeast(0)
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            BrowserTabCard(
                                tab = tab,
                                isCurrent = tab.id == overviewCurrentId,
                                iconsDir = iconsDir,
                                onSelect = { activateAndExit(tab.id) },
                                onClose = { if (inputEnabled) onClose(tab.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tabsPlural(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "вкладка"
    n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> "вкладки"
    else -> "вкладок"
}

@Composable
private fun BrowserTabCard(
    tab: Tab,
    isCurrent: Boolean,
    iconsDir: File,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val host = hostOf(tab.url)
    val preview = if (tab.isPrivate) null else TabPreviewStore[tab.id]
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        tab.isPrivate -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val cardColor = when {
        tab.isPrivate -> MaterialTheme.colorScheme.surfaceContainerHighest
        isCurrent -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(cardColor)
            .border(if (isCurrent) 1.5.dp else 1.dp, borderColor, Radius.card)
            .clickable(onClick = onSelect),
    ) {
        if (tab.isPrivate) {
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                Text(
                    "Приватная вкладка",
                    Modifier.align(Alignment.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                        .semantics { contentDescription = "Закрыть вкладку" },
                ) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(if (host.isNotBlank()) tab.url else host, iconsDir, 17.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    tab.title.ifBlank {
                        if (tab.url.isBlank() || tab.url == "about:blank") "Новая вкладка" else host.ifBlank { tab.url }
                    },
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).semantics { contentDescription = "Закрыть вкладку" },
                ) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.88f)
                .background(
                    if (tab.isPrivate) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            if (preview != null && !preview.isRecycled) {
                Image(
                    preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            } else {
                TabPreviewFallback(tab, host, iconsDir)
            }
        }
    }
}

@Composable
private fun TabPreviewFallback(tab: Tab, host: String, iconsDir: File) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (tab.isPrivate) {
            Icon(
                AppIcons.Incognito,
                null,
                Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            )
        } else if (host.isNotBlank()) {
            Favicon(tab.url, iconsDir, 40.dp)
        } else {
            Icon(
                AppIcons.Globe,
                null,
                Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                tab.isPrivate -> "Приватный режим"
                host.isBlank() -> "Новая вкладка"
                else -> host.removePrefix("www.")
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
