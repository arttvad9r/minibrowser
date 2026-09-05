package com.artt.minibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import com.artt.minibrowser.browser.SitePermissionGroup
import com.artt.minibrowser.browser.SitePermissionItem
import com.artt.minibrowser.browser.SitePermissionKind
import com.artt.minibrowser.browser.SiteSettingsController
import com.artt.minibrowser.engine.BrowserApp
import kotlinx.coroutines.launch

@Composable
internal fun SiteSettingsSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as BrowserApp
    val controller = remember(app) { SiteSettingsController(app.runtime.storageController) }
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<SitePermissionGroup>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var clearHost by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        controller.load().fold(
            onSuccess = {
                groups = it
                loadFailed = false
            },
            onFailure = {
                groups = emptyList()
                loadFailed = true
            },
        )
    }

    BrowserBottomSheet(onDismissRequest = onDismiss) { _ ->
        Text(stringResource(R.string.site_settings_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        when {
            loadFailed -> Text(
                stringResource(R.string.site_settings_load_failed),
                color = MaterialTheme.colorScheme.error,
            )
            groups == null -> Text(
                "…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            groups!!.isEmpty() -> Text(
                stringResource(R.string.site_settings_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> groups!!.forEach { group ->
                SitePermissionGroupContent(
                    group = group,
                    onReset = { item ->
                        controller.resetPermission(item)
                        reloadKey++
                    },
                    onClearData = { clearHost = group.host },
                )
            }
        }
    }

    clearHost?.let { host ->
        AlertDialog(
            onDismissRequest = { clearHost = null },
            title = { Text(stringResource(R.string.site_settings_clear_data)) },
            text = { Text(stringResource(R.string.site_settings_clear_data_confirm, host)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearHost = null
                        scope.launch {
                            controller.clearSiteData(host)
                            reloadKey++
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearHost = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SitePermissionGroupContent(
    group: SitePermissionGroup,
    onReset: (SitePermissionItem) -> Unit,
    onClearData: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(group.host, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        group.permissions.forEach { item ->
            SheetRow(
                icon = AppIcons.Shield,
                label = permissionLabel(item.kind),
                trailing = {
                    Row {
                        Text(
                            stringResource(
                                if (item.allowed) R.string.site_permission_allowed else R.string.site_permission_denied,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onReset(item) }) {
                            Text(stringResource(R.string.site_settings_reset_permission))
                        }
                    }
                },
            )
        }
        TextButton(onClick = onClearData) {
            Text(stringResource(R.string.site_settings_clear_data))
        }
    }
}

@Composable
private fun permissionLabel(kind: SitePermissionKind): String = when (kind) {
    SitePermissionKind.Geolocation -> stringResource(R.string.site_permission_geolocation)
    SitePermissionKind.Notifications -> stringResource(R.string.site_permission_notifications)
    SitePermissionKind.PersistentStorage -> stringResource(R.string.site_permission_persistent_storage)
    SitePermissionKind.Xr -> stringResource(R.string.site_permission_xr)
    SitePermissionKind.Autoplay -> stringResource(R.string.site_permission_autoplay)
    SitePermissionKind.Drm -> stringResource(R.string.site_permission_drm)
    SitePermissionKind.Tracking -> stringResource(R.string.site_permission_tracking)
    SitePermissionKind.StorageAccess -> stringResource(R.string.site_permission_storage_access)
    SitePermissionKind.LocalDevice -> stringResource(R.string.site_permission_local_device)
    SitePermissionKind.LocalNetwork -> stringResource(R.string.site_permission_local_network)
    SitePermissionKind.Other -> stringResource(R.string.site_permission_other)
}
