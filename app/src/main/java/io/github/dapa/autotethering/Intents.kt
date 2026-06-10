package io.github.dapa.autotethering

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Central factory for all Intents in the app.
 *
 * Keeping construction here means:
 * - Flag / component changes are made in one place
 * - Call sites are self-documenting (name describes purpose, not mechanics)
 * - Class renames / package changes require only a single fix
 */
object Intents {

    // ── External system screens ───────────────────────────────────────────────

    /**
     * Opens the tethering settings screen.
     *
     * Used by:
     * - [TetheringService.launchTetherSettings] — programmatic navigation
     * - [StatusService.createNotification]      — "Toggle" notification button
     *
     * Flags:
     * - FLAG_ACTIVITY_NEW_TASK       — required when starting from a non-Activity context
     * - FLAG_ACTIVITY_SINGLE_TOP     — reuses the existing instance if already on top
     * - FLAG_ACTIVITY_REORDER_TO_FRONT — brings it forward if already in the back stack
     */
    fun tetherSettings(settingsPackage: String, settingsActivity: String): Intent =
        Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(settingsPackage, settingsActivity)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

    /**
     * Wraps [tetherSettings] as a [PendingIntent] for use with
     * [TileService.startActivityAndCollapse] on Android 14+.
     *
     * Used by:
     * - [TetherTileService.onClick]
     */
     // API 34
    fun tetherSettingsPending(context: Context, settingsPackage: String, settingsActivity: String): android.app.PendingIntent =
        android.app.PendingIntent.getActivity(
            context,
            0,
            tetherSettings(settingsPackage, settingsActivity),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Opens the system "App Info" screen for this app.
     *
     * Used by:
     * - [StatusActivity] "Open Android Settings" button
     * - [StatusActivity] battery optimization dialog positive button
     */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }

    /**
     * Opens the system Accessibility Settings screen.
     *
     * Used by:
     * - [StatusActivity] "Open Accessibility" button
     * - [StatusActivity] accessibility prompt dialog
     */
    fun accessibilitySettings(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /**
     * Requests that the system exempt this app from battery optimization.
     *
     * Displays the system dialog asking the user to allow unrestricted background activity.
     * Requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission in the manifest.
     *
     * Used by:
     * - [StatusActivity.askPermissionsAndSettings]
     */
    @SuppressLint("BatteryLife")
    fun requestBatteryExemption(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }

    // ── Internal screens / services ───────────────────────────────────────────

    /**
     * Opens [SettingsActivity] (in-app keyword/settings screen).
     *
     * Used by:
     * - [StatusActivity] "Open Settings" button
     */
    fun appSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)

    /**
     * Opens [StatusActivity] (app's main status screen).
     *
     * Used by:
     * - [StatusService.createNotification] — notification tap target
     */
    fun statusScreen(context: Context): Intent =
        Intent(context, StatusActivity::class.java)

    /**
     * Starts [StatusService] (foreground notification service).
     *
     * Used by:
     * - [TetheringService.onServiceConnected]
     */
    fun statusService(context: Context): Intent =
        Intent(context, StatusService::class.java)

}  // object Intents
