/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.tvremote

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.mousepad.KeyListenerView
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.plugins.share.SharePlugin
import org.kde.kdeconnect.plugins.systemvolume.Sink
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin

data class DeviceEntry(val id: String, val name: String, val reachable: Boolean)

data class MediaUiState(
    val players: List<String>,
    val player: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    /** [SystemClock.elapsedRealtime] at which [positionMs] was sampled, to extrapolate while playing */
    val positionSampledAt: Long,
    val albumArt: Bitmap?,
)

data class VolumeUiState(val volume: Int, val max: Int, val muted: Boolean, val canMute: Boolean)

data class RemoteUiState(
    val devices: List<DeviceEntry> = emptyList(),
    val device: DeviceEntry? = null,
    val hasInput: Boolean = false,
    val hasMediaPlugin: Boolean = false,
    /** null when the PC has no media player open */
    val media: MediaUiState? = null,
    val volume: VolumeUiState? = null,
    val commandNames: List<String> = emptyList(),
    val canRunCommands: Boolean = false,
    val canOpenUrls: Boolean = false,
)

enum class RemoteKey { BACK, HOME, FULLSCREEN, CLOSE, ENTER, BACKSPACE, SPACE, COPY, PASTE }

enum class MouseButton { LEFT, RIGHT }

enum class MediaCommand { PLAY_PAUSE, PREVIOUS, NEXT, REWIND, FORWARD }

enum class ShortcutResult { SENT, OPENED_FALLBACK_URL, MISSING_COMMAND, UNAVAILABLE }

/** Everything the UI can ask the PC to do. Kept as an interface so the screen can be previewed without a device. */
interface RemoteActions {
    fun selectDevice(deviceId: String) {}
    fun key(key: RemoteKey) {}
    fun mouseMove(dx: Float, dy: Float) {}
    fun click(button: MouseButton) {}
    fun scroll(dx: Double, dy: Double) {}
    fun sendText(text: String) {}
    fun media(command: MediaCommand) {}
    fun seekTo(positionMs: Long) {}
    fun selectPlayer(player: String) {}
    fun setVolume(volume: Int) {}
    fun volumeStep(up: Boolean) {}
    fun toggleMute() {}
    fun runShortcut(shortcut: Shortcut): ShortcutResult = ShortcutResult.UNAVAILABLE
    fun openCommandSetup() {}
}

/**
 * Bridges the regular KDE Connect plugins to the remote UI: listens to the selected device's plugins and
 * republishes their data as a single [RemoteUiState]. Only uses the plugins' public API, so upstream
 * changes inside the plugins don't affect it.
 */
class TvRemoteController(private val store: ShortcutStore) : RemoteActions {

    var state by mutableStateOf(RemoteUiState())
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var device: Device? = null
    private var userSelectedPlayer: String? = null

    // Plugins we registered listeners on, kept to unregister from the same instances after a plugin reload
    private var mprisPlugin: MprisPlugin? = null
    private var volumePlugin: SystemVolumePlugin? = null
    private var runCommandPlugin: RunCommandPlugin? = null
    private val listenedSinks = mutableListOf<Sink>()

    private val refreshLater = Runnable { refresh() }

    private val pluginsChangedListener = Device.PluginsChangedListener { onMainThread { attachPluginListeners() } }
    private val commandsChangedCallback = RunCommandPlugin.CommandsChangedCallback { postRefresh() }
    private val sinkUpdateListener = object : Sink.UpdateListener {
        override fun updateSink(sink: Sink) = postRefresh()
    }
    private val sinkListener = object : SystemVolumePlugin.SinkListener {
        override fun sinksChanged() = onMainThread { listenToSinks() }
    }

    fun start() {
        KdeConnect.getInstance().addDeviceListChangedCallback(LISTENER_ID) { onMainThread { onDeviceListChanged() } }
        onDeviceListChanged()
    }

