/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package org.kde.kdeconnect.tvremote

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect_tp.R

@Composable
fun TvRemoteScreen(
    state: RemoteUiState,
    actions: RemoteActions,
    shortcuts: List<Shortcut>,
    onShortcutsChange: (List<Shortcut>) -> Unit,
    tab: RemoteTab,
    onTabChange: (RemoteTab) -> Unit,
    volumeKeysControlPc: Boolean,
    onVolumeKeysControlPcChange: (Boolean) -> Unit,
    onOpenKdeConnect: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingShortcuts by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    // Shortcut being edited, null to create a new one
    var editorInitial by remember { mutableStateOf<Shortcut?>(null) }
    var confirmTarget by remember { mutableStateOf<Shortcut?>(null) }

    fun run(shortcut: Shortcut) {
        val result = actions.runShortcut(shortcut)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            when (result) {
                ShortcutResult.SENT -> snackbarHostState.showSnackbar(context.getString(R.string.tvremote_sent, shortcut.label))
                ShortcutResult.OPENED_FALLBACK_URL ->
                    snackbarHostState.showSnackbar(context.getString(R.string.tvremote_opened_fallback, shortcut.target))
                ShortcutResult.UNAVAILABLE -> snackbarHostState.showSnackbar(context.getString(R.string.tvremote_unavailable))
                ShortcutResult.MISSING_COMMAND -> {
                    val choice = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.tvremote_missing_command, shortcut.target),
                        actionLabel = context.getString(R.string.tvremote_setup_command),
                        duration = SnackbarDuration.Long,
                    )
                    if (choice == SnackbarResult.ActionPerformed) actions.openCommandSetup()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            RemoteTopBar(
                state = state,
                actions = actions,
                volumeKeysControlPc = volumeKeysControlPc,
                onVolumeKeysControlPcChange = onVolumeKeysControlPcChange,
                onOpenKdeConnect = onOpenKdeConnect,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val device = state.device
        if (device == null) {
            NoDevice(onOpenKdeConnect, Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                // Keeps the typing field of the touchpad tab above the on-screen keyboard
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!device.reachable) OfflineBanner()
                TabSwitcher(tab, onTabChange)
            }
            val tabModifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f)
            when (tab) {
                RemoteTab.MEDIA -> Column(
                    tabModifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NowPlayingCard(state.media, available = state.hasMediaPlugin, actions = actions)
                    VolumeCard(state.volume, actions)
                    KeysCard(enabled = state.hasInput, actions = actions)
                    ShortcutsSection(
                        shortcuts = shortcuts,
                        editing = editingShortcuts,
                        onEditingChange = { editingShortcuts = it },
                        onRun = { if (it.confirm) confirmTarget = it else run(it) },
                        onEdit = {
                            editorInitial = it
                            editorOpen = true
                        },
                    )
                }
                RemoteTab.TOUCHPAD -> TouchpadTab(enabled = state.hasInput, actions = actions, modifier = tabModifier)
            }
        }
    }

    confirmTarget?.let { shortcut ->
        ConfirmShortcutDialog(
            shortcut = shortcut,
            deviceName = state.device?.name.orEmpty(),
            onConfirm = {
                confirmTarget = null
                run(shortcut)
            },
            onDismiss = { confirmTarget = null },
        )
    }
    if (editorOpen) {
        val initial = editorInitial
        ShortcutEditorDialog(
            initial = initial,
            commandNames = state.commandNames,
            onSave = { saved ->
                val index = shortcuts.indexOfFirst { it.id == saved.id }
                onShortcutsChange(if (index >= 0) shortcuts.toMutableList().apply { set(index, saved) } else shortcuts + saved)
                editorOpen = false
            },
            onDelete = initial?.let { toDelete ->
                {
                    onShortcutsChange(shortcuts.filterNot { it.id == toDelete.id })
                    editorOpen = false
                }
            },
            onDismiss = { editorOpen = false },
        )
    }
}

