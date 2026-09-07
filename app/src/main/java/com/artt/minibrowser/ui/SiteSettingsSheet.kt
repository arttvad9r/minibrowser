package com.artt.minibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.R
import com.artt.minibrowser.browser.SitePermissionGroup
import com.artt.minibrowser.browser.SitePermissionItem
import com.artt.minibrowser.browser.SitePermissionKind
import com.artt.minibrowser.browser.SiteSettingsController
import com.artt.minibrowser.data.SitePermissionPolicy
import com.artt.minibrowser.data.SitePermissionPolicyRepository
import com.artt.minibrowser.data.SitePermissionPolicyType
import com.artt.minibrowser.engine.BrowserApp
import kotlinx.coroutines.launch

private enum class SiteSettingsCategory {
    Geolocation,
    Camera,
    Microphone,
    Notifications,
    Drm,
    StorageAccess,
    Autoplay,
    PersistentStorage,
    Xr,
    LocalAccess,
}

private sealed interface SiteSettingsPage {
    data object Root : SiteSettingsPage
    data object AllSites : SiteSettingsPage
    data class Category(val category: SiteSettingsCategory) : SiteSettingsPage
    data class Host(val host: String, val returnTo: SiteSettingsPage) : SiteSettingsPage
}

/** Full-screen site settings: browser defaults first, per-site exceptions second. */
@Composable
internal fun SiteSettingsSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as BrowserApp
    val controller = remember(app) { SiteSettingsController(app.runtime.storageController) }
    val policyRepository = remember(app) { SitePermissionPolicyRepository(app) }
    val policy by policyRepository.policy.collectAsState(initial = SitePermissionPolicy())
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<SitePermissionGroup>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf<SiteSettingsPage>(SiteSettingsPage.Root) }
    var clearRequest by remember { mutableStateOf<Pair<String, SiteSettingsPage>?>(null) }

    fun navigateBack() {
        page = when (val current = page) {
            SiteSettingsPage.Root -> {
                onDismiss()
                SiteSettingsPage.Root
            }
            SiteSettingsPage.AllSites -> SiteSettingsPage.Root
            is SiteSettingsPage.Category -> SiteSettingsPage.Root
            is SiteSettingsPage.Host -> current.returnTo
        }
    }

    BackHandler(onBack = ::navigateBack)

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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        CenteredSinglePane(maxWidth = 720.dp) {
            Column(Modifier.fillMaxSize()) {
                SiteSettingsToolbar(
                    title = when (val current = page) {
                        SiteSettingsPage.Root -> stringResource(R.string.site_settings_title)
                        SiteSettingsPage.AllSites -> stringResource(R.string.site_settings_all_sites)
                        is SiteSettingsPage.Category -> categoryTitle(current.category)
                        is SiteSettingsPage.Host -> current.host
                    },
                    onBack = ::navigateBack,
                )

                when (val current = page) {
                    SiteSettingsPage.Root -> SiteSettingsRoot(
                        groups = groups,
                        policy = policy,
                        loadFailed = loadFailed,
                        onRetry = { reloadKey++ },
                        onAllSites = { page = SiteSettingsPage.AllSites },
                        onCategory = { page = SiteSettingsPage.Category(it) },
                    )
                    SiteSettingsPage.AllSites -> SiteSettingsAllSites(
                        groups = groups,
                        loadFailed = loadFailed,
                        onRetry = { reloadKey++ },
                        onHost = { page = SiteSettingsPage.Host(it, SiteSettingsPage.AllSites) },
                    )
                    is SiteSettingsPage.Category -> SiteSettingsCategoryContent(
                        category = current.category,
                        groups = groups,
                        policy = policy,
                        loadFailed = loadFailed,
                        onRetry = { reloadKey++ },
                        onPolicyChange = { enabled ->
                            scope.launch {
                                policyRepository.set(current.category.policyType(), enabled)
                            }
                        },
                        onHost = { page = SiteSettingsPage.Host(it, current) },
                    )
                    is SiteSettingsPage.Host -> SiteSettingsHost(
                        group = groups?.firstOrNull { it.host == current.host },
                        onReset = { item ->
                            controller.resetPermission(item)
                            reloadKey++
                        },
                        onClearData = { clearRequest = current.host to current.returnTo },
                    )
                }
            }
        }
    }

    clearRequest?.let { request ->
        val host = request.first
        val returnTo = request.second
        AlertDialog(
            onDismissRequest = { clearRequest = null },
            title = { Text(stringResource(R.string.site_settings_clear_data)) },
            text = { Text(stringResource(R.string.site_settings_clear_data_confirm, host)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearRequest = null
                        scope.launch {
                            controller.clearSiteData(host)
                            reloadKey++
                            page = returnTo
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearRequest = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SiteSettingsToolbar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        Text(
            title,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun SiteSettingsRoot(
    groups: List<SitePermissionGroup>?,
    policy: SitePermissionPolicy,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onAllSites: () -> Unit,
    onCategory: (SiteSettingsCategory) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SettingsGroup {
            SiteSettingRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.site_settings_all_sites),
                subtitle = when {
                    loadFailed -> stringResource(R.string.site_settings_load_failed)
                    groups == null -> stringResource(R.string.site_settings_loading)
                    else -> pluralStringResource(
                        R.plurals.site_settings_saved_sites_count,
                        groups.size,
                        groups.size,
                    )
                },
                onClick = onAllSites,
            )
        }

        SiteSettingsSectionLabel(stringResource(R.string.site_settings_permissions_section))
        SettingsGroup {
            SiteSettingRow(
                Icons.Filled.LocationOn,
                stringResource(R.string.site_settings_category_geolocation),
                categoryDefaultSubtitle(SiteSettingsCategory.Geolocation, policy),
                onClick = { onCategory(SiteSettingsCategory.Geolocation) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                Icons.Filled.Videocam,
                stringResource(R.string.site_settings_category_camera),
                categoryDefaultSubtitle(SiteSettingsCategory.Camera, policy),
                onClick = { onCategory(SiteSettingsCategory.Camera) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                Icons.Filled.Mic,
                stringResource(R.string.site_settings_category_microphone),
                categoryDefaultSubtitle(SiteSettingsCategory.Microphone, policy),
                onClick = { onCategory(SiteSettingsCategory.Microphone) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                Icons.Filled.Notifications,
                stringResource(R.string.site_settings_category_notifications),
                categoryDefaultSubtitle(SiteSettingsCategory.Notifications, policy),
                onClick = { onCategory(SiteSettingsCategory.Notifications) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                Icons.Filled.Lock,
                stringResource(R.string.site_settings_category_drm),
                categoryDefaultSubtitle(SiteSettingsCategory.Drm, policy),
                onClick = { onCategory(SiteSettingsCategory.Drm) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                Icons.Filled.Storage,
                stringResource(R.string.site_settings_category_storage_access),
                categoryDefaultSubtitle(SiteSettingsCategory.StorageAccess, policy),
                onClick = { onCategory(SiteSettingsCategory.StorageAccess) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                AppIcons.Globe,
                stringResource(R.string.site_settings_category_xr),
                categoryDefaultSubtitle(SiteSettingsCategory.Xr, policy),
                onClick = { onCategory(SiteSettingsCategory.Xr) },
            )
        }

        SiteSettingsSectionLabel(stringResource(R.string.site_settings_content_section))
        SettingsGroup {
            SiteSettingRow(
                Icons.Filled.PlayArrow,
                stringResource(R.string.site_settings_category_autoplay),
                categoryDefaultSubtitle(SiteSettingsCategory.Autoplay, policy),
                onClick = { onCategory(SiteSettingsCategory.Autoplay) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                Icons.Filled.Storage,
                stringResource(R.string.site_settings_category_persistent_storage),
                categoryDefaultSubtitle(SiteSettingsCategory.PersistentStorage, policy),
                onClick = { onCategory(SiteSettingsCategory.PersistentStorage) },
            )
            SiteSettingsDivider()
            SiteSettingRow(
                Icons.Filled.Devices,
                stringResource(R.string.site_settings_category_local_access),
                categoryDefaultSubtitle(SiteSettingsCategory.LocalAccess, policy),
                onClick = { onCategory(SiteSettingsCategory.LocalAccess) },
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.site_settings_global_policy_note),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loadFailed) {
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun SiteSettingsCategoryContent(
    category: SiteSettingsCategory,
    groups: List<SitePermissionGroup>?,
    policy: SitePermissionPolicy,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onPolicyChange: (Boolean) -> Unit,
    onHost: (String) -> Unit,
) {
    val matchingGroups = groups?.mapNotNull { group ->
        val permissions = group.permissions.filter { category.matches(it.kind) }
        if (permissions.isEmpty()) null else SitePermissionGroup(group.host, permissions)
    }.orEmpty()
    val enabled = policy.enabled(category.policyType())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.site_settings_default_behavior_title),
            Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsGroup {
            SitePolicyChoiceRow(
                selected = enabled,
                text = stringResource(
                    if (category == SiteSettingsCategory.Autoplay) {
                        R.string.site_settings_allow_autoplay_audible
                    } else {
                        R.string.site_settings_allow_requests
                    },
                ),
                onClick = { onPolicyChange(true) },
            )
            SiteSettingsDivider()
            SitePolicyChoiceRow(
                selected = !enabled,
                text = stringResource(
                    if (category == SiteSettingsCategory.Autoplay) {
                        R.string.site_settings_block_autoplay_audible
                    } else {
                        R.string.site_settings_block_requests
                    },
                ),
                onClick = { onPolicyChange(false) },
            )
        }

        if (category.isSessionMediaPermission()) {
            Text(
                stringResource(R.string.site_settings_media_permissions_note),
                Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        SiteSettingsSectionLabel(stringResource(R.string.site_settings_category_exceptions_note))

        when {
            groups == null -> Box(
                Modifier.fillMaxWidth().heightIn(min = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            loadFailed -> {
                Text(
                    stringResource(R.string.site_settings_load_failed),
                    Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            matchingGroups.isEmpty() -> Text(
                stringResource(R.string.site_settings_category_exceptions_empty),
                Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> SettingsGroup {
                matchingGroups.forEachIndexed { index, group ->
                    SiteSettingRow(
                        icon = categoryIcon(category),
                        title = group.host,
                        subtitle = pluralStringResource(
                            R.plurals.site_settings_saved_setting_count,
                            group.permissions.size,
                            group.permissions.size,
                        ),
                        onClick = { onHost(group.host) },
                    )
                    if (index != matchingGroups.lastIndex) SiteSettingsDivider()
                }
            }
        }
    }
}

@Composable
private fun SitePolicyChoiceRow(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SiteSettingsAllSites(
    groups: List<SitePermissionGroup>?,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onHost: (String) -> Unit,
) {
    when {
        groups == null -> LoadingContent()
        loadFailed -> MessageContent(
            message = stringResource(R.string.site_settings_load_failed),
            action = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
        groups.isEmpty() -> MessageContent(stringResource(R.string.site_settings_all_sites_empty))
        else -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            SettingsGroup {
                groups.forEachIndexed { index, group ->
                    SiteSettingRow(
                        icon = AppIcons.Globe,
                        title = group.host,
                        subtitle = pluralStringResource(
                            R.plurals.site_settings_saved_setting_count,
                            group.visibleSettingCount(),
                            group.visibleSettingCount(),
                        ),
                        onClick = { onHost(group.host) },
                    )
                    if (index != groups.lastIndex) SiteSettingsDivider()
                }
            }
        }
    }
}

@Composable
private fun SiteSettingsHost(
    group: SitePermissionGroup?,
    onReset: (SitePermissionItem) -> Unit,
    onClearData: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.site_settings_host_description),
            Modifier.padding(start = 4.dp, bottom = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group == null || group.permissions.isEmpty()) {
            Text(
                stringResource(R.string.site_settings_no_saved_settings),
                Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SettingsGroup {
                group.permissions.forEachIndexed { index, item ->
                    SitePermissionRow(item, onReset)
                    if (index != group.permissions.lastIndex) SiteSettingsDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onClearData, modifier = Modifier.align(Alignment.End)) {
            Text(
                stringResource(R.string.site_settings_clear_data),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SitePermissionRow(item: SitePermissionItem, onReset: (SitePermissionItem) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            permissionIcon(item.kind),
            null,
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(permissionLabel(item.kind), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (item.allowed) R.string.site_permission_allowed else R.string.site_permission_denied,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onReset(item) }) {
            Text(stringResource(R.string.site_settings_reset_permission))
        }
    }
}

@Composable
private fun SiteSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    val interaction = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        interaction
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                AppIcons.ChevronRight,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SiteSettingsSectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SiteSettingsDivider() {
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageContent(
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

private fun SiteSettingsCategory.matches(kind: SitePermissionKind): Boolean = when (this) {
    SiteSettingsCategory.Geolocation -> kind == SitePermissionKind.Geolocation
    SiteSettingsCategory.Camera,
    SiteSettingsCategory.Microphone,
    -> false
    SiteSettingsCategory.Notifications -> kind == SitePermissionKind.Notifications
    SiteSettingsCategory.Drm -> kind == SitePermissionKind.Drm
    SiteSettingsCategory.StorageAccess -> kind == SitePermissionKind.StorageAccess
    SiteSettingsCategory.Autoplay -> kind == SitePermissionKind.AutoplayAudible ||
        kind == SitePermissionKind.AutoplayInaudible
    SiteSettingsCategory.PersistentStorage -> kind == SitePermissionKind.PersistentStorage
    SiteSettingsCategory.Xr -> kind == SitePermissionKind.Xr
    SiteSettingsCategory.LocalAccess -> kind == SitePermissionKind.LocalDevice ||
        kind == SitePermissionKind.LocalNetwork
}

private fun SiteSettingsCategory.isSessionMediaPermission(): Boolean =
    this == SiteSettingsCategory.Camera || this == SiteSettingsCategory.Microphone

private fun SiteSettingsCategory.policyType(): SitePermissionPolicyType = when (this) {
    SiteSettingsCategory.Geolocation -> SitePermissionPolicyType.GEOLOCATION
    SiteSettingsCategory.Camera -> SitePermissionPolicyType.CAMERA
    SiteSettingsCategory.Microphone -> SitePermissionPolicyType.MICROPHONE
    SiteSettingsCategory.Notifications -> SitePermissionPolicyType.NOTIFICATIONS
    SiteSettingsCategory.Drm -> SitePermissionPolicyType.DRM
    SiteSettingsCategory.StorageAccess -> SitePermissionPolicyType.STORAGE_ACCESS
    SiteSettingsCategory.Autoplay -> SitePermissionPolicyType.AUTOPLAY_AUDIBLE
    SiteSettingsCategory.PersistentStorage -> SitePermissionPolicyType.PERSISTENT_STORAGE
    SiteSettingsCategory.Xr -> SitePermissionPolicyType.XR
    SiteSettingsCategory.LocalAccess -> SitePermissionPolicyType.LOCAL_ACCESS
}

@Composable
private fun categoryTitle(category: SiteSettingsCategory): String = stringResource(
    when (category) {
        SiteSettingsCategory.Geolocation -> R.string.site_settings_category_geolocation
        SiteSettingsCategory.Camera -> R.string.site_settings_category_camera
        SiteSettingsCategory.Microphone -> R.string.site_settings_category_microphone
        SiteSettingsCategory.Notifications -> R.string.site_settings_category_notifications
        SiteSettingsCategory.Drm -> R.string.site_settings_category_drm
        SiteSettingsCategory.StorageAccess -> R.string.site_settings_category_storage_access
        SiteSettingsCategory.Autoplay -> R.string.site_settings_category_autoplay
        SiteSettingsCategory.PersistentStorage -> R.string.site_settings_category_persistent_storage
        SiteSettingsCategory.Xr -> R.string.site_settings_category_xr
        SiteSettingsCategory.LocalAccess -> R.string.site_settings_category_local_access
    },
)

@Composable
private fun categoryDefaultSubtitle(
    category: SiteSettingsCategory,
    policy: SitePermissionPolicy,
): String {
    if (category == SiteSettingsCategory.Autoplay) {
        return stringResource(
            if (policy.autoplayAudibleAllowed) {
                R.string.site_settings_autoplay_policy_allowed
            } else {
                R.string.site_settings_autoplay_policy
            },
        )
    }
    return stringResource(
        if (policy.enabled(category.policyType())) {
            if (category.isSessionMediaPermission()) {
                R.string.site_settings_media_prompt_each_time
            } else {
                R.string.site_settings_default_ask
            }
        } else {
            R.string.site_settings_default_blocked
        },
    )
}

private fun categoryIcon(category: SiteSettingsCategory): ImageVector = when (category) {
    SiteSettingsCategory.Geolocation -> Icons.Filled.LocationOn
    SiteSettingsCategory.Camera -> Icons.Filled.Videocam
    SiteSettingsCategory.Microphone -> Icons.Filled.Mic
    SiteSettingsCategory.Notifications -> Icons.Filled.Notifications
    SiteSettingsCategory.Drm -> Icons.Filled.Lock
    SiteSettingsCategory.StorageAccess -> Icons.Filled.Storage
    SiteSettingsCategory.Autoplay -> Icons.Filled.PlayArrow
    SiteSettingsCategory.PersistentStorage -> Icons.Filled.Storage
    SiteSettingsCategory.Xr -> AppIcons.Globe
    SiteSettingsCategory.LocalAccess -> Icons.Filled.Devices
}

private fun SitePermissionGroup.visibleSettingCount(): Int = permissions
    .map { item ->
        when (item.kind) {
            SitePermissionKind.AutoplayAudible,
            SitePermissionKind.AutoplayInaudible,
            -> "autoplay"
            else -> item.kind.name
        }
    }
    .distinct()
    .size

@Composable
private fun permissionLabel(kind: SitePermissionKind): String = when (kind) {
    SitePermissionKind.Geolocation -> stringResource(R.string.site_permission_geolocation)
    SitePermissionKind.Notifications -> stringResource(R.string.site_permission_notifications)
    SitePermissionKind.PersistentStorage -> stringResource(R.string.site_permission_persistent_storage)
    SitePermissionKind.Xr -> stringResource(R.string.site_permission_xr)
    SitePermissionKind.AutoplayAudible -> stringResource(R.string.site_permission_autoplay_audible)
    SitePermissionKind.AutoplayInaudible -> stringResource(R.string.site_permission_autoplay_inaudible)
    SitePermissionKind.Drm -> stringResource(R.string.site_permission_drm)
    SitePermissionKind.Tracking -> stringResource(R.string.site_permission_tracking)
    SitePermissionKind.StorageAccess -> stringResource(R.string.site_permission_storage_access)
    SitePermissionKind.LocalDevice -> stringResource(R.string.site_permission_local_device)
    SitePermissionKind.LocalNetwork -> stringResource(R.string.site_permission_local_network)
    SitePermissionKind.Other -> stringResource(R.string.site_permission_other)
}

private fun permissionIcon(kind: SitePermissionKind): ImageVector = when (kind) {
    SitePermissionKind.Geolocation -> Icons.Filled.LocationOn
    SitePermissionKind.Notifications -> Icons.Filled.Notifications
    SitePermissionKind.PersistentStorage -> Icons.Filled.Storage
    SitePermissionKind.Xr -> AppIcons.Globe
    SitePermissionKind.AutoplayAudible,
    SitePermissionKind.AutoplayInaudible,
    -> Icons.Filled.PlayArrow
    SitePermissionKind.Drm -> Icons.Filled.Lock
    SitePermissionKind.Tracking -> AppIcons.Shield
    SitePermissionKind.StorageAccess -> Icons.Filled.Storage
    SitePermissionKind.LocalDevice,
    SitePermissionKind.LocalNetwork,
    -> Icons.Filled.Devices
    SitePermissionKind.Other -> AppIcons.Shield
}