    fun stop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback(LISTENER_ID)
        setDevice(null)
        handler.removeCallbacksAndMessages(null)
    }

    private fun onMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    /** Coalesces bursts of plugin updates (e.g. while a slider is dragged on the PC) into a single refresh */
    private fun postRefresh() {
        handler.removeCallbacks(refreshLater)
        handler.postDelayed(refreshLater, 50)
    }

    private fun pairedDevices(): List<Device> =
        KdeConnect.getInstance().devices.values.filter { it.isPaired }.sortedBy { it.name.lowercase() }

    private fun onDeviceListChanged() {
        val paired = pairedDevices()
        val wanted = paired.firstOrNull { it.deviceId == store.selectedDeviceId }
            ?: paired.firstOrNull { it.isReachable }
            ?: paired.firstOrNull()
        if (wanted !== device) {
            setDevice(wanted)
        } else {
            refresh()
        }
    }

    private fun setDevice(newDevice: Device?) {
        device?.removePluginsChangedListener(pluginsChangedListener)
        device = newDevice
        userSelectedPlayer = null
        newDevice?.addPluginsChangedListener(pluginsChangedListener)
        attachPluginListeners()
    }

    private fun attachPluginListeners() {
        mprisPlugin?.removePlayerStatusUpdatedHandler(LISTENER_ID)
        mprisPlugin?.removePlayerListUpdatedHandler(LISTENER_ID)
        runCommandPlugin?.removeCommandsUpdatedCallback(commandsChangedCallback)
        volumePlugin?.removeSinkListener(sinkListener)

        val d = device
        mprisPlugin = d?.getPlugin<MprisPlugin>()?.apply {
            setPlayerStatusUpdatedHandler(LISTENER_ID) { postRefresh() }
            setPlayerListUpdatedHandler(LISTENER_ID) { postRefresh() }
        }
        runCommandPlugin = d?.getPlugin<RunCommandPlugin>()?.apply {
            addCommandsUpdatedCallback(commandsChangedCallback)
        }
        volumePlugin = d?.getPlugin<SystemVolumePlugin>()?.apply {
            addSinkListener(sinkListener)
        }
        listenToSinks()
    }

    private fun listenToSinks() {
        listenedSinks.forEach { it.removeListener(sinkUpdateListener) }
        listenedSinks.clear()
        volumePlugin?.sinks?.forEach {
            it.addListener(sinkUpdateListener)
            listenedSinks.add(it)
        }
        refresh()
    }

    private fun refresh() {
        val d = device
        state = RemoteUiState(
            devices = pairedDevices().map { DeviceEntry(it.deviceId, it.name, it.isReachable) },
            device = d?.let { DeviceEntry(it.deviceId, it.name, it.isReachable) },
            hasInput = mousePad() != null,
            hasMediaPlugin = mprisPlugin != null,
            media = mediaState(),
            volume = volumeState(),
            commandNames = runCommandPlugin?.commandList?.toList().orEmpty().map { it.name }.sortedBy { it.lowercase() },
            canRunCommands = runCommandPlugin != null,
            canOpenUrls = d?.getPlugin<SharePlugin>() != null,
        )
    }

    private fun currentPlayer(): MprisPlugin.MprisPlayer? {
        val plugin = mprisPlugin ?: return null
        return plugin.getPlayerStatus(userSelectedPlayer)
            ?: plugin.playingPlayer
            ?: plugin.getPlayerStatus(plugin.playerList.firstOrNull())
    }

    private fun mediaState(): MediaUiState? {
        val plugin = mprisPlugin ?: return null
        val player = currentPlayer() ?: return null
        return MediaUiState(
            players = plugin.playerList,
            player = player.playerName,
            title = player.title,
            artist = listOf(player.artist, player.album).filter { it.isNotBlank() }.joinToString(" · "),
            isPlaying = player.isPlaying,
            canSeek = player.isSeekAllowed,
            canPrevious = player.isGoPreviousAllowed,
            canNext = player.isGoNextAllowed,
            positionMs = player.position,
            lengthMs = player.length,
            positionSampledAt = SystemClock.elapsedRealtime(),
            albumArt = player.getAlbumArt(),
        )
    }

    private fun defaultSink(): Sink? {
        val sinks = volumePlugin?.sinks ?: return null
        return sinks.firstOrNull { it.isDefault } ?: sinks.firstOrNull()
    }

    private fun volumeState(): VolumeUiState? {
        defaultSink()?.let { return VolumeUiState(it.volume, it.maxVolume, it.mute, canMute = true) }
        // Without the system volume plugin, fall back to the media player's own volume
        val player = currentPlayer()?.takeIf { it.isSetVolumeAllowed } ?: return null
        return VolumeUiState(player.volume, 100, muted = false, canMute = false)
    }

    private fun mousePad(): MousePadPlugin? = device?.getPlugin<MousePadPlugin>()

    override fun selectDevice(deviceId: String) {
        store.selectedDeviceId = deviceId
        onDeviceListChanged()
    }

    override fun key(key: RemoteKey) {
        val plugin = mousePad() ?: return
        when (key) {
            RemoteKey.BACK -> plugin.sendBack()
            RemoteKey.HOME -> plugin.sendHome()
            // "f" is the full screen key of web and desktop video players (Netflix, YouTube, Jellyfin, VLC...)
            RemoteKey.FULLSCREEN -> plugin.sendText("f")
            RemoteKey.CLOSE -> plugin.sendClose()
            RemoteKey.ENTER -> plugin.sendSelect()
            RemoteKey.BACKSPACE -> plugin.sendSpecialKey(KeyEvent.KEYCODE_DEL)
            RemoteKey.SPACE -> plugin.sendText(" ")
            RemoteKey.COPY -> plugin.sendCtrlShortcut("c")
            RemoteKey.PASTE -> plugin.sendCtrlShortcut("v")
        }
    }

    private fun MousePadPlugin.sendCtrlShortcut(key: String) {
        val np = NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST)
        np["key"] = key
        np["ctrl"] = true
        sendPacket(np)
    }

    private fun MousePadPlugin.sendSpecialKey(keyCode: Int) {
        val np = NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(keyCode)
        sendPacket(np)
    }

    override fun mouseMove(dx: Float, dy: Float) {
        mousePad()?.sendMouseDelta(dx, dy)
    }

    override fun click(button: MouseButton) {
        val plugin = mousePad() ?: return
        when (button) {
            MouseButton.LEFT -> plugin.sendLeftClick()
            MouseButton.RIGHT -> plugin.sendRightClick()
        }
    }

    override fun scroll(dx: Double, dy: Double) {
        mousePad()?.sendScroll(dx, dy)
    }

    override fun sendText(text: String) {
        mousePad()?.sendText(text)
    }

    override fun media(command: MediaCommand) {
        val player = currentPlayer() ?: return
        when (command) {
            MediaCommand.PLAY_PAUSE -> player.sendPlayPause()
            MediaCommand.PREVIOUS -> player.sendPrevious()
            MediaCommand.NEXT -> player.sendNext()
            MediaCommand.REWIND -> player.sendSeek(-SEEK_STEP_US)
            MediaCommand.FORWARD -> player.sendSeek(SEEK_STEP_US)
        }
    }

    override fun seekTo(positionMs: Long) {
        currentPlayer()?.sendSetPosition(positionMs.toInt())
        refresh()
    }

    override fun selectPlayer(player: String) {
        userSelectedPlayer = player
        refresh()
    }

    override fun setVolume(volume: Int) {
        val sink = defaultSink()
        if (sink != null) {
            volumePlugin?.sendVolume(sink.name, volume.coerceIn(0, sink.maxVolume))
        } else {
            currentPlayer()?.sendSetVolume(volume.coerceIn(0, 100))
        }
    }

    override fun volumeStep(up: Boolean) {
        val current = state.volume ?: return
        val step = (current.max / VOLUME_STEPS).coerceAtLeast(1)
        setVolume(current.volume + if (up) step else -step)
    }

    override fun toggleMute() {
        val sink = defaultSink() ?: return
        volumePlugin?.sendMute(sink.name, !sink.mute)
    }

    override fun runShortcut(shortcut: Shortcut): ShortcutResult {
        val d = device ?: return ShortcutResult.UNAVAILABLE
        return when (shortcut.action) {
            ShortcutAction.OPEN_URL -> if (openUrl(d, shortcut.target)) ShortcutResult.SENT else ShortcutResult.UNAVAILABLE
            ShortcutAction.RUN_COMMAND -> {
                val plugin = runCommandPlugin
                val wanted = commandNameKey(shortcut.target)
                val command = plugin?.commandList?.toList()?.firstOrNull { commandNameKey(it.name) == wanted }
                when {
                    command != null -> {
                        plugin.runCommand(command.key)
                        ShortcutResult.SENT
                    }
                    shortcut.fallbackUrl.isNotBlank() && openUrl(d, shortcut.fallbackUrl) -> ShortcutResult.OPENED_FALLBACK_URL
                    plugin == null -> ShortcutResult.UNAVAILABLE
                    else -> ShortcutResult.MISSING_COMMAND
                }
            }
        }
    }

    /** Ignores case and spaces, so a "Shut down" button finds a command named "Shutdown" or "shut down" */
    private fun commandNameKey(name: String): String = name.filterNot { it.isWhitespace() }.lowercase()

    private fun openUrl(device: Device, url: String): Boolean {
        val plugin = device.getPlugin<SharePlugin>() ?: return false
        val trimmed = url.trim()
        plugin.sendUrls(listOf(if ("://" in trimmed) trimmed else "https://$trimmed"))
        return true
    }

    override fun openCommandSetup() {
        runCommandPlugin?.sendSetupPacket()
    }

    companion object {
        private const val LISTENER_ID = "TvRemoteController"
        private const val SEEK_STEP_US = 10_000_000 // MPRIS seeks in microseconds
        private const val VOLUME_STEPS = 20
    }
}
