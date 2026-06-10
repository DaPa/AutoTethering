package io.github.dapa.autotethering

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import android.util.Log


/*
    StatusService
       ↓ creates notification
       ↓ attaches button action
    ToggleReceiver (BroadcastReceiver)
       ↓ reacts to button click
       ↓ triggers toggle logic
    TetheringService
       ↓ (already handles actual toggle via UI automation)

    When user taps button:
        Notification button "Toggle"
                ↓
        ToggleReceiver.onReceive()
                ↓
        Opens Settings → Tether screen
                ↓
        AccessibilityService detects UI
                ↓
        Clicks toggle
                ↓
        DONE
 */

/**
 * Foreground service showing persistent notification with tethering status.
 */
class StatusService : Service() {

    private lateinit var tetherSettingsName: String
    private lateinit var tetherActivityName: String

    override fun onCreate() {
        super.onCreate()
        // Immediately promote to foreground on creation, before Android has a
        // chance to kill it. onStartCommand may arrive slightly later.
        createNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "StatusService::onStartCommand")
        // Notification already created in onCreate(); refresh it in case
        // settings changed since last start (e.g. different tetherActivityName).
        createNotification()
        return START_STICKY
    }

    private fun createNotification() {
        val channelId = "tether_status"

        val manager = getSystemService(NotificationManager::class.java)

        // Create channel
        val channel = NotificationChannel(
            channelId,
            "Tethering Status",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        // Open app intent when clicking notification itself
        val openPending = PendingIntent.getActivity(
            this, 0, Intents.statusScreen(this),
            PendingIntent.FLAG_IMMUTABLE
        )

        val sharedPrefs = getSharedPreferences(SettingsActivity.SHARED_PREFS_NAME, MODE_PRIVATE)

        tetherSettingsName = sharedPrefs.getString(
            SettingsActivity.ETHERNET_SETTINGS_TEXT_ID,
            getString(R.string.custom_ethernet_tether_settings_text_val)
        ) ?: getString(R.string.custom_ethernet_tether_settings_text_val)

        tetherActivityName = sharedPrefs.getString(
            SettingsActivity.ETHERNET_ACTIVITY_TEXT_ID,
            getString(R.string.custom_ethernet_tether_activity_text_val)
        ) ?: getString(R.string.custom_ethernet_tether_activity_text_val)

        // Toggle tethering intent when user clicks the quick "Toggle" button inside notification
        val toggleIntent = Intents.tetherSettings(tetherSettingsName, tetherActivityName)
        val togglePending = PendingIntent.getActivity(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT  // ← UPDATE_CURRENT important
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Ethernet Tethering")
            .setContentText("Tap to toggle")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPending)  // notification click triggers StatusActivity
            .setOngoing(true)
            // this is where the quick "Toggle" button (inside notification) is added
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_dialog_info),
                    "Toggle",
                    togglePending  // quick "Toggle" button triggers Tethering settings
                ).build()
            )
            .build()

        Log.i(TAG, "StatusService::createNotification -> startForeground!")
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "##.StatusService"
    }
}
