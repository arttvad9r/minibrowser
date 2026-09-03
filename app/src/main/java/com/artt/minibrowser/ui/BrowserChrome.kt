@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.artt.minibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.artt.minibrowser.R
import java.io.File

@Composable
internal fun TopBar(
    state: BrowserChromeUiState,
    tabCount: Int,
    bookmarked: Boolean,
    iconsDir: File,
    omniboxFocus: FocusRequester,
    suggestions: List<BrowserSuggestionUiState>,
    onSuggestionQueryChanged: (String?) -> Unit,
    onSubmitQuery: (String) -> Unit,
    adblockStatus: BrowserExtensionUiState,
    onToggleAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onSiteInfo: () -> Unit,
    onSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onTranslate: () -> Unit,
    onToggleDesktop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedSuggestionIndex by remember { mutableStateOf(-1) }
    var fieldSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val rawUrl = state.url
    val newTab = rawUrl.isBlank() || rawUrl == "about:blank"
    val shown = if (focused) text else (if (newTab) "" else rawUrl)
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = focused) {
        focusManager.clearFocus(force = true)
    }
    val siteInfoDescription = stringResource(R.string.site_info_content_description)
    val searchActionDescription = stringResource(R.string.action_search)
    val searchDescription = stringResource(R.string.search_content_description)
    val omniboxDescription = stringResource(R.string.omnibox_hint)
    val newTabDescription = stringResource(R.string.new_tab_title)
    val tabsDescription = pluralStringResource(R.plurals.tabs_count, tabCount, tabCount)
    val menuDescription = stringResource(R.string.menu_content_description)
    val fieldRadius by animateDpAsState(
        targetValue = if (focused) 18.dp else 24.dp,
        animationSpec = tween(MotionTokens.ToolbarFocus, easing = MotionEasing.Transform),
        label = "omnibox radius",
    )
    val fieldColor by animateColorAsState(
        targetValue = when {
            focused -> MaterialTheme.colorScheme.surfaceContainer
            state.isPrivate -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(MotionTokens.ToolbarFocus, easing = MotionEasing.Transform),
        label = "omnibox surface",
    )

    LaunchedEffect(focused, text, rawUrl, newTab) {
        val userHasEdited = newTab || text != rawUrl
        onSuggestionQueryChanged(
            text.takeIf { focused && it.isNotBlank() && userHasEdited },
        )
    }
    LaunchedEffect(focused, text, suggestions) {
        selectedSuggestionIndex = -1
    }
    val navigate: (String) -> Unit = { query ->
        onSubmitQuery(query)
        focusManager.clearFocus(force = true)
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .onGloballyPositioned { fieldSize = it.size },
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(fieldRadius))
                    .background(fieldColor)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val leadingIsSearch = newTab || focused
                IconButton(
                    onClick = {
                        if (leadingIsSearch) {
                            if (text.isBlank()) omniboxFocus.requestFocus() else navigate(text)
                        } else {
                            onSiteInfo()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = when {
                                !leadingIsSearch -> siteInfoDescription
                                text.isNotBlank() -> searchActionDescription
                                else -> searchDescription
                            }
                        },
                ) {
                    AnimatedContent(
                        targetState = Triple(
                            leadingIsSearch,
                            state.isPrivate,
                            state.securityState == BrowserSecurityUiState.Secure,
                        ),
                        transitionSpec = {
                            fadeIn(
                                tween(MotionTokens.IconState, easing = MotionEasing.FadeIn),
                            ).togetherWith(
                                fadeOut(tween(MotionTokens.IconState, easing = MotionEasing.FadeOut)),
                            )
                        },
                        label = "omnibox leading icon",
                    ) { visual ->
                        if (visual.first) {
                            if (visual.second) {
                                Icon(
                                    AppIcons.Incognito,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Search,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (visual.second) {
                                    Icon(
                                        AppIcons.Incognito,
                                        null,
                                        Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (visual.third) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        null,
                                        Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Icon(
                                        AppIcons.Globe,
                                        null,
                                        Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (shown.isEmpty()) {
                        Text(
                            omniboxDescription,
                            Modifier.clearAndSetSemantics { },
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = shown,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(omniboxFocus)
                            .onFocusChanged {
                                if (it.isFocused && !focused) {
                                    text = if (newTab) "" else rawUrl
                                }
                                focused = it.isFocused
                                if (!it.isFocused) text = ""
                            }
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type != KeyEventType.KeyDown ||
                                    !focused ||
                                    suggestions.isEmpty()
                                ) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        selectedSuggestionIndex =
                                            if (
                                                selectedSuggestionIndex < 0 ||
                                                selectedSuggestionIndex >= suggestions.lastIndex
                                            ) {
                                                0
                                            } else {
                                                selectedSuggestionIndex + 1
                                            }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        selectedSuggestionIndex =
                                            if (selectedSuggestionIndex <= 0) {
                                                suggestions.lastIndex
                                            } else {
                                                selectedSuggestionIndex - 1
                                            }
                                        true
                                    }
                                    Key.Enter -> {
                                        val selected =
                                            suggestions.getOrNull(selectedSuggestionIndex)
                                                ?: return@onPreviewKeyEvent false
                                        focusManager.clearFocus(force = true)
                                        onNavigate(selected.url)
                                        true
                                    }
                                    Key.Escape -> {
                                        focusManager.clearFocus(force = true)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .semantics { contentDescription = omniboxDescription },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { navigate(text) }),
                    )
                }
            }
            if (focused && suggestions.isNotEmpty()) {
                val density = LocalDensity.current
                val offsetY = with(density) { 8.dp.roundToPx() }
                val suggestionsWidth = with(density) { fieldSize.width.toDp() }
                val popupVisibility = remember {
                    MutableTransitionState(false).apply { targetState = true }
                }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, fieldSize.height + offsetY),
                    onDismissRequest = { focusManager.clearFocus(force = true) },
                    properties = androidx.compose.ui.window.PopupProperties(
                        focusable = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                ) {
                    AnimatedVisibility(
                        visibleState = popupVisibility,
                        enter = fadeIn(tween(MotionTokens.Popup, easing = MotionEasing.FadeIn)),
                        exit = fadeOut(tween(MotionTokens.Popup, easing = MotionEasing.FadeOut)),
                    ) {
                        Column(
                            Modifier
                                .width(suggestionsWidth)
                                .heightIn(max = 176.dp)
                                .clip(Radius.card)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card),
                        ) {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                suggestions.forEachIndexed { index, s ->
                                    SuggestionRow(
                                        s = s,
                                        iconsDir = iconsDir,
                                        selected = index == selectedSuggestionIndex,
                                    ) {
                                        focusManager.clearFocus()
                                        onNavigate(s.url)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !focused,
            enter = expandHorizontally(
                animationSpec = tween(MotionTokens.ToolbarFocus, easing = MotionEasing.Transform),
                expandFrom = Alignment.End,
            ) + fadeIn(
                tween(MotionTokens.ToolbarButton, easing = MotionEasing.FadeIn),
            ),
            exit = shrinkHorizontally(
                animationSpec = tween(MotionTokens.ToolbarFocus, easing = MotionEasing.Transform),
                shrinkTowards = Alignment.End,
            ) + fadeOut(
                tween(MotionTokens.ToolbarButton, easing = MotionEasing.FadeOut),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { focusManager.clearFocus(force = true); onNewTab() },
                    modifier = Modifier.semantics { contentDescription = newTabDescription },
                ) {
                    Icon(Icons.Filled.Add, null)
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(Radius.button)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .softClickable { focusManager.clearFocus(force = true); onSwitcher() }
                        .semantics { contentDescription = tabsDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = tabCount,
                        transitionSpec = {
                            fadeIn(
                                tween(MotionTokens.IconState, easing = MotionEasing.FadeIn),
                            ).togetherWith(
                                fadeOut(tween(MotionTokens.IconState, easing = MotionEasing.FadeOut)),
                            )
                        },
                        label = "toolbar tab count",
                    ) { count ->
                        Text("$count", style = MaterialTheme.typography.titleMedium)
                    }
                }
                IconButton(
                    onClick = { focusManager.clearFocus(force = true); menuOpen = true },
                    modifier = Modifier.semantics { contentDescription = menuDescription },
                ) {
                    Icon(Icons.Filled.MoreVert, null)
                }
            }
        }
    }

    if (menuOpen) {
        MenuSheet(
            state = state,
            bookmarked = bookmarked,
            adblockStatus = adblockStatus,
            onDismiss = { menuOpen = false },
            onNewPrivateTab = onNewPrivateTab,
            onBack = onBack,
            onForward = onForward,
            onReload = onReload,
            onToggleBookmark = onToggleBookmark,
            onBookmarks = onBookmarks,
            onHistory = onHistory,
            onDownloads = onDownloads,
            onFind = onFind,
            onShare = onShare,
            onTranslate = onTranslate,
            onToggleAdblock = onToggleAdblock,
            onRetryAdblock = onRetryAdblock,
            onSettings = onSettings,
            onToggleDesktop = onToggleDesktop,
        )
    }
}

@Composable
private fun SuggestionRow(
    s: BrowserSuggestionUiState,
    iconsDir: File,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val rowColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = tween(MotionTokens.IconState, easing = MotionEasing.Transform),
        label = "suggestion selection",
    )
    LaunchedEffect(selected) {
        if (selected) bringIntoViewRequester.bringIntoView()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .heightIn(min = 56.dp)
            .clip(Radius.small)
            .background(rowColor)
            .then(
                if (selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, Radius.small)
                } else {
                    Modifier
                },
            )
            .softClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Favicon(s.url, iconsDir, 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.label.ifBlank { s.url },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (s.label.isNotBlank()) {
                Text(
                    hostOf(s.url).ifBlank { s.url },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MenuSheet(
    state: BrowserChromeUiState,
    bookmarked: Boolean,
    adblockStatus: BrowserExtensionUiState,
    onDismiss: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onTranslate: () -> Unit,
    onToggleAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    onSettings: () -> Unit,
    onToggleDesktop: () -> Unit,
) {
    val httpPage = state.isWebPage
    val density = LocalDensity.current
    val popupOffset = IntOffset(
        with(density) { (-8).dp.roundToPx() },
        with(density) { 60.dp.roundToPx() },
    )
    fun dismissThen(action: () -> Unit) {
        onDismiss()
        action()
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = popupOffset,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Column(
            Modifier
                .width(260.dp)
                .heightIn(max = 500.dp)
                .clip(Radius.card)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MenuSection {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MenuNavigationAction(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.action_back),
                        state.canGoBack,
                        Modifier.weight(1f),
                    ) { dismissThen(onBack) }
                    MenuNavigationAction(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        stringResource(R.string.action_forward),
                        state.canGoForward,
                        Modifier.weight(1f),
                    ) { dismissThen(onForward) }
                    MenuNavigationAction(
                        if (state.isLoading) Icons.Filled.Stop else Icons.Filled.Refresh,
                        stringResource(if (state.isLoading) R.string.action_stop else R.string.action_reload),
                        httpPage,
                        Modifier.weight(1f),
                    ) { dismissThen(onReload) }
                    MenuNavigationAction(
                        if (bookmarked) Icons.Filled.Star else AppIcons.Star,
                        stringResource(
                            if (bookmarked) R.string.remove_from_bookmarks_action else R.string.add_to_bookmarks_action,
                        ),
                        httpPage,
                        Modifier.weight(1f),
                    ) { dismissThen(onToggleBookmark) }
                }
            }

            MenuSection {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        CompactMenuQuickAction(AppIcons.Incognito, stringResource(R.string.private_tab_title)) {
                            dismissThen(onNewPrivateTab)
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        CompactMenuQuickAction(AppIcons.Download, stringResource(R.string.downloads_title)) {
                            dismissThen(onDownloads)
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        CompactMenuQuickAction(AppIcons.History, stringResource(R.string.history_title)) {
                            dismissThen(onHistory)
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        CompactMenuQuickAction(AppIcons.Star, stringResource(R.string.bookmarks_title)) {
                            dismissThen(onBookmarks)
                        }
                    }
                }
            }

            if (httpPage) {
                MenuSection {
                    CompactMenuRow(
                        Icons.Filled.Search,
                        stringResource(R.string.find_on_page),
                        onClick = { dismissThen(onFind) },
                    )
                    CompactMenuToggleRow(
                        AppIcons.Desktop,
                        stringResource(R.string.desktop_site),
                        state.desktop,
                        onChecked = { dismissThen(onToggleDesktop) },
                    )
                    CompactMenuRow(
                        Icons.Filled.Share,
                        stringResource(R.string.action_share),
                        onClick = { dismissThen(onShare) },
                    )
                    CompactMenuRow(
                        AppIcons.Globe,
                        stringResource(R.string.translate_page),
                        onClick = { dismissThen(onTranslate) },
                    )
                }
            }

            MenuSection {
                when (adblockStatus) {
                    BrowserExtensionUiState.Installing ->
                        CompactMenuRow(
                            AppIcons.Shield,
                            stringResource(R.string.settings_adblock),
                            dense = true,
                            enabled = false,
                            trailing = {
                                Text(
                                    stringResource(R.string.extension_starting),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    BrowserExtensionUiState.Error ->
                        CompactMenuRow(
                            AppIcons.Shield,
                            stringResource(R.string.settings_adblock),
                            dense = true,
                            trailing = {
                                Text(
                                    stringResource(R.string.extension_retry_short),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { dismissThen(onRetryAdblock) },
                        )
                    BrowserExtensionUiState.Enabled ->
                        CompactMenuToggleRow(
                            AppIcons.Shield,
                            stringResource(R.string.settings_adblock),
                            true,
                            dense = true,
                            onToggleAdblock,
                        )
                    BrowserExtensionUiState.Disabled ->
                        CompactMenuToggleRow(
                            AppIcons.Shield,
                            stringResource(R.string.settings_adblock),
                            false,
                            dense = true,
                            onToggleAdblock,
                        )
                }
                CompactMenuRow(
                    Icons.Filled.Settings,
                    stringResource(R.string.settings_title),
                    dense = true,
                    onClick = { dismissThen(onSettings) },
                )
            }
        }
    }
}

@Composable
private fun MenuSection(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
    ) {
        content()
    }
}

@Composable
private fun MenuNavigationAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier
            .height(40.dp)
            .clip(Radius.small)
            .softClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                fadeIn(
                    tween(MotionTokens.IconState, easing = MotionEasing.FadeIn),
                ).togetherWith(
                    fadeOut(tween(MotionTokens.IconState, easing = MotionEasing.FadeOut)),
                )
            },
            label = "menu navigation icon",
        ) { targetIcon ->
            Icon(
                targetIcon,
                null,
                Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
    }
}

@Composable
private fun CompactMenuQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.small)
            .softClickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CompactMenuRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (!enabled && onClick != null) return

    val alpha = if (enabled) 1f else 0.52f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (dense) 34.dp else 40.dp)
            .clip(Radius.small)
            .then(
                if (onClick != null) {
                    Modifier.softClickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        trailing?.invoke()
    }
}

@Composable
private fun CompactMenuToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    dense: Boolean = false,
    onChecked: (Boolean) -> Unit,
) {
    CompactMenuRow(
        icon = icon,
        label = label,
        dense = dense,
        trailing = { CompactMenuSwitch(checked) },
        onClick = { onChecked(!checked) },
    )
}

@Composable
private fun CompactMenuSwitch(checked: Boolean) {
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier
            .width(34.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(16.dp)
                .background(thumbColor, CircleShape),
        )
    }
}

@Composable
internal fun SiteInfoSheet(
    state: BrowserChromeUiState,
    adblockEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val newTabTitle = stringResource(R.string.new_tab_title)
    val host = hostOf(state.url).ifBlank { state.url.ifBlank { newTabTitle } }
    val message = when (state.securityState) {
        BrowserSecurityUiState.Secure -> stringResource(R.string.security_secure)
        BrowserSecurityUiState.Exception -> stringResource(R.string.security_exception)
        BrowserSecurityUiState.Insecure -> stringResource(R.string.security_insecure)
        BrowserSecurityUiState.Unknown -> stringResource(R.string.security_unknown)
    }
    BrowserBottomSheet(onDismissRequest = onDismiss) {
        Text(host, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SheetRow(
            AppIcons.Shield,
            stringResource(R.string.settings_adblock),
            trailing = {
                Text(
                    stringResource(if (adblockEnabled) R.string.state_on else R.string.state_off),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
internal fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    val errorHint = stringResource(R.string.page_error_hint)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                error(errorHint)
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            errorHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
