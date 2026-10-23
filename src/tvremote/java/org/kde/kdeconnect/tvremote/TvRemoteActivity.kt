/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.tvremote

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.compose.KdeTheme

/** Remote control for a computer plugged to a TV. Has its own launcher icon, next to the regular KDE Connect one. */
class TvRemoteActivity : AppCompatActivity() {

    private lateinit var store: ShortcutStore
    private lateinit var controller: TvRemoteController
    private var volumeKeysControlPc by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = ShortcutStore(this)
        controller = TvRemoteController(store)
        volumeKeysControlPc = store.volumeKeysControlPc

        setContent {
            KdeTheme(this) {
                var shortcuts by remember { mutableStateOf(store.load()) }
                var tab by remember { mutableStateOf(store.tab) }
                TvRemoteScreen(
                    state = controller.state,
                    actions = controller,
                    shortcuts = shortcuts,
                    onShortcutsChange = {
                        shortcuts = it
                        store.save(it)
                    },
                    tab = tab,
                    onTabChange = {
                        tab = it
                        store.tab = it
                    },
                    volumeKeysControlPc = volumeKeysControlPc,
                    onVolumeKeysControlPcChange = {
                        volumeKeysControlPc = it
                        store.volumeKeysControlPc = it
                    },
                    onOpenKdeConnect = { startActivity(Intent(this, MainActivity::class.java)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BackgroundService.Start(applicationContext)
        controller.start()
    }

    override fun onStop() {
        controller.stop()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val up = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> null
        }
        if (up != null && volumeKeysControlPc && controller.state.volume != null) {
            if (event.action == KeyEvent.ACTION_DOWN) controller.volumeStep(up)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
