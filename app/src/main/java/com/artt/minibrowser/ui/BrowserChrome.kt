@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.artt.minibrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import com.artt.minibrowser.R
import com.artt.minibrowser.data.Suggestion
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.SecurityState
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.formatFindCounter
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.io.File

@Composable
internal fun GeckoContent(
    tab: Tab?,
    modifier: Modifier = Modifier,
) {
    val session = tab?.session
    val tabId = tab?.id
    val url = tab?.url.orEmpty()
    val isPrivate = tab?.isPrivate == true
    val pageSettled = tab != null && tab.progress < 0f &&
        (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))

    AndroidView(
        factory = { context -> GeckoView(context) },
        update = { view ->
            if (view.session !== session) {
                TabPreviewStore.captureBeforeSessionSwap(view)
                view.releaseSession()
                session?.let(view::setSession)
            }
            TabPreviewStore.maybeCapture(
                view = view,
                tabId = tabId,
                url = url,
                isPrivate = isPrivate,
                pageSettled = pageSettled,
            )
        },
        modifier = modifier,
    )
}

@Composable
internal fun TopBar(
    tab: Tab?,
    tabCount: Int,
    bookmarked: Boolean,
    iconsDir: File,
    omniboxFocus: FocusRequester,
    suggestions: List<Suggestion>,
    onSuggestionQueryChanged: (String?) -> Unit,
    onSubmitQuery: (String) -> Unit,
    adblockStatus: ExtensionLoader.Status?,
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
    onSettings: () -> Unit,
    onTranslate: () -> Unit,
    onToggleDesktop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val rawUrl = tab?.url ?: ""
    val newTab = rawUrl.isBlank() || rawUrl == "about:blank"
    val shown = if (focused) text else (if (newTab) "" else rawUrl)
    val focusManager = LocalFocusManager.current
    val siteInfoDescription = stringResource(R.string.site_info_content_description)
    val searchActionDescription = stringResource(R.string.action_search)
    val searchDescription = stringResource(R.string.search_content_description)
    val newTabDescription = stringResource(R.string.new_tab_title)
    val tabsDescription = pluralStringResource(R.plurals.tabs_count, tabCount, tabCount)
    val menuDescription = stringResource(R.string.menu_content_description)
    LaunchedEffect(focused, text, rawUrl, newTab) {
        val userHasEdited = newTab || text != rawUrl
        onSuggestionQueryChanged(
            text.takeIf { focused && it.isNotBlank() && userHasEdited },
        )
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
                    .clip(Radius.field)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val leadingIsSearch = newTab
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
                    if (leadingIsSearch) {
                        if (tab?.isPrivate == true) {
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
                            if (tab?.isPrivate == true) {
                                Icon(
                                    AppIcons.Incognito,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (tab?.securityState == SecurityState.Secure) {
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
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(R.string.omnibox_hint),
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
                            },
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
                val suggestionsWidth = with(density) {
                    (fieldSize.width + 48.dp.roundToPx() + 48.dp.roundToPx() + 48.dp.roundToPx() + 8.dp.roundToPx()).toDp()
                }
                val suggestionsHeight = (suggestions.size * 56 + 8).coerceAtMost(176).dp
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, fieldSize.height + offsetY),
                    onDismissRequest = { focusManager.clearFocus(force = true) },
                ) {
                    Column(
                        Modifier
                            .width(suggestionsWidth)
                            .height(suggestionsHeight)
                            .clip(Radius.card)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card),
                    ) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            suggestions.forEach { s ->
                                SuggestionRow(s, iconsDir) {
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
                .clickable { focusManager.clearFocus(force = true); onSwitcher() }
                .semantics { contentDescription = tabsDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text("$tabCount", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(
            onClick = { focusManager.clearFocus(force = true); menuOpen = true },
            modifier = Modifier.semantics { contentDescription = menuDescription },
        ) {
            Icon(Icons.Filled.MoreVert, null)
        }
    }

    if (menuOpen) {
        MenuSheet(
            tab = tab,
            bookmarked = bookmarked,
            adblockStatus = adblockStatus,
            onDismiss = { menuOpen = false },
            onNewTab = onNewTab,
            onNewPrivateTab = onNewPrivateTab,
            onBack = onBack,
            onForward = onForward,
            onReload = onReload,
            onToggleBookmark = onToggleBookmark,
            onBookmarks = onBookmarks,
            onHistory = onHistory,
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
private fun SuggestionRow(s: Suggestion, iconsDir: File, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
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
    tab: Tab?,
    bookmarked: Boolean,
    adblockStatus: ExtensionLoader.Status?,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onTranslate: () -> Unit,
    onToggleAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    onSettings: () -> Unit,
    onToggleDesktop: () -> Unit,
) {
    val httpPage = tab?.url?.startsWith("http") == true
    BrowserBottomSheet(onDismissRequest = onDismiss) { dismissThen ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Radius.button)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MenuNavigationAction(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.action_back),
                tab?.canGoBack == true,
                Modifier.weight(1f),
            ) { dismissThen(onBack) }
            MenuNavigationAction(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.action_forward),
                tab?.canGoForward == true,
                Modifier.weight(1f),
            ) { dismissThen(onForward) }
            MenuNavigationAction(
                Icons.Filled.Refresh,
                stringResource(R.string.action_reload),
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
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(Icons.Filled.Add, stringResource(R.string.new_tab_title)) { dismissThen(onNewTab) }
            QuickAction(AppIcons.Incognito, stringResource(R.string.private_tab_title)) { dismissThen(onNewPrivateTab) }
            QuickAction(AppIcons.Star, stringResource(R.string.bookmarks_title)) { dismissThen(onBookmarks) }
            QuickAction(AppIcons.History, stringResource(R.string.history_title)) { dismissThen(onHistory) }
        }
        MenuDivider()
        SheetRow(
            Icons.Filled.Search,
            stringResource(R.string.find_on_page),
            enabled = httpPage,
            onClick = { dismissThen(onFind) },
        )
        tab?.takeIf { httpPage }?.let { currentTab ->
            ToggleRow(
                AppIcons.Desktop,
                stringResource(R.string.desktop_site),
                currentTab.desktop,
                onChecked = { dismissThen(onToggleDesktop) },
            )
        }
        SheetRow(
            Icons.Filled.Share,
            stringResource(R.string.action_share),
            enabled = httpPage,
            onClick = { dismissThen(onShare) },
        )
        SheetRow(
            AppIcons.Globe,
            stringResource(R.string.translate_page),
            enabled = httpPage,
            onClick = { dismissThen(onTranslate) },
        )
        MenuDivider()
        when (adblockStatus) {
            null, ExtensionLoader.Status.Installing ->
                SheetRow(
                    AppIcons.Shield,
                    stringResource(R.string.settings_adblock),
                    enabled = false,
                    trailing = {
                        Text(
                            stringResource(R.string.extension_starting),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            ExtensionLoader.Status.Error ->
                SheetRow(
                    AppIcons.Shield,
                    stringResource(R.string.settings_adblock),
                    trailing = {
                        Text(
                            stringResource(R.string.extension_retry_short),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { dismissThen(onRetryAdblock) },
                )
            ExtensionLoader.Status.Enabled ->
                ToggleRow(
                    AppIcons.Shield,
                    stringResource(R.string.settings_adblock),
                    true,
                    onToggleAdblock,
                    subtitle = stringResource(R.string.settings_adblock_subtitle),
                )
            ExtensionLoader.Status.Disabled ->
                ToggleRow(
                    AppIcons.Shield,
                    stringResource(R.string.settings_adblock),
                    false,
                    onToggleAdblock,
                    subtitle = stringResource(R.string.settings_adblock_subtitle),
                )
        }
        SheetRow(
            Icons.Filled.Settings,
            stringResource(R.string.settings_title),
            onClick = { dismissThen(onSettings) },
        )
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
            .height(48.dp)
            .clip(Radius.small)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    }
}

@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(4.dp))
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun FindBar(session: GeckoSession, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val doFind: (Boolean) -> Unit = { backward ->
        if (q.isBlank()) {
            session.finder.clear()
            total = 0
            current = 0
        } else {
            session.finder.find(
                q,
                if (backward) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD,
            ).accept { result ->
                total = result?.total ?: 0
                current = result?.current ?: 0
            }
        }
    }
    LaunchedEffect(q, session) {
        if (q.isNotBlank()) delay(70)
        doFind(false)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(Radius.field)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserTextField(
                q,
                { q = it },
                Modifier.weight(1f),
                placeholder = stringResource(R.string.find_on_page),
            )
            if (total > 0) {
                Text(
                    formatFindCounter(current, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconBtn(Icons.Filled.KeyboardArrowUp, stringResource(R.string.previous_match_content_description)) {
            doFind(true)
        }
        IconBtn(Icons.Filled.KeyboardArrowDown, stringResource(R.string.next_match_content_description)) {
            doFind(false)
        }
        IconBtn(Icons.Filled.Close, stringResource(R.string.close_find_content_description)) {
            session.finder.clear()
            onClose()
        }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = desc }) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SiteInfoSheet(tab: Tab, adblockEnabled: Boolean, onDismiss: () -> Unit) {
    val newTabTitle = stringResource(R.string.new_tab_title)
    val host = hostOf(tab.url).ifBlank { tab.url.ifBlank { newTabTitle } }
    val message = when (tab.securityState) {
        SecurityState.Secure -> stringResource(R.string.security_secure)
        SecurityState.Exception -> stringResource(R.string.security_exception)
        SecurityState.Insecure -> stringResource(R.string.security_insecure)
        SecurityState.Unknown -> stringResource(R.string.security_unknown)
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
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.page_error_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
