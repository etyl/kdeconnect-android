/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.tvremote

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import org.kde.kdeconnect.helpers.JsonHelper.json
import org.kde.kdeconnect_tp.R
import java.util.UUID

/** A custom button on the remote. */
@Serializable
data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val icon: ShortcutIcon = ShortcutIcon.STAR,
    val action: ShortcutAction,
    /** Url to open for [ShortcutAction.OPEN_URL], name of the PC command for [ShortcutAction.RUN_COMMAND] */
    val target: String,
    /** For [ShortcutAction.RUN_COMMAND]: url opened instead when the PC has no command with that name */
    val fallbackUrl: String = "",
    /** Ask before running, for destructive actions such as shutting down */
    val confirm: Boolean = false,
)

@Serializable
enum class ShortcutAction {
    /** Opens the url in the PC's default browser, through the Share plugin */
    OPEN_URL,
    /** Runs a command defined on the PC in the Run Command plugin, matched by name */
    RUN_COMMAND,
}

@Serializable
enum class ShortcutIcon(@param:DrawableRes val drawable: Int) {
    MOVIE(R.drawable.tvremote_ic_movie),
    TV(R.drawable.tvremote_ic_tv),
    WEB(R.drawable.tvremote_ic_web),
    MUSIC(R.drawable.tvremote_ic_music),
    POWER(R.drawable.tvremote_ic_power),
    SLEEP(R.drawable.tvremote_ic_sleep),
    LOCK(R.drawable.tvremote_ic_lock),
    TERMINAL(R.drawable.tvremote_ic_terminal),
    STAR(R.drawable.tvremote_ic_star),
}

/** Persists the shortcuts in their own preferences file, separate from the rest of the app's settings. */
class ShortcutStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Shortcut> {
        val stored = prefs.getString(KEY_SHORTCUTS, null) ?: return defaultShortcuts
        return try {
            json.decodeFromString<List<Shortcut>>(stored)
        } catch (e: SerializationException) {
            Log.e(TAG, "Invalid stored shortcuts, resetting to defaults", e)
            defaultShortcuts
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid stored shortcuts, resetting to defaults", e)
            defaultShortcuts
        }
    }

    fun save(shortcuts: List<Shortcut>) {
        prefs.edit { putString(KEY_SHORTCUTS, json.encodeToString(shortcuts)) }
    }

    var selectedDeviceId: String?
        get() = prefs.getString(KEY_DEVICE, null)
        set(value) = prefs.edit { putString(KEY_DEVICE, value) }

    var tab: RemoteTab
        get() = prefs.getString(KEY_TAB, null)?.let { runCatching { RemoteTab.valueOf(it) }.getOrNull() } ?: RemoteTab.MEDIA
        set(value) = prefs.edit { putString(KEY_TAB, value.name) }

    var volumeKeysControlPc: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_KEYS, true)
        set(value) = prefs.edit { putBoolean(KEY_VOLUME_KEYS, value) }

    companion object {
        private const val TAG = "TvRemote"
        private const val PREFS_NAME = "tvremote"
        private const val KEY_SHORTCUTS = "shortcuts"
        private const val KEY_DEVICE = "device"
        private const val KEY_TAB = "tab"
        private const val KEY_VOLUME_KEYS = "volume_keys"

        val defaultShortcuts = listOf(
            Shortcut(label = "Netflix", icon = ShortcutIcon.MOVIE, action = ShortcutAction.RUN_COMMAND, target = "Netflix", fallbackUrl = "https://www.netflix.com/browse"),
            Shortcut(label = "Jellyfin", icon = ShortcutIcon.TV, action = ShortcutAction.RUN_COMMAND, target = "Jellyfin", fallbackUrl = "http://localhost:8096"),
            Shortcut(label = "Shut down", icon = ShortcutIcon.POWER, action = ShortcutAction.RUN_COMMAND, target = "Shut down", confirm = true),
        )
    }
}

enum class RemoteTab { MEDIA, TOUCHPAD }
