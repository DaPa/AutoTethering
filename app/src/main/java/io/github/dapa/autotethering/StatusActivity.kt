package io.github.dapa.autotethering

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.hardware.usb.UsbManager
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/**
 * Displays current system state:
 * - Tethering ON/OFF
 * - USB connected
 * - Accessibility enabled
 */
class StatusActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tetheringManager: TetheringManager
    private var ethernetActive = false
    private lateinit var callback: TetheringManager.TetheringEventCallback
    private lateinit var usbManager: UsbManager
    private lateinit var pm: PowerManager

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, REFRESH_DELAY) // refresh every 1s
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        pm = getSystemService(PowerManager::class.java)

        Log.i(TAG, "StatusActivity::onCreate")

        setContentView(R.layout.activity_status)

        tetheringManager = getSystemService(TETHERING_SERVICE) as TetheringManager

        callback = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                ethernetActive = interfaces.any { it.type == 5 }
                runOnUiThread { updateStatus() }
            }
        }

        val root = findViewById<View>(android.R.id.content)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )

            v.setPadding(
                systemBars.left,
                systemBars.top,   // ✅ pushes below status bar
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        val sharedPrefs = getSharedPreferences(SettingsActivity.SHARED_PREFS_NAME, MODE_PRIVATE)

        val tetherSettingsName = sharedPrefs.getString(
            SettingsActivity.ETHERNET_SETTINGS_TEXT_ID,
            getString(R.string.custom_ethernet_tether_settings_text_val)
        ) ?: getString(R.string.custom_ethernet_tether_settings_text_val)

        val tetherActivityName = sharedPrefs.getString(
            SettingsActivity.ETHERNET_ACTIVITY_TEXT_ID,
            getString(R.string.custom_ethernet_tether_activity_text_val)
        ) ?: getString(R.string.custom_ethernet_tether_activity_text_val)

        // Setup UI buttons
        findViewById<Button>(R.id.open_android_btn).setOnClickListener {
            startActivity(Intents.appDetails(this))
        }

        findViewById<Button>(R.id.open_settings_btn).setOnClickListener {
            startActivity(Intents.appSettings(this))
        }

        findViewById<Button>(R.id.open_accessibility_btn).setOnClickListener {
            startActivity(Intents.accessibilitySettings())
        }

        findViewById<Button>(R.id.open_tethering_btn).setOnClickListener {
            startActivity(Intents.tetherSettings(tetherSettingsName, tetherActivityName))
        }

        // Resurrect the foreground notification if it was killed
        startService(Intents.statusService(this))

        askPermissionsAndSettings()
    }

    override fun onStart() {
        super.onStart()
        tetheringManager.registerTetheringEventCallback(mainExecutor, callback)
    }

    override fun onStop() {
        super.onStop()
        tetheringManager.unregisterTetheringEventCallback(callback)
    }

    override fun onResume() {
        super.onResume()

        // Refresh when user returns from settings:
        //  immediate update once to get instant refresh
        updateStatus() // before starting loop

        Log.d(TAG, "onResume → start auto-refresh")
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()

        Log.d(TAG, "onPause → stop auto-refresh")
        handler.removeCallbacks(refreshRunnable)
    }


    /**
     * Binds a status row (from item_status_row.xml) with an icon, label, and state chip.
     *
     * @param rowView    the inflated include view
     * @param iconRes    drawable resource for the leading icon
     * @param label      the row label string (e.g. "Tethering")
     * @param active     true = good state, false = problem state
     * @param activeText chip text when active  (e.g. "ON")
     * @param inactiveText chip text when inactive (e.g. "OFF")
     */
    private fun bindStatusRow(
        rowView: View,
        @DrawableRes iconRes: Int,
        label: String,
        active: Boolean,
        activeText: String,
        inactiveText: String
    ) {
        rowView.findViewById<ImageView>(R.id.row_icon).setImageResource(iconRes)
        rowView.findViewById<TextView>(R.id.row_label).text = label

        val chip = rowView.findViewById<Chip>(R.id.row_chip)
        chip.text = if (active) activeText else inactiveText
        chip.setChipBackgroundColorResource(
            if (active) R.color.chip_active else R.color.chip_inactive
        )
        chip.setTextColor(
            ContextCompat.getColor(
                this,
                if (active) R.color.chip_active_text else R.color.chip_inactive_text
            )
        )
    }

    /**
     * Updates ALL UI fields
     */
    private fun updateStatus() {
        val allGood: Boolean

        // 1. Tethering
        bindStatusRow(
            rowView       = findViewById(R.id.row_tethering),
            iconRes       = R.drawable.ic_status_tethering,
            label         = getString(R.string.label_tethering),
            active        = ethernetActive,
            activeText    = getString(R.string.status_on),
            inactiveText  = getString(R.string.status_off)
        )

        // 2. USB
        val usbConnected = usbManager.deviceList.isNotEmpty()
        bindStatusRow(
            rowView       = findViewById(R.id.row_usb),
            iconRes       = R.drawable.ic_status_usb,
            label         = getString(R.string.label_usb),
            active        = usbConnected,
            activeText    = getString(R.string.status_connected),
            inactiveText  = getString(R.string.status_not_connected)
        )

        // 3. Accessibility
        val accessEnabled = isAccessibilityEnabled(this)
        bindStatusRow(
            rowView       = findViewById(R.id.row_accessibility),
            iconRes       = R.drawable.ic_status_accessibility,
            label         = getString(R.string.label_accessibility),
            active        = accessEnabled,
            activeText    = getString(R.string.status_enabled),
            inactiveText  = getString(R.string.status_disabled)
        )

        // 4. Battery
        val batteryIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        bindStatusRow(
            rowView       = findViewById(R.id.row_battery),
            iconRes       = R.drawable.ic_status_battery,
            label         = getString(R.string.label_battery),
            active        = batteryIgnoring,
            activeText    = getString(R.string.status_battery_full),
            inactiveText  = getString(R.string.status_battery_optimized)
        )

        // Summary banner
        allGood = ethernetActive && usbConnected && accessEnabled && batteryIgnoring
        val summaryCard = findViewById<MaterialCardView>(R.id.summary_card)
        val summaryText = findViewById<TextView>(R.id.summary_text)
        if (allGood) {
            summaryCard.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.chip_active)
            )
            summaryText.text = getString(R.string.summary_all_good)
            summaryText.setTextColor(ContextCompat.getColor(this, R.color.chip_active_text))
        } else {
            summaryCard.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.chip_inactive)
            )
            summaryText.text = getString(R.string.summary_needs_attention)
            summaryText.setTextColor(ContextCompat.getColor(this, R.color.chip_inactive_text))
        }
    }

    /**
     * Checks if AccessibilityService is enabled
     */
    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, TetheringService::class.java)

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        return enabledServices?.contains(expected.flattenToString()) == true
    }

    private fun askPermissionsAndSettings() {

        var needAction = false

        // ✅ Check accessibility
        if (!isAccessibilityEnabled(this)) {
            needAction = true

            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility")
                .setMessage("Required for auto tethering")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intents.accessibilitySettings())
                }
                .show()
        }

        // ✅ Check battery optimization
        val pm = getSystemService(PowerManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)

        if (!ignoring) {
            needAction = true

            startActivity(Intents.requestBatteryExemption(this))

            AlertDialog.Builder(this)
                .setTitle("Enable Unrestricted Battery Usage")
                .setMessage(
                    "To ensure auto-tethering works reliably in the background, " +
                    "go to App Info → Battery and select \"Unrestricted\"."
                )
                .setPositiveButton("Open App Settings") { _, _ ->
                    startActivity(Intents.appDetails(this))
                }
                .setNegativeButton("Later", null)
                .show()

        }

        // ✅ Notification permission (Android 13+)
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            100
        )

        Log.d(TAG, "askPermissionsAndSettings → needAction = $needAction")
    }

    companion object {
        const val TAG = "##.StatusActivity"
        private const val REFRESH_DELAY: Long = 5000
    }

}
