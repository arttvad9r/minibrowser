package com.artt.minibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.artt.minibrowser.R
import java.io.File
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val ChromiumTabAddDurationMs = 120

internal data class BrowserTabItemUiState(
    val id: Long,
    val url: String,
    val title: String,
    val isPrivate: Boolean,
)

/**
 * Adaptive tab overview using Chromium Hub's official fallback animation.
 *
 * Chromium's preferred shrink/expand transition requires a compositor thumbnail and exact source /
 * destination rectangles. GeckoView does not expose the equivalent Chrome compositor dependency,
 * so MiniBrowser follows Chromium's own fallback path instead of imitating the transform: the Hub
 * container fades as one surface for 325 ms. Session selection still happens immediately underneath
 * the fading overview, so the animation never blocks binding the selected GeckoSession.
 *
 * Creating a foreground tab is different: Chromium has enough geometry to animate a solid new-tab
 * surface without a thumbnail. MiniBrowser mirrors that path from the Hub pane itself: 20% -> 110%
 * rect growth for 300 ms with STANDARD easing, while the Hub toolbar fades out in parallel.
 */
@Composable
internal fun BrowserTabSwitcher(
    tabs: List<BrowserTabItemUiState>,
    currentId: Long?,
    iconsDir: File,
    previewStore: TabPreviewStore,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    val newTabProgress = remember { Animatable(0f) }
    val newTabToolbarAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var switchTarget by remember { mutableStateOf<Long?>(null) }
    var exiting by remember { mutableStateOf(false) }
    var newTabAnimating by remember { mutableStateOf(false) }
    var overviewWidthPx by remember { mutableFloatStateOf(1f) }
    val overviewCurrentId = remember { currentId }
    val closeSwitcherDescription = stringResource(R.string.close_tab_switcher_content_description)
    val newTabTitle = stringResource(R.string.new_tab_title)
    val overviewPaneTitle = stringResource(R.string.tabs_content_description)
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val newTabOrigin = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        Alignment.TopEnd
    } else {
        Alignment.TopStart
    }

    DisposableEffect(previewStore) {
        previewStore.setOverviewVisible(true)
        onDispose { previewStore.setOverviewVisible(false) }
    }

    suspend fun animateReveal(target: Float, easing: Easing) {
        reveal.animateTo(
            target,
            animationSpec = tween(MotionTokens.TabFallbackFade, easing = easing),
        )
    }

    LaunchedEffect(Unit) { animateReveal(1f, MotionEasing.FadeIn) }

    // Bind the selected GeckoSession first. Then fade Chromium's Hub container out over the already
    // selected page, matching the fallback animator without reintroducing the old selection delay.
    LaunchedEffect(switchTarget, currentId) {
        val target = switchTarget ?: return@LaunchedEffect
        if (currentId != target || exiting) return@LaunchedEffect
        exiting = true
        withFrameNanos { }
        animateReveal(0f, MotionEasing.FadeOut)
        switchTarget = null
        onDismiss()
    }

    fun requestExit(action: () -> Unit) {
        if (switchTarget != null || exiting || newTabAnimating) return
        exiting = true
        scope.launch {
            animateReveal(0f, MotionEasing.FadeOut)
            action()
        }
    }

    fun activateAndExit(id: Long) {
        if (switchTarget != null || exiting || newTabAnimating) return
        switchTarget = id
        onSelect(id)
    }

    fun createNewTabAndExit() {
        if (switchTarget != null || exiting || newTabAnimating) return
        exiting = true
        newTabAnimating = true

        // Chromium prepares EXPAND_NEW_TAB after the new foreground tab exists, then starts hiding
        // the Hub. Create/select the tab immediately so the destination is already ready underneath.
        onNew()

        scope.launch {
            coroutineScope {
                launch {
                    newTabProgress.snapTo(0f)
                    newTabProgress.animateTo(
                        1f,
                        animationSpec = tween(
                            MotionTokens.TabNew,
                            easing = MotionEasing.Standard,
                        ),
                    )
                }
                launch {
                    newTabToolbarAlpha.snapTo(1f)
                    newTabToolbarAlpha.animateTo(
                        0f,
                        animationSpec = tween(
                            MotionTokens.TabNew,
                            easing = MotionEasing.FadeOut,
                        ),
                    )
                }
            }
            onDismiss()
        }
    }

    val backEnabled = switchTarget == null && !exiting
    val inputEnabled = backEnabled && !newTabAnimating
    // Chromium Hub intercepts Back, but does not implement progressive predictive-back callbacks.
    // Start the same Hub hide animation only after the system Back gesture is committed.
    BackHandler(enabled = backEnabled) { requestExit(onDismiss) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { overviewWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { alpha = reveal.value }
            .background(MaterialTheme.colorScheme.background)
            .semantics { paneTitle = overviewPaneTitle },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer { alpha = newTabToolbarAlpha.value },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { requestExit(onDismiss) },
                    enabled = inputEnabled,
                    modifier = Modifier.semantics { contentDescription = closeSwitcherDescription },
                ) {
                    Icon(AppIcons.ChevronDown, null)
                }
                AnimatedContent(
                    targetState = tabs.size,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        fadeIn(
                            tween(MotionTokens.IconState, easing = MotionEasing.FadeIn),
                        ).togetherWith(
                            fadeOut(tween(MotionTokens.IconState, easing = MotionEasing.FadeOut)),
                        )
                    },
                    label = "tab count",
                ) { count ->
                    Text(
                        pluralStringResource(R.plurals.tabs_count, count, count),
                        Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(
                    onClick = ::createNewTabAndExit,
                    enabled = inputEnabled,
                    modifier = Modifier.semantics { contentDescription = newTabTitle },
                ) {
                    Icon(Icons.Filled.Add, null)
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (tabs.size) {
                    0 -> EmptyState(AppIcons.Globe, stringResource(R.string.no_open_tabs))
                    1 -> {
                        val tab = tabs.first()
                        val highlightedId = switchTarget ?: overviewCurrentId
                        Box(
                            Modifier.fillMaxSize().padding(top = 8.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            BrowserTabCard(
                                tab = tab,
                                isCurrent = tab.id == highlightedId,
                                gesturesEnabled = inputEnabled,
                                swipeExitDistancePx = overviewWidthPx,
                                iconsDir = iconsDir,
                                previewStore = previewStore,
                                modifier = Modifier.width(224.dp),
                                onSelect = { activateAndExit(tab.id) },
                                onClose = { if (inputEnabled) onClose(tab.id) },
                            )
                        }
                    }
                    else -> {
                        val columns = tabGridColumnCount(windowSizeClass)
                        val initialIndex =
                            tabs.indexOfFirst { it.id == overviewCurrentId }.coerceAtLeast(0)
                        val gridState =
                            rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
                        val highlightedId = switchTarget ?: overviewCurrentId
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(tabs, key = { it.id }) { tab ->
                                BrowserTabCard(
                                    tab = tab,
                                    isCurrent = tab.id == highlightedId,
                                    gesturesEnabled = inputEnabled,
                                    swipeExitDistancePx = overviewWidthPx,
                                    iconsDir = iconsDir,
                                    previewStore = previewStore,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(
                                            ChromiumTabAddDurationMs,
                                            easing = MotionEasing.Linear,
                                        ),
                                        placementSpec = tween(
                                            MotionTokens.TabMove,
                                            easing = MotionEasing.Standard,
                                        ),
                                        fadeOutSpec = tween(
                                            MotionTokens.TabRemove,
                                            easing = MotionEasing.StandardAccelerate,
                                        ),
                                    ),
                                    onSelect = { activateAndExit(tab.id) },
                                    onClose = { if (inputEnabled) onClose(tab.id) },
                                )
                            }
                        }
                    }
                }

                if (newTabAnimating) {
                    val progress = newTabProgress.value
                    val scale = 0.2f + (1.1f - 0.2f) * progress
                    val finalRadiusScale = 0.2f / 1.1f
                    val radiusScale = 1f + (finalRadiusScale - 1f) * progress
                    Box(
                        Modifier
                            .align(newTabOrigin)
                            .width(maxWidth * scale)
                            .height(maxHeight * scale)
                            .clip(RoundedCornerShape(10.dp * radiusScale))
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
}

internal fun tabGridColumnCount(windowSizeClass: WindowSizeClass): Int = when {
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> 6
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> 5
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 4
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 3
    else -> 2
}

@Composable
private fun BrowserTabCard(
    tab: BrowserTabItemUiState,
    isCurrent: Boolean,
    gesturesEnabled: Boolean,
    swipeExitDistancePx: Float,
    iconsDir: File,
    previewStore: TabPreviewStore,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val host = hostOf(tab.url)
    val preview = if (tab.isPrivate) null else previewStore[tab.id]
    val newTabTitle = stringResource(R.string.new_tab_title)
    val privateTabTitle = stringResource(R.string.private_tab_title)
    val displayTitle = when {
        tab.isPrivate -> privateTabTitle
        tab.title.isNotBlank() -> tab.title
        tab.url.isBlank() || tab.url == "about:blank" -> newTabTitle
        else -> host.ifBlank { tab.url }
    }
    val closeTabDescription = stringResource(R.string.close_named_tab_content_description, displayTitle)
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
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeDismissThresholdPx = with(density) {
        MotionTokens.TabSwipeDismissThresholdDp.dp.toPx()
    }
    val closeScale = remember(tab.id) { Animatable(1f) }
    var dragOffsetPx by remember(tab.id) { mutableFloatStateOf(0f) }
    var settling by remember(tab.id) { mutableStateOf(false) }
    var overSwipeThreshold by remember(tab.id) { mutableStateOf(false) }

    fun settle(target: Float, closeAfter: Boolean) {
        if (settling) return
        settling = true
        scope.launch {
            animate(
                initialValue = dragOffsetPx,
                targetValue = target,
                animationSpec = tween(
                    MotionTokens.TabRemove,
                    easing = MotionEasing.AccelerateDecelerate,
                ),
            ) { value, _ -> dragOffsetPx = value }
            settling = false
            overSwipeThreshold = false
            if (closeAfter) onClose()
        }
    }

    fun closeWithChromiumAnimation() {
        if (settling) return
        settling = true
        scope.launch {
            closeScale.animateTo(
                0.6f,
                animationSpec = tween(
                    durationMillis = MotionTokens.TabRemove / 2,
                    easing = MotionEasing.Linear,
                ),
            )
            closeScale.animateTo(
                0f,
                animationSpec = tween(
                    durationMillis = MotionTokens.TabRemove / 2,
                    easing = MotionEasing.FadeIn,
                ),
            )
            onClose()
        }
    }

    val dragAlpha = maxOf(
        0.2f,
        1f - 0.8f * abs(dragOffsetPx) / swipeDismissThresholdPx.coerceAtLeast(1f),
    )

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dragOffsetPx
                alpha = dragAlpha
                scaleX = closeScale.value
                scaleY = closeScale.value
            }
            .pointerInput(tab.id, gesturesEnabled, swipeDismissThresholdPx, swipeExitDistancePx) {
                if (!gesturesEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragCancel = { settle(0f, closeAfter = false) },
                    onDragEnd = {
                        if (abs(dragOffsetPx) >= swipeDismissThresholdPx) {
                            val direction = if (dragOffsetPx == 0f) 1f else dragOffsetPx.sign
                            settle(
                                direction * swipeExitDistancePx.coerceAtLeast(1f),
                                closeAfter = true,
                            )
                        } else {
                            settle(0f, closeAfter = false)
                        }
                    },
                ) { change, dragAmount ->
                    if (!settling) {
                        change.consume()
                        dragOffsetPx = (dragOffsetPx + dragAmount)
                            .coerceIn(-swipeExitDistancePx, swipeExitDistancePx)
                        val nowOverThreshold = abs(dragOffsetPx) >= swipeDismissThresholdPx
                        if (nowOverThreshold && !overSwipeThreshold) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        overSwipeThreshold = nowOverThreshold
                    }
                }
            }
            .clip(Radius.card)
            .background(cardColor)
            .border(if (isCurrent) 1.5.dp else 1.dp, borderColor, Radius.card)
            .selectable(
                selected = isCurrent,
                enabled = gesturesEnabled && !settling,
                role = Role.Tab,
                onClick = onSelect,
            ),
    ) {
        if (tab.isPrivate) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    displayTitle,
                    Modifier.align(Alignment.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                )
                IconButton(
                    onClick = ::closeWithChromiumAnimation,
                    enabled = gesturesEnabled && !settling,
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
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(if (host.isNotBlank()) tab.url else host, iconsDir, 17.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    displayTitle,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                )
                IconButton(
                    onClick = ::closeWithChromiumAnimation,
                    enabled = gesturesEnabled && !settling,
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
private fun TabPreviewFallback(tab: BrowserTabItemUiState, host: String, iconsDir: File) {
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
