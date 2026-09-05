package com.artt.minibrowser.ui

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
    Box(Modifier.fillMaxSize()) {
        BrowserTabSwitcher(
            tabs = tabs,
            currentId = currentId,
            iconsDir = iconsDir,
            previewStore = previewStore,
            onSelect = onSelect,
            onClose = onClose,
            onNew = onNew,
            onDismiss = onDismiss,
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
                enabled = tabs.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = actionsDescription },
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (tabs.any { it.isPrivate }) {
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
