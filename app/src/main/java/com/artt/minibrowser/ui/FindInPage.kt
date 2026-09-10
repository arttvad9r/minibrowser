package com.artt.minibrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.R
import com.artt.minibrowser.engine.BrowserCommandTarget
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.browserCommandTargetForTab
import com.artt.minibrowser.engine.clearBrowserFindMatches
import kotlinx.coroutines.delay
import mozilla.components.browser.state.state.content.FindResultState
import mozilla.components.browser.state.store.BrowserStore
import org.mozilla.geckoview.GeckoSession

internal data class FindBarUiState(
    val query: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val resultsReady: Boolean = false,
)

internal class FindRequestTracker {
    private var revision = 0L

    fun begin(): Long = ++revision

    fun invalidate() {
        revision++
    }

    fun isCurrent(requestRevision: Long): Boolean = requestRevision == revision
}

internal fun formatFindCounter(current: Int, total: Int): String =
    if (current >= 0 && total >= 0) "$current/$total" else ""

/** Matches Android Components' FindInPageBar zero-based -> human-readable result conversion. */
internal fun androidComponentsFindPosition(result: FindResultState): Pair<Int, Int> {
    val current = if (result.numberOfMatches > 0) {
        result.activeMatchOrdinal + 1
    } else {
        result.activeMatchOrdinal
    }
    return current to result.numberOfMatches
}

/** Owns find interaction while routing commands to the tab's current session owner. */
@Composable
internal fun FindInPageRoute(
    tab: Tab,
    browserStore: BrowserStore,
    onClose: () -> Unit,
) {
    // BrowserPageContent keys this route by tab id. Keep the query/results stable if crash recovery
    // swaps the raw GeckoSession inside the same logical tab, matching the pre-extraction behavior.
    var query by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var resultsReady by remember { mutableStateOf(false) }
    val requestTracker = remember { FindRequestTracker() }
    val browserStoreState by browserStore.stateFlow.collectAsStateWithLifecycle()
    val commandTarget = browserCommandTargetForTab(tab, browserStore)
    val activeTarget by rememberUpdatedState(commandTarget)
    val linkedFindResults = browserStoreState.tabs
        .firstOrNull { it.id == tab.id.toString() }
        ?.content
        ?.findResults
        .orEmpty()

    val rawFind: (GeckoSession, Boolean) -> Unit = { session, backward ->
        if (query.isBlank()) {
            requestTracker.invalidate()
            session.finder.clear()
            current = 0
            total = 0
            resultsReady = false
        } else {
            val requestedQuery = query
            val requestedSession = session
            val requestRevision = requestTracker.begin()
            session.finder.find(
                requestedQuery,
                if (backward) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD,
            ).accept { result ->
                val currentRawSession = (activeTarget as? BrowserCommandTarget.Raw)?.session
                if (
                    requestTracker.isCurrent(requestRevision) &&
                    currentRawSession === requestedSession &&
                    query == requestedQuery
                ) {
                    current = result?.current ?: 0
                    total = result?.total ?: 0
                    resultsReady = true
                }
            }
        }
    }

    LaunchedEffect(query, commandTarget) {
        if (query.isNotBlank()) delay(70)
        when (val target = activeTarget) {
            is BrowserCommandTarget.Raw -> rawFind(target.session, false)
            is BrowserCommandTarget.Linked -> {
                requestTracker.invalidate()
                if (query.isBlank()) {
                    target.session.clearFindMatches()
                    current = 0
                    total = 0
                    resultsReady = false
                } else {
                    target.session.findAll(query)
                }
            }
            null -> {
                requestTracker.invalidate()
                current = 0
                total = 0
                resultsReady = false
            }
        }
    }

    LaunchedEffect(commandTarget, linkedFindResults) {
        if (commandTarget !is BrowserCommandTarget.Linked || query.isBlank()) return@LaunchedEffect
        val result = linkedFindResults.lastOrNull()
        if (result == null) {
            current = 0
            total = 0
            resultsReady = false
        } else {
            val position = androidComponentsFindPosition(result)
            current = position.first
            total = position.second
            resultsReady = true
        }
    }

    val stepFind: (Boolean) -> Unit = { forward ->
        if (query.isNotBlank()) {
            when (val target = browserCommandTargetForTab(tab, browserStore)) {
                is BrowserCommandTarget.Raw -> rawFind(target.session, !forward)
                is BrowserCommandTarget.Linked -> target.session.findNext(forward)
                null -> Unit
            }
        }
    }

    FindBarContent(
        state = FindBarUiState(
            query = query,
            current = current,
            total = total,
            resultsReady = resultsReady,
        ),
        onQueryChange = {
            requestTracker.invalidate()
            query = it
            current = 0
            total = 0
            resultsReady = false
        },
        onPrevious = { stepFind(false) },
        onNext = { stepFind(true) },
        onClose = {
            requestTracker.invalidate()
            clearBrowserFindMatches(tab, browserStore)
            onClose()
        },
    )
}

/** Pure find-in-page renderer. */
@Composable
internal fun FindBarContent(
    state: FindBarUiState,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

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
                state.query,
                onQueryChange,
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = stringResource(R.string.find_on_page),
            )
            if (state.resultsReady) {
                val resultDescription = if (state.total > 0) {
                    stringResource(R.string.find_match_position, state.current, state.total)
                } else {
                    stringResource(R.string.find_no_matches)
                }
                Text(
                    formatFindCounter(state.current, state.total),
                    Modifier.clearAndSetSemantics {
                        contentDescription = resultDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FindActionButton(
            Icons.Filled.KeyboardArrowUp,
            stringResource(R.string.previous_match_content_description),
            onPrevious,
        )
        FindActionButton(
            Icons.Filled.KeyboardArrowDown,
            stringResource(R.string.next_match_content_description),
            onNext,
        )
        FindActionButton(
            Icons.Filled.Close,
            stringResource(R.string.close_find_content_description),
            onClose,
        )
    }
}

@Composable
private fun FindActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
