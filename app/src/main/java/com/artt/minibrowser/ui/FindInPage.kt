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
import com.artt.minibrowser.R
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession

internal data class FindBarUiState(
    val query: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val resultsReady: Boolean = false,
)

internal fun formatFindCounter(current: Int, total: Int): String =
    if (current >= 0 && total >= 0) "$current/$total" else ""

/** Owns Gecko finder interaction while keeping [FindBarContent] state/callback driven. */
@Composable
internal fun FindInPageRoute(
    session: GeckoSession,
    onClose: () -> Unit,
) {
    // BrowserPageContent keys this route by tab id. Keep the query/results stable if crash recovery
    // swaps the GeckoSession inside the same logical tab, matching the pre-extraction behavior.
    var query by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var resultsReady by remember { mutableStateOf(false) }

    val find: (Boolean) -> Unit = { backward ->
        if (query.isBlank()) {
            session.finder.clear()
            current = 0
            total = 0
            resultsReady = false
        } else {
            val requestedQuery = query
            session.finder.find(
                requestedQuery,
                if (backward) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD,
            ).accept { result ->
                if (query == requestedQuery) {
                    current = result?.current ?: 0
                    total = result?.total ?: 0
                    resultsReady = true
                }
            }
        }
    }

    LaunchedEffect(query, session) {
        if (query.isNotBlank()) delay(70)
        find(false)
    }

    FindBarContent(
        state = FindBarUiState(
            query = query,
            current = current,
            total = total,
            resultsReady = resultsReady,
        ),
        onQueryChange = {
            query = it
            current = 0
            total = 0
            resultsReady = false
        },
        onPrevious = { find(true) },
        onNext = { find(false) },
        onClose = {
            session.finder.clear()
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