@Composable
private fun RemoteTopBar(
    state: RemoteUiState,
    actions: RemoteActions,
    volumeKeysControlPc: Boolean,
    onVolumeKeysControlPcChange: (Boolean) -> Unit,
    onOpenKdeConnect: () -> Unit,
) {
    var devicesExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val device = state.device
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        title = {
            if (device == null) {
                Text(stringResource(R.string.tvremote_app_name))
                return@TopAppBar
            }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.tvremote_ic_computer), contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(if (device.reachable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(if (device.reachable) R.string.tvremote_connected else R.string.tvremote_offline),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (state.devices.size > 1) {
                        IconButton(onClick = { devicesExpanded = true }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.tvremote_choose_device))
                        }
                    }
                }
                DropdownMenu(expanded = devicesExpanded, onDismissRequest = { devicesExpanded = false }) {
                    state.devices.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.name) },
                            leadingIcon = { StatusDot(if (entry.reachable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline) },
                            trailingIcon = { if (entry.id == device.id) Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = {
                                devicesExpanded = false
                                actions.selectDevice(entry.id)
                            },
                        )
                    }
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.tvremote_more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tvremote_volume_keys)) },
                        trailingIcon = { if (volumeKeysControlPc) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = { onVolumeKeysControlPcChange(!volumeKeysControlPc) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tvremote_open_kdeconnect)) },
                        onClick = {
                            menuExpanded = false
                            onOpenKdeConnect()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun TabSwitcher(tab: RemoteTab, onTabChange: (RemoteTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        RemoteTab.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = tab == entry,
                onClick = { onTabChange(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, RemoteTab.entries.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = tab == entry) {
                        Icon(
                            painterResource(if (entry == RemoteTab.MEDIA) R.drawable.tvremote_ic_music else R.drawable.tvremote_ic_mouse),
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
                label = { Text(stringResource(if (entry == RemoteTab.MEDIA) R.string.tvremote_tab_media else R.string.tvremote_tab_touchpad)) },
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Surface(Modifier.size(8.dp), shape = CircleShape, color = color) {}
}

@Composable
private fun OfflineBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.tvremote_offline), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoDevice(onOpenKdeConnect: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.tvremote_ic_tv),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.tvremote_no_device_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tvremote_no_device_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenKdeConnect) { Text(stringResource(R.string.tvremote_open_kdeconnect)) }
    }
}

/** Rounded container used by every section of the remote. */
@Composable
fun RemoteCard(modifier: Modifier = Modifier, contentPadding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(PaddingValues(contentPadding)), content = content)
    }
}

private val previewState = RemoteUiState(
    devices = listOf(DeviceEntry("1", "Living room PC", true), DeviceEntry("2", "Laptop", false)),
    device = DeviceEntry("1", "Living room PC", true),
    hasInput = true,
    hasMediaPlugin = true,
    media = MediaUiState(
        players = listOf("Firefox", "VLC"),
        player = "Firefox",
        title = "Big Buck Bunny",
        artist = "Blender Foundation",
        isPlaying = true,
        canSeek = true,
        canPrevious = true,
        canNext = true,
        positionMs = 135_000,
        lengthMs = 596_000,
        positionSampledAt = SystemClock.elapsedRealtime(),
        albumArt = null,
    ),
    volume = VolumeUiState(volume = 40, max = 100, muted = false, canMute = true),
    commandNames = listOf("Netflix", "Jellyfin", "Shut down"),
    canRunCommands = true,
    canOpenUrls = true,
)

@Preview(name = "Light", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TvRemoteScreenPreview() {
    var tab by remember { mutableStateOf(RemoteTab.MEDIA) }
    KdeTheme(LocalContext.current) {
        TvRemoteScreen(
            state = previewState,
            actions = object : RemoteActions {},
            shortcuts = ShortcutStore.defaultShortcuts,
            onShortcutsChange = {},
            tab = tab,
            onTabChange = { tab = it },
            volumeKeysControlPc = true,
            onVolumeKeysControlPcChange = {},
            onOpenKdeConnect = {},
        )
    }
}

@Preview(name = "Touchpad", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TvRemoteTouchpadPreview() {
    KdeTheme(LocalContext.current) {
        TvRemoteScreen(
            state = previewState.copy(device = DeviceEntry("1", "Living room PC", false)),
            actions = object : RemoteActions {},
            shortcuts = ShortcutStore.defaultShortcuts,
            onShortcutsChange = {},
            tab = RemoteTab.TOUCHPAD,
            onTabChange = {},
            volumeKeysControlPc = true,
            onVolumeKeysControlPcChange = {},
            onOpenKdeConnect = {},
        )
    }
}

@Preview(name = "No device", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TvRemoteNoDevicePreview() {
    KdeTheme(LocalContext.current) {
        TvRemoteScreen(
            state = RemoteUiState(),
            actions = object : RemoteActions {},
            shortcuts = emptyList(),
            onShortcutsChange = {},
            tab = RemoteTab.MEDIA,
            onTabChange = {},
            volumeKeysControlPc = true,
            onVolumeKeysControlPcChange = {},
            onOpenKdeConnect = {},
        )
    }
}
