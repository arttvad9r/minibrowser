@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser.ui

// Переиспользуемые примитивы дизайн-системы: заголовки секций, строки настроек/меню,
// поля, empty states и действия закладки.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import com.artt.minibrowser.net.webUriHost
import kotlinx.coroutines.launch

fun hostOf(url: String): String = webUriHost(url).orEmpty()

/** Заголовок секции: слева название, справа необязательное действие («Все ›»). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionContentDescription: String? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            val actionDescription = actionContentDescription ?: actionLabel
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(Radius.small)
                    .softClickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { contentDescription = actionDescription },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    actionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(
                    AppIcons.ChevronRight,
                    null,
                    Modifier.padding(start = 2.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Пустое состояние: небольшая outline-иконка + заголовок + строка пояснения. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Нейтральное поле ввода без рамки (вставляется в подложку вызывающим кодом). */
@Composable
fun BrowserTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    keyboardActions: KeyboardActions = KeyboardActions(),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            if (placeholder.isNotEmpty()) contentDescription = placeholder
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        Modifier.clearAndSetSemantics { },
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

/** Строка bottom sheet / списка: иконка + подпись + необязательный trailing. */
@Composable
fun SheetRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (!enabled && onClick != null) return

    val alpha = if (enabled) 1f else 0.52f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(Radius.small)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        trailing?.invoke()
    }
}

/** Строка с переключателем. */
@Composable
fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Быстрое действие главного меню: круглая подложка + иконка + подпись. */
@Composable
fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(Radius.button)
            .softClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** Карточка-группа настроек: единая поверхность с bounded ripple по форме. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card),
    ) { content() }
}

/** Строка внутри SettingsGroup. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Строка выбора: минимум 48dp; при крупном шрифте строка может расширяться по высоте. */
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Bottom sheet with Chromium's current settle timing. The Material3 version pinned by this project
 * keeps SheetState's animation specs internal, so [applyChromiumBottomSheetMotion] updates those
 * library-owned fields after Material's own SideEffect. This preserves Material's gestures and
 * accessibility while using Chromium's 350 ms expand / 250 ms shrink EMPHASIZED motion.
 */
@Composable
fun BrowserBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable (dismissThen: (after: () -> Unit) -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }

    val dismissThen: (after: () -> Unit) -> Unit = { after ->
        if (!dismissing) {
            dismissing = true
            after()
            scope.launch {
                state.hide()
                if (!state.isVisible) onDismissRequest()
                dismissing = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = if (dismissing) Color.Transparent else BottomSheetDefaults.ScrimColor,
        shape = Radius.sheet,
    ) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            content(dismissThen)
        }
    }

    // Registered after ModalBottomSheet's own SideEffect, so these exact Chromium specs win before
    // the sheet's show LaunchedEffect or any subsequent programmatic hide call executes.
    SideEffect { applyChromiumBottomSheetMotion(state) }
}

/** Действия над закладкой без зависимости reusable UI от data-layer модели. */
@Composable
fun BookmarkActionsSheet(
    bookmarkKey: String,
    bookmarkTitle: String,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var text by remember(bookmarkKey) { mutableStateOf(bookmarkTitle) }
    val renameFocusRequester = remember { FocusRequester() }
    val submitRename: () -> Unit = { onRename(text.trim().ifBlank { bookmarkTitle }) }
    LaunchedEffect(renaming) {
        if (renaming) renameFocusRequester.requestFocus()
    }
    BrowserBottomSheet(onDismissRequest = onDismiss) { dismissThen ->
        Column {
            if (renaming) {
                Text(stringResource(R.string.action_rename), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        BrowserTextField(
                            text,
                            { text = it },
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(renameFocusRequester),
                            placeholder = stringResource(R.string.field_title),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitRename() }),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = submitRename) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            } else {
                SheetRow(
                    Icons.Filled.Search,
                    stringResource(R.string.action_open),
                    onClick = { dismissThen(onOpen) },
                )
                SheetRow(
                    Icons.Filled.Edit,
                    stringResource(R.string.action_rename),
                    onClick = { renaming = true },
                )
                SheetRow(
                    Icons.Filled.Delete,
                    stringResource(R.string.action_delete),
                    onClick = { dismissThen(onDelete) },
                )
            }
        }
    }
}
