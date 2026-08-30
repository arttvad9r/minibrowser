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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import com.artt.minibrowser.engine.formatFindCounter
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession

internal data class FindBarUiState(
    val query: String = "",
    val current: Int = 0,
    val total: Int = 0,
)

/** Owns Gecko finder interaction while keeping [FindBarContent] state/callback driven. */
@Composable
internal fun FindInPageRoute(
    session: GeckoSession,
    onClose: () -> Unit,
) {
    var query by remember(session) { mutableStateOf("") }
    var current by remember(session) { mutableIntStateOf(0) }
    var total by remember(session) { mutableIntStateOf(0) }

    val find: (Boolean) -> Unit = { backward ->
        if (query.isBlank()) {
            session.finder.clear()
            current = 0
            total = 0
        } else {
            session.finder.find(
                query,
                if (backward) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD,
            ).accept { result ->
                current = result?.current ?: 0
                total = result?.total ?: 0
            }
        }
    }

    LaunchedEffect(query, session) {
        if (query.isNotBlank()) delay(70)
        find(false)
    }

    FindBarContent(
        state = FindBarUiState(query = query, current = current, total = total),
        onQueryChange = { query = it },
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
                Modifier.weight(1f),
                placeholder = stringResource(R.string.find_on_page),
            )
            if (state.total > 0) {
                Text(
                    formatFindCounter(state.current, state.total),
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
