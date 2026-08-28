@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
