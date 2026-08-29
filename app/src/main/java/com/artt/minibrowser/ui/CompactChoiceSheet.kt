@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Content-height picker sheet. Material3's default partially-expanded anchor can make a short
 * four-item chooser occupy roughly half the display; pickers should instead open only as tall as
 * their content while retaining the native modal-sheet gesture and animation.
 */
@Composable
fun CompactChoiceSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        modifier = Modifier.wrapContentHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Radius.sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            content()
        }
    }
}

/** 48dp touch target; the trailing slot is fixed so selection never causes layout or scale motion. */
@Composable
fun CompactChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
            }
        }
    }
}
