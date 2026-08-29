package com.artt.minibrowser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.engine.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Browser-style tab overview with real page previews. Normal tabs use the captured Gecko frame as
 * a container-transform bridge; GeckoView itself is never transformed. Private tabs deliberately
 * fall back to the restrained whole-screen transition because their pixels are never cached.
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
    var entered by remember { mutableStateOf(false) }
    var exitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var exitHeroId by remember { mutableStateOf<Long?>(null) }
    var heroEnabledForExit by remember { mutableStateOf(true) }
    var predictiveActive by remember { mutableStateOf(false) }
    var predictiveReturning by remember { mutableStateOf(false) }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    val predictiveReturn = remember { Animatable(1f) }
    val motionScope = rememberCoroutineScope()
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    val cardBounds = remember { mutableStateMapOf<Long, Rect>() }

    // Give the grid one frame to position/scroll the current card before the hero starts shrinking.
    LaunchedEffect(Unit) {
        delay(24)
        entered = true
    }

    val settledProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = MotionTokens.ExpressiveSpatial,
        label = "tabSwitcherReveal",
    )

    LaunchedEffect(exitAction) {
        val action = exitAction ?: return@LaunchedEffect
        entered = false
        snapshotFlow { settledProgress }.first { it <= 0.015f }
        action()
        exitAction = null
        exitHeroId = null
        heroEnabledForExit = true
    }

    fun requestExit(heroId: Long?, useHero: Boolean = true, action: () -> Unit) {
        if (exitAction != null) return
        exitHeroId = heroId
        heroEnabledForExit = useHero
        exitAction = action
    }

    PredictiveBackHandler(enabled = exitAction == null) { events ->
        var receivedProgress = false
        try {
            predictiveReturning = false
            predictiveActive = true
            events.collect { event ->
                receivedProgress = true
                predictiveProgress = event.progress.coerceIn(0f, 1f)
            }
            if (receivedProgress) {
                predictiveProgress = 1f
                onDismiss()
            } else {
                predictiveActive = false
                requestExit(currentId, action = onDismiss)
            }
        } catch (_: CancellationException) {
            val visibleProgress = (1f - predictiveProgress).coerceIn(0f, 1f)
            predictiveActive = false
            predictiveReturning = true
            motionScope.launch {
                predictiveReturn.snapTo(visibleProgress)
                predictiveReturn.animateTo(1f, MotionTokens.ExpressiveSpatial)
                predictiveProgress = 0f
                predictiveReturning = false
            }
        }
    }

    val transitionProgress = when {
        predictiveActive -> 1f - predictiveProgress
        predictiveReturning -> predictiveReturn.value
        else -> settledProgress
    }.coerceIn(0f, 1f)

    val heroId = when {
        predictiveActive || predictiveReturning -> currentId
        exitAction != null && heroEnabledForExit -> exitHeroId
        exitAction != null -> null
        else -> currentId
    }
    val heroPreview = heroId?.let(TabPreviewStore::get)?.takeUnless { it.isRecycled }
    val heroTarget = heroId?.let(cardBounds::get)
    val hero = if (heroPreview != null && heroTarget != null && rootBounds.width > 1f && rootBounds.height > 1f) {
        heroPreview to heroTarget
    } else {
        null
    }
    val hasHero = hero != null
    val chromeProgress = if (hasHero) {
        ((transitionProgress - 0.18f) / 0.82f).coerceIn(0f, 1f)
    } else {
        transitionProgress
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .graphicsLayer {
                    alpha = chromeProgress
                    if (!hasHero) {
                        val scale = 0.985f + 0.015f * chromeProgress
                        scaleX = scale
                        scaleY = scale
                    }
                },
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { requestExit(currentId, action = onDismiss) },
                    enabled = exitAction == null,
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
                    onClick = { requestExit(null, useHero = false, action = onNew) },
                    enabled = exitAction == null,
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
                            isCurrent = tab.id == currentId,
                            iconsDir = iconsDir,
                            modifier = Modifier
                                .width(224.dp)
                                .onGloballyPositioned { cardBounds[tab.id] = it.boundsInWindow() },
                            onSelect = { requestExit(tab.id) { onSelect(tab.id) } },
                            onClose = { onClose(tab.id) },
                        )
                    }
                }
                else -> {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(currentId, tabs.size) {
                        val currentIndex = tabs.indexOfFirst { it.id == currentId }
                        if (currentIndex >= 0) gridState.scrollToItem(currentIndex)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 164.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            BrowserTabCard(
                                tab = tab,
                                isCurrent = tab.id == currentId,
                                iconsDir = iconsDir,
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(MotionTokens.Standard),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                        fadeOutSpec = tween(MotionTokens.Quick),
                                    )
                                    .onGloballyPositioned { cardBounds[tab.id] = it.boundsInWindow() },
                                onSelect = { requestExit(tab.id) { onSelect(tab.id) } },
                                onClose = { onClose(tab.id) },
                            )
                        }
                    }
                }
            }
        }

        hero?.let { (preview, target) ->
            val root = rootBounds
            val targetScaleX = (target.width / root.width).coerceAtLeast(0.01f)
            val targetScaleY = (target.height / root.height).coerceAtLeast(0.01f)
            val targetX = target.left - root.left
            val targetY = target.top - root.top
            val p = transitionProgress
            val heroAlpha = if (p < 0.82f) 1f else ((1f - p) / 0.18f).coerceIn(0f, 1f)

            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = mix(1f, targetScaleX, p)
                        scaleY = mix(1f, targetScaleY, p)
                        translationX = mix(0f, targetX, p)
                        translationY = mix(0f, targetY, p)
                        alpha = heroAlpha
                    }
                    .clip(Radius.card),
            )
        }
    }
}

private fun mix(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

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
    var closing by remember(tab.id) { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            delay(MotionTokens.Quick.toLong())
            onClose()
        }
    }
    val closeProgress by animateFloatAsState(
        targetValue = if (closing) 1f else 0f,
        animationSpec = tween(MotionTokens.Quick),
        label = "tabClose",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            tab.isPrivate && isCurrent -> MaterialTheme.colorScheme.primary
            tab.isPrivate -> MaterialTheme.colorScheme.outline
            isCurrent -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(MotionTokens.Standard),
        label = "tabBorder",
    )
    val cardColor by animateColorAsState(
        targetValue = when {
            tab.isPrivate -> MaterialTheme.colorScheme.surfaceContainerHighest
            isCurrent -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(MotionTokens.Standard),
        label = "tabSurface",
    )

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - closeProgress
                val scale = 1f - 0.035f * closeProgress
                scaleX = scale
                scaleY = scale
            }
            .clip(Radius.card)
            .background(cardColor)
            .border(1.dp, borderColor, Radius.card)
            .softClickable(enabled = !closing, pressedScale = 0.985f, onClick = onSelect),
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
                    onClick = { if (!closing) closing = true },
                    enabled = !closing,
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
                    onClick = { if (!closing) closing = true },
                    enabled = !closing,
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
            Crossfade(
                targetState = preview,
                animationSpec = tween(MotionTokens.Standard),
                label = "tabPreview",
            ) { bitmap ->
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap.asImageBitmap(),
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
