/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.kde.kdeconnect.tvremote

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect_tp.R

@Composable
fun ShortcutsSection(
    shortcuts: List<Shortcut>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onRun: (Shortcut) -> Unit,
    onEdit: (Shortcut?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tvremote_shortcuts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            TextButton(onClick = { onEditingChange(!editing) }) {
                Text(stringResource(if (editing) R.string.tvremote_done_editing else R.string.tvremote_edit_shortcuts))
            }
        }
        val tiles: List<Shortcut?> = if (editing || shortcuts.isEmpty()) shortcuts + null else shortcuts
        tiles.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { shortcut ->
                    val tileModifier = Modifier.weight(1f)
                    if (shortcut == null) {
                        AddTile(onClick = { onEdit(null) }, modifier = tileModifier)
                    } else {
                        ShortcutTile(
                            shortcut = shortcut,
                            editing = editing,
                            onClick = { if (editing) onEdit(shortcut) else onRun(shortcut) },
                            onLongClick = { onEdit(shortcut) },
                            modifier = tileModifier,
                        )
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShortcutTile(shortcut: Shortcut, editing: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val view = LocalView.current
    val destructive = shortcut.icon == ShortcutIcon.POWER
    Surface(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                },
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(8.dp).align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(shortcut.icon.drawable), contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    shortcut.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (editing) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.tvremote_edit_shortcut),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tvremote_add_shortcut), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ConfirmShortcutDialog(shortcut: Shortcut, deviceName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(shortcut.icon.drawable), contentDescription = null) },
        title = { Text(stringResource(R.string.tvremote_confirm_run, shortcut.label, deviceName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.tvremote_run)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tvremote_cancel)) } },
    )
}

/** Edits [initial], or creates a new shortcut when it is null. */
@Composable
fun ShortcutEditorDialog(
    initial: Shortcut?,
    commandNames: List<String>,
    onSave: (Shortcut) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.icon ?: ShortcutIcon.STAR) }
    var action by remember { mutableStateOf(initial?.action ?: ShortcutAction.OPEN_URL) }
    var target by remember { mutableStateOf(initial?.target.orEmpty()) }
    var fallbackUrl by remember { mutableStateOf(initial?.fallbackUrl.orEmpty()) }
    var confirm by remember { mutableStateOf(initial?.confirm ?: false) }
    val canSave = label.isNotBlank() && target.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.tvremote_new_shortcut else R.string.tvremote_edit_shortcut)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.tvremote_shortcut_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.tvremote_shortcut_icon), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShortcutIcon.entries.forEach { entry ->
                        FilledIconToggleButton(checked = icon == entry, onCheckedChange = { icon = entry }) {
                            Icon(painterResource(entry.drawable), contentDescription = entry.name.lowercase())
                        }
                    }
                }

                Text(stringResource(R.string.tvremote_shortcut_action), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ShortcutAction.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = action == entry,
                            onClick = { action = entry },
                            shape = SegmentedButtonDefaults.itemShape(index, ShortcutAction.entries.size),
                            label = {
                                Text(stringResource(if (entry == ShortcutAction.OPEN_URL) R.string.tvremote_action_open_url else R.string.tvremote_action_run_command))
                            },
                        )
                    }
                }

                when (action) {
                    ShortcutAction.OPEN_URL -> {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            label = { Text(stringResource(R.string.tvremote_shortcut_url)) },
                            supportingText = { Text(stringResource(R.string.tvremote_shortcut_url_help)) },
                            placeholder = { Text("https://www.netflix.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ShortcutAction.RUN_COMMAND -> {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            label = { Text(stringResource(R.string.tvremote_shortcut_command)) },
                            supportingText = { Text(stringResource(R.string.tvremote_shortcut_command_help)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (commandNames.isNotEmpty()) {
                            Text(stringResource(R.string.tvremote_shortcut_pc_commands), style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                commandNames.forEach { name ->
                                    SuggestionChip(onClick = {
                                        target = name
                                        if (label.isBlank()) label = name
                                    }, label = { Text(name) })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = fallbackUrl,
                            onValueChange = { fallbackUrl = it },
                            label = { Text(stringResource(R.string.tvremote_shortcut_fallback_url)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tvremote_shortcut_confirm), modifier = Modifier.weight(1f))
                    Switch(checked = confirm, onCheckedChange = { confirm = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                val base = initial ?: Shortcut(label = label, action = action, target = target)
                onSave(
                    base.copy(
                        label = label.trim(),
                        icon = icon,
                        action = action,
                        target = target.trim(),
                        fallbackUrl = if (action == ShortcutAction.RUN_COMMAND) fallbackUrl.trim() else "",
                        confirm = confirm,
                    )
                )
            }) { Text(stringResource(R.string.tvremote_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.tvremote_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tvremote_cancel)) }
            }
        },
    )
}

private const val COLUMNS = 3
