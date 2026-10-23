/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.tvremote

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.kde.kdeconnect_tp.R
import kotlin.math.roundToInt

@Composable
fun NowPlayingCard(media: MediaUiState?, available: Boolean, actions: RemoteActions, modifier: Modifier = Modifier) {
    RemoteCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(media)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (media == null) {
                    Text(
                        stringResource(if (available) R.string.tvremote_nothing_playing else R.string.tvremote_media_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        media.title.ifBlank { media.player },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (media.artist.isNotBlank()) {
                        Text(
                            media.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (media != null) {
            PlayerChooser(media, actions)
        }

        if (media != null && media.canSeek && media.lengthMs > 0) {
            SeekBar(media, actions)
        } else {
            Spacer(Modifier.size(12.dp))
        }

        val view = LocalView.current
        fun send(command: MediaCommand) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            actions.media(command)
        }
        val canSeek = media?.canSeek == true
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { send(MediaCommand.PREVIOUS) }, enabled = media?.canPrevious == true, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.tvremote_ic_skip_previous), stringResource(R.string.tvremote_previous))
            }
            FilledTonalIconButton(onClick = { send(MediaCommand.REWIND) }, enabled = canSeek, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.tvremote_ic_fast_rewind), stringResource(R.string.tvremote_rewind))
            }
            FilledIconButton(
                onClick = { send(MediaCommand.PLAY_PAUSE) },
                enabled = media != null,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    painterResource(if (media?.isPlaying == true) R.drawable.tvremote_ic_pause else R.drawable.tvremote_ic_play),
                    contentDescription = stringResource(R.string.tvremote_play_pause),
                    modifier = Modifier.size(36.dp),
                )
            }
            FilledTonalIconButton(onClick = { send(MediaCommand.FORWARD) }, enabled = canSeek, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.tvremote_ic_fast_forward), stringResource(R.string.tvremote_forward))
            }
            IconButton(onClick = { send(MediaCommand.NEXT) }, enabled = media?.canNext == true, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.tvremote_ic_skip_next), stringResource(R.string.tvremote_next))
            }
        }
    }
}

@Composable
private fun AlbumArt(media: MediaUiState?) {
    val bitmap = media?.albumArt
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Surface(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.clip(RoundedCornerShape(12.dp)))
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.tvremote_ic_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PlayerChooser(media: MediaUiState, actions: RemoteActions) {
    if (media.players.size < 2) return
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(media.player) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.tvremote_choose_player)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            media.players.forEach { player ->
                DropdownMenuItem(text = { Text(player) }, onClick = {
                    expanded = false
                    actions.selectPlayer(player)
                })
            }
        }
    }
}

@Composable
private fun SeekBar(media: MediaUiState, actions: RemoteActions) {
    // The PC only reports the position on changes, so extrapolate it while playing
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.isPlaying, media.positionSampledAt) {
        now = SystemClock.elapsedRealtime()
        while (media.isPlaying) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    val elapsed = if (media.isPlaying) (now - media.positionSampledAt).coerceAtLeast(0) else 0
    val position = (media.positionMs + elapsed).coerceIn(0, media.lengthMs)
    var dragged by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.padding(top = 8.dp)) {
        Slider(
            value = dragged ?: position.toFloat(),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let { actions.seekTo(it.toLong()) }
                dragged = null
            },
            valueRange = 0f..media.lengthMs.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatDuration(dragged?.toLong() ?: position), style = style, color = color)
            Text(formatDuration(media.lengthMs), style = style, color = color)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
fun VolumeCard(volume: VolumeUiState?, actions: RemoteActions, modifier: Modifier = Modifier) {
    val enabled = volume != null
    var dragged by remember { mutableStateOf<Float?>(null) }
    val view = LocalView.current
    RemoteCard(modifier, contentPadding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val muted = volume?.muted == true
            IconButton(onClick = { actions.toggleMute() }, enabled = volume?.canMute == true) {
                Icon(
                    painterResource(if (muted) R.drawable.tvremote_ic_volume_off else R.drawable.tvremote_ic_volume_up),
                    contentDescription = stringResource(R.string.tvremote_mute),
                    tint = if (muted) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
            FilledTonalIconButton(enabled = enabled, onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                actions.volumeStep(up = false)
            }) {
                Icon(painterResource(R.drawable.tvremote_ic_volume_down), stringResource(R.string.tvremote_volume_down))
            }
            Slider(
                value = dragged ?: volume?.volume?.toFloat() ?: 0f,
                enabled = enabled,
                onValueChange = {
                    dragged = it
                    actions.setVolume(it.roundToInt())
                },
                onValueChangeFinished = { dragged = null },
                valueRange = 0f..(volume?.max ?: 100).coerceAtLeast(1).toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            FilledTonalIconButton(enabled = enabled, onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                actions.volumeStep(up = true)
            }) {
                Icon(painterResource(R.drawable.tvremote_ic_volume_up), stringResource(R.string.tvremote_volume_up))
            }
        }
    }
}

/** Window and browser keys, useful to drive a video page in a browser. */
@Composable
fun KeysCard(enabled: Boolean, actions: RemoteActions, modifier: Modifier = Modifier) {
    RemoteCard(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledKey(rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), R.string.tvremote_back, enabled) { actions.key(RemoteKey.BACK) }
            LabeledKey(rememberVectorPainter(Icons.Filled.Home), R.string.tvremote_home, enabled) { actions.key(RemoteKey.HOME) }
            LabeledKey(painterResource(R.drawable.tvremote_ic_fullscreen), R.string.tvremote_fullscreen, enabled) { actions.key(RemoteKey.FULLSCREEN) }
            LabeledKey(rememberVectorPainter(Icons.Filled.Close), R.string.tvremote_close_window, enabled) { actions.key(RemoteKey.CLOSE) }
        }
    }
}

@Composable
private fun LabeledKey(icon: Painter, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
            enabled = enabled,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(icon, contentDescription = stringResource(label))
        }
        Spacer(Modifier.size(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
