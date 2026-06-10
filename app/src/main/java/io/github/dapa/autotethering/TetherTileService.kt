package io.github.dapa.autotethering

import android.graphics.drawable.Icon
import android.net.TetheringInterface
import android.net.TetheringManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Quick Settings tile for ethernet tethering.
 *
 * Appears in the notification-shade tile panel.
 * Tapping opens the system tethering settings screen, where [TetheringService]
 * will automatically click the ethernet toggle if it is off.
 *
 * Tile state reflects live ethernet tethering state via [TetheringManager].
 *
 * Lifecycle notes:
 * - [onStartListening] — tile is visible; register callback and refresh state
 * - [onStopListening]  — tile scrolled away; unregister to avoid leaking
 * - [onClick]          — user tapped; launch tether settings
 */
class TetherTileService : TileService() {

    private lateinit var tetheringManager: TetheringManager
    private lateinit var tetheringCallback: TetheringManager.TetheringEventCallback

    private var ethernetActive = false

    override fun onCreate() {
        super.onCreate()
        tetheringManager = getSystemService(TetheringManager::class.java)
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")

        tetheringCallback = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                ethernetActive = interfaces.any { it.type == 5 }
                Log.d(TAG, "Tethering interfaces changed → ethernetActive=$ethernetActive")
                refreshTile()
            }
        }

        tetheringManager.registerTetheringEventCallback(mainExecutor, tetheringCallback)
        refreshTile() // set initial state immediately
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening")
        tetheringManager.unregisterTetheringEventCallback(tetheringCallback)
    }

    override fun onClick() {
        super.onClick()
        Log.i(TAG, "Tile tapped → launching tether settings")

        val sharedPrefs = getSharedPreferences(SettingsActivity.SHARED_PREFS_NAME, MODE_PRIVATE)
        val settingsPackage = sharedPrefs.getString(
            SettingsActivity.ETHERNET_SETTINGS_TEXT_ID,
            getString(R.string.custom_ethernet_tether_settings_text_val)
        ) ?: getString(R.string.custom_ethernet_tether_settings_text_val)
        val settingsActivity = sharedPrefs.getString(
            SettingsActivity.ETHERNET_ACTIVITY_TEXT_ID,
            getString(R.string.custom_ethernet_tether_activity_text_val)
        ) ?: getString(R.string.custom_ethernet_tether_activity_text_val)

        // API 34+: must use PendingIntent overload
        startActivityAndCollapse(
            Intents.tetherSettingsPending(this, settingsPackage, settingsActivity)
        )
    }

    /**
     * Updates tile label and state to match current ethernet tethering status.
     * Must be called on the main thread; [TetheringManager] callback already
     * delivers on [mainExecutor] so this is safe.
     */
    private fun refreshTile() {
        val tile = qsTile ?: return

        if (ethernetActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Eth Tether"
            tile.contentDescription = "Ethernet tethering on"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_tether_on)
            tile.stateDescription = "On"
            tile.subtitle = "Tether On"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Eth Tether"
            tile.contentDescription = "Ethernet tethering off"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_tether_off)
            tile.stateDescription = "Off"
            tile.subtitle = "Tether Off"
        }

        tile.updateTile()
        Log.d(TAG, "Tile refreshed → state=${tile.state}")
    }

    companion object {
        const val TAG = "##.TetherTileService"
    }
}
