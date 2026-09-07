package com.artt.minibrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import java.io.File

internal fun shouldCreateTabBeforeOverviewDismiss(tabCount: Int, newTabRequested: Boolean): Boolean =
    tabCount == 0 && !newTabRequested

/** Adds bulk actions without making the tab overview itself more visually dense. */
@Composable
internal fun BrowserTabSwitcher(
    tabs: List<BrowserTabItemUiState>,
    currentId: Long?,
    iconsDir: File,
    previewStore: TabPreviewStore,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onCloseAll: () -> Unit,
    onClosePrivate: () -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newTabRequested by remember { mutableStateOf(false) }
    var frozenTabsDuringExit by remember {
        mutableStateOf<List<BrowserTabItemUiState>?>(null)
    }

    fun createNewTab() {
        // Creating the real tab updates TabManager immediately, while the overview is still running
        // its exit/new-tab animation. Keep the visible overview snapshot stable so its counter/card
        // list does not briefly jump 0 -> 1 (or N -> N+1) before disappearing.
        frozenTabsDuringExit = tabs
        newTabRequested = true
        onNew()
    }

    fun dismissOverview() {
        // A zero-tab overview is valid while it is visible. Once the user leaves it, restore the
        // browser invariant that the page chrome always belongs to a real tab. Freeze the visible
        // zero-tab state first: otherwise the synthetic blank tab can flash "1 вкладка" for a frame
        // before the overview disappears. Avoid a second tab when dismissal follows the "+" action.
        if (shouldCreateTabBeforeOverviewDismiss(tabs.size, newTabRequested)) {
            frozenTabsDuringExit = tabs
            onNew()
        }
        newTabRequested = false
        onDismiss()
    }

    val overviewTabs = frozenTabsDuringExit ?: tabs

    Box(
        Modifier
            .fillMaxSize()
            // The original overview fades its own background. Keep an opaque surface behind it so
            // Gecko/start-page pixels never bleed through during entry or exit frames.
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrowserTabSwitcher(
            tabs = overviewTabs,
            currentId = currentId,
            iconsDir = iconsDir,
            previewStore = previewStore,
            onSelect = onSelect,
            onClose = onClose,
            onNew = ::createNewTab,
            onDismiss = ::dismissOverview,
        )

        var expanded by remember { mutableStateOf(false) }
        val actionsDescription = stringResource(R.string.tab_actions_content_description)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp, end = 56.dp),
        ) {
            IconButton(
                onClick = { expanded = true },
                enabled = overviewTabs.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = actionsDescription },
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (overviewTabs.any { it.isPrivate }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.close_private_tabs)) },
                        onClick = {
                            expanded = false
                            onClosePrivate()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.close_all_tabs)) },
                    onClick = {
                        expanded = false
                        onCloseAll()
                    },
                )
            }
        }
    }
}
