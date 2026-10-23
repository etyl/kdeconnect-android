/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.tvremote

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect_tp.R
import kotlin.math.abs

/** Mouse and keyboard: the touchpad takes all the space left by the buttons and the typing field. */
@Composable
fun TouchpadTab(enabled: Boolean, actions: RemoteActions, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!enabled) {
            Text(
                stringResource(R.string.tvremote_input_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            return@Column
        }
        Touchpad(actions, Modifier.fillMaxWidth().weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { actions.click(MouseButton.LEFT) }, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.tvremote_left_click))
            }
            OutlinedButton(onClick = { actions.click(MouseButton.RIGHT) }, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.tvremote_right_click))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyButton(R.drawable.tvremote_ic_copy, R.string.tvremote_copy, Modifier.weight(1f)) { actions.key(RemoteKey.COPY) }
            KeyButton(R.drawable.tvremote_ic_paste, R.string.tvremote_paste, Modifier.weight(1f)) { actions.key(RemoteKey.PASTE) }
        }
        KeyboardRow(actions)
    }
}

@Composable
private fun KeyButton(icon: Int, label: Int, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    FilledTonalButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        modifier = modifier.height(52.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}

@Composable
private fun Touchpad(actions: RemoteActions, modifier: Modifier = Modifier) {
    val currentActions by rememberUpdatedState(actions)
    val view = LocalView.current
    // Same normalization as the regular touchpad screen, so the pointer speed doesn't depend on the screen density
    val pointerScale = remember(view) { STANDARD_DPI / view.resources.displayMetrics.xdpi * POINTER_SENSITIVITY }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            Modifier.fillMaxSize().pointerInput(pointerScale) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    var maxPointers = 1
                    var travelled = 0f
                    var moved = false
                    var scrollAccumulator = Offset.Zero
                    var lastTime = downTime
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        event.changes.firstOrNull()?.let { lastTime = it.uptimeMillis }
                        if (pressedChanges.isEmpty()) break
                        maxPointers = maxOf(maxPointers, pressedChanges.size)
                        val delta = pressedChanges
                            .map { it.positionChange() }
                            .fold(Offset.Zero) { acc, offset -> acc + offset } / pressedChanges.size.toFloat()
                        travelled += delta.getDistance()
                        if (travelled > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            if (maxPointers == 1) {
                                currentActions.mouseMove(delta.x * pointerScale, delta.y * pointerScale)
                            } else {
                                scrollAccumulator += delta * SCROLL_SENSITIVITY
                                if (abs(scrollAccumulator.x) > MIN_SCROLL || abs(scrollAccumulator.y) > MIN_SCROLL) {
                                    // Finger moving up scrolls down, like on the regular touchpad screen
                                    currentActions.scroll(scrollAccumulator.x.toDouble(), -scrollAccumulator.y.toDouble())
                                    scrollAccumulator = Offset.Zero
                                }
                            }
                        }
                        pressedChanges.forEach { it.consume() }
                    }
                    if (!moved && lastTime - downTime < TAP_TIMEOUT_MS) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        currentActions.click(if (maxPointers >= 2) MouseButton.RIGHT else MouseButton.LEFT)
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(
                    painterResource(R.drawable.tvremote_ic_mouse),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.tvremote_touchpad_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Types on the PC as you type on the phone: appended characters are sent right away and deleted ones become
 * backspaces, so search fields on the TV update live. Enter sends the key and clears the field.
 */
@Composable
private fun KeyboardRow(actions: RemoteActions) {
    var text by remember { mutableStateOf("") }

    fun onTextChange(newText: String) {
        when {
            newText.startsWith(text) -> newText.substring(text.length).takeIf { it.isNotEmpty() }?.let(actions::sendText)
            text.startsWith(newText) -> repeat(text.length - newText.length) { actions.key(RemoteKey.BACKSPACE) }
            else -> {
                // Edited in the middle: retype everything
                repeat(text.length) { actions.key(RemoteKey.BACKSPACE) }
                if (newText.isNotEmpty()) actions.sendText(newText)
            }
        }
        text = newText
    }

    fun sendEnter() {
        actions.key(RemoteKey.ENTER)
        text = ""
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = ::onTextChange,
            placeholder = { Text(stringResource(R.string.tvremote_type_on_pc)) },
            leadingIcon = { Icon(painterResource(R.drawable.tvremote_ic_keyboard), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { sendEnter() }),
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = {
            actions.key(RemoteKey.BACKSPACE)
            text = text.dropLast(1)
        }, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(R.drawable.tvremote_ic_backspace), contentDescription = stringResource(R.string.tvremote_backspace))
        }
        FilledTonalIconButton(onClick = ::sendEnter, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(R.drawable.tvremote_ic_enter), contentDescription = stringResource(R.string.tvremote_enter))
        }
    }
}

private const val TAP_TIMEOUT_MS = 250L
private const val STANDARD_DPI = 240f
private const val POINTER_SENSITIVITY = 1.6f
private const val SCROLL_SENSITIVITY = 0.5f
private const val MIN_SCROLL = 2.5f
