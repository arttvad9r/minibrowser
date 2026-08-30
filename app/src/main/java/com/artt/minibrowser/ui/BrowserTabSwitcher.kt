package com.artt.minibrowser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import com.artt.minibrowser.engine.Tab
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Stable adaptive tab overview.
 *
 * The overview keeps one opaque surface mounted and moves only its content by a small amount. This
 * prevents a close/open transition from exposing Gecko or an empty new-tab frame while avoiding the
 * previous full-screen maxHeight translation that crossed thousands of pixels in a few frames.
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
    var predictiveBackActive by remember { mutableStateOf(false) }
    var activeAnimations by remember { mutableIntStateOf(0) }
    val overviewCurrentId = remember { currentId }
    val closeSwitcherDescription = stringResource(R.string.close_tab_switcher_content_description)
    val newTabTitle = stringResource(R.string.new_tab_title)

    HighFrameRateDuringMotion(activeAnimations > 0 || predictiveBackActive)

    DisposableEffect(Unit) {
        TabPreviewStore.setOverviewVisible(true)
        onDispose { TabPreviewStore.setOverviewVisible(false) }
    }

    suspend fun animateReveal(target: Float) {
        activeAnimations++
        try {
            reveal.animateTo(target, animationSpec = tween(MotionTokens.Screen))
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
    // only then animate the overview content. withFrameNanos follows 60/90/120/144 Hz automatically.
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
        if (pendingAction != null || switchTarget != null || predictiveBackActive) return
        pendingAction = action
    }

    fun activateAndExit(id: Long) {
        if (pendingAction != null || switchTarget != null || predictiveBackActive) return
        switchTarget = id
        onSelect(id)
    }

    val backEnabled = pendingAction == null && switchTarget == null
    val inputEnabled = backEnabled && !predictiveBackActive
    PredictiveBackHandler(enabled = backEnabled) { progress ->
        predictiveBackActive = true
        try {
            progress.collect { event ->
                reveal.snapTo(predictiveBackReveal(event.progress))
            }
            reveal.snapTo(0f)
            predictiveBackActive = false
            onDismiss()
        } catch (cancelled: CancellationException) {
            animateReveal(1f)
            predictiveBackActive = false
            throw cancelled
        }
    }

    val density = LocalDensity.current
    val travelPx = with(density) { 28.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .graphicsLayer {
                    translationY = (1f - reveal.value) * travelPx
                    alpha = 0.94f + reveal.value * 0.06f
                },
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { requestExit(onDismiss) },
                    enabled = inputEnabled,
                    modifier = Modifier.semantics { contentDescription = closeSwitcherDescription },
                ) {
                    Icon(AppIcons.ChevronDown, null)
                }
                Text(
                    pluralStringResource(R.plurals.tabs_count, tabs.size, tabs.size),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { requestExit(onNew) },
                    enabled = inputEnabled,
                    modifier = Modifier.semantics { contentDescription = newTabTitle },
                ) {
                    Icon(Icons.Filled.Add, null)
                }
            }

            when (tabs.size) {
                0 -> EmptyState(AppIcons.Globe, stringResource(R.string.no_open_tabs))
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
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val columns = tabGridColumnCount(maxWidth.value)
                        val initialIndex = tabs.indexOfFirst { it.id == overviewCurrentId }.coerceAtLeast(0)
                        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
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
}

internal fun tabGridColumnCount(widthDp: Float): Int = when {
    widthDp >= 840f -> 4
    widthDp >= 600f -> 3
    else -> 2
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
    val closeTabDescription = stringResource(R.string.close_tab_content_description)
    val newTabTitle = stringResource(R.string.new_tab_title)
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
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Text(
                    stringResource(R.string.private_tab_title),
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
                        .size(48.dp)
                        .semantics { contentDescription = closeTabDescription },
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
                Modifier.fillMaxWidth().height(48.dp).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(if (host.isNotBlank()) tab.url else host, iconsDir, 17.dp)
                Spacer(Modifier.width(7.dp))
                val displayTitle = when {
                    tab.title.isNotBlank() -> tab.title
                    tab.url.isBlank() || tab.url == "about:blank" -> newTabTitle
                    else -> host.ifBlank { tab.url }
                }
                Text(
                    displayTitle,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = closeTabDescription },
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
                tab.isPrivate -> stringResource(R.string.private_mode_title)
                host.isBlank() -> stringResource(R.string.new_tab_title)
                else -> host.removePrefix("www.")
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
