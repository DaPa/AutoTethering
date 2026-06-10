package io.github.dapa.autotethering

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock.elapsedRealtime
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.dapa.autotethering.SettingsActivity.Companion.ETHERNET_KEYWORD_TEXT_ID
import io.github.dapa.autotethering.SettingsActivity.Companion.ETHERNET_SETTINGS_TEXT_ID
import io.github.dapa.autotethering.SettingsActivity.Companion.ETHERNET_ACTIVITY_TEXT_ID
import io.github.dapa.autotethering.SettingsActivity.Companion.SHARED_PREFS_NAME
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean


/*
    Special Android service
    Runs in background
    Can observe and control UI
    Triggered by system accessibility framework
 */
@SuppressLint("AccessibilityPolicy")
class TetheringService : AccessibilityService() {
    private lateinit var tetherSwitchKeyword: String
    private lateinit var tetherSettingsName: String
    private lateinit var tetherActivityName: String
    private var serviceConnected = false
    // class-level field
    @Volatile private var turnedOnTimestamp: Long = 0
    private val clickPending = AtomicBoolean(false)
    lateinit var usbBroadcastReceiver: UsbBroadcastReceiver

    /**
     * Opens Android tethering settings screen.
     *
     * Retries until the AccessibilityService is fully connected.
     *
     * Why retry loop:
     * - AccessibilityService may not be ready immediately
     * - Avoid race condition when launching UI
     *
     * Once successful, the accessibility framework will start generating events
     * which are handled in onAccessibilityEvent().
     */
    private fun launchTetherSettings(context: Context) {
        Log.d(TAG, "Launching tether settings screen")
        while (true) {
            try {
                if (serviceConnected) {
                    Log.i(TAG, "Start TetherSettings activity!")
                    context.startActivity(Intents.tetherSettings(tetherSettingsName, tetherActivityName))
                    break
                } else {
                    Log.d(TAG, "Waiting for serviceConnected...")
                    // prevent CPU spin
                    Thread.sleep(RETRY_DELAY)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception ${e.toString()}!")
                Thread.sleep(RETRY_DELAY)
            }
        }
    }

    inner class UsbBroadcastReceiver : BroadcastReceiver() {
        /**
         * BroadcastReceiver for USB device events.
         *
         * Triggered when:
         * - USB Ethernet adapter is connected
         * - USB Ethernet adapter is disconnected
         *
         * Behavior:
         * - On attach → opens tethering settings (after 2s delay)
         * - On detach → resets internal state (timestamp)
         */
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "UsbBroadcastReceiver::onReceive...")
            when (intent!!.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                    if (context != null) {
                        // wait 2 seconds to allow system to detect NIC and UI to stabilize
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.i(TAG, "UsbBroadcastReceiver::onReceive(ACTION_USB_DEVICE_ATTACHED) -> delayed launchTetherSettings...")
                            launchTetherSettings(context)
                        }, CLICK_DELAY)
                    } else {
                        Log.e(TAG, "null context!")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED, UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    Log.i(TAG, "USB detached → reset state")
                    clickPending.set(false)   // cancel any pending gate
                    turnedOnTimestamp = 0
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "TetheringService::onDestroy → unregistering UsbBroadcastReceiver")
        unregisterReceiver(usbBroadcastReceiver)
    }

    /**
     * Called when the AccessibilityService instance is created.
     *
     * Responsibilities:
     * - Initialize tethering keyword (default or user-defined)
     * - Register BroadcastReceiver for:
     *      - USB attach/detach events
     *      - BOOT_COMPLETED (functional with manifest permission)
     *
     * This sets up the event-driven triggers used to start tethering automation.
     */
    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Service created")
        // load keyword from strings.xml
        tetherSwitchKeyword = getString(R.string.custom_ethernet_tether_checkbox_text_val)
        tetherSettingsName =  getString(R.string.custom_ethernet_tether_settings_text_val)
        tetherActivityName =  getString(R.string.custom_ethernet_tether_activity_text_val)
        // override from user settings (if any)
        val sharedPrefs = this.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)

        val keyword = sharedPrefs.getString(ETHERNET_KEYWORD_TEXT_ID, tetherSwitchKeyword)
        if (!keyword.isNullOrEmpty()) tetherSwitchKeyword = keyword

        val settings = sharedPrefs.getString(ETHERNET_SETTINGS_TEXT_ID, tetherSettingsName)
        if (!settings.isNullOrEmpty()) tetherSettingsName = settings

        val activity = sharedPrefs.getString(ETHERNET_ACTIVITY_TEXT_ID, tetherActivityName)
        if (!activity.isNullOrEmpty()) tetherActivityName = activity

        // register BroadcastReceiver: this is what listens for USB events
        val filter = IntentFilter()
        filter.addAction("android.intent.action.BOOT_COMPLETED")
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        usbBroadcastReceiver = UsbBroadcastReceiver()
        Log.i(TAG, "registering UsbBroadcastReceiver")
        registerReceiver(usbBroadcastReceiver, filter)
    }

    /**
     * Called by the system when the AccessibilityService is fully connected and ready.
     *
     * Responsibilities:
     * - Mark service as active (serviceConnected = true)
     * - Detect already-attached USB devices
     * - If a USB device is already present, immediately open tethering settings
     *
     * This ensures tethering is triggered even if the device was plugged before service start.
     */
    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility service connected ✅")

        // override some defaults from accessibility_service_config.xml
        val info = serviceInfo
        info.notificationTimeout = ACCESSIBILITY_DELAY
        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags =
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info

        // required for launchTetherSettings()
        serviceConnected = true

        // start the foreground service for persistent notification + quick toggles
        startService(Intents.statusService(this))

        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val deviceIterator: Iterator<UsbDevice> = usbManager.deviceList.values.iterator()
        // checks if any USB device exists
        Log.d(TAG, "USB devices present: ${deviceIterator.hasNext()}")
        if (deviceIterator.hasNext() || !usbManager.accessoryList.isNullOrEmpty()) {
            launchTetherSettings(this)
        } else {
            Log.e(TAG, "no USB!")
        }
        super.onServiceConnected()
    }

    /**
     * Core automation logic.
     *
     * Triggered whenever Accessibility events occur. Called MANY times when:
     *  - UI changes
     *  - windows open
     *  - views update
     *
     * Responsibilities:
     * - Detect when Settings screen is visible
     * - Search for tethering toggle using keyword text
     * - Identify the switch UI element
     * - If switch is OFF → click it
     *
     * Uses debounce logic to prevent repeated clicks.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // event.eventType values:
        //      32 = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        //    2048 = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        // 4194304 = AccessibilityEvent.TYPE_WINDOWS_CHANGED
        //Log.v(TAG, "Accessibility event: ${event?.eventType} from ${event?.packageName} ✅")

        //
        // The actual hierarchy around the "Ethernet tethering" row is:
        //   ${node.className} ("android.widget.xxx", ex: "android.widget.RelativeLayout") and
        //   ${node.viewIdResourceName} (if not null, then "android:id/xxx", ex: "android:id/title")
        //   ${node.text} (if not null, ex: "Share phone’s Internet connection via Ethernet")
        //   ${node.isClickable}, ${node.isCheckable}, ${node.isChecked}
        //
        //   LinearLayout  isClickable=true🟢 (isCheckable=false)  ← 3️⃣ THIS is what must be clicked
        //   ├── RelativeLayout  isClickable=false🔴 (isCheckable=false)
        //   │   ├── TextView  "Ethernet tethering"🟡  (isClickable=false, isCheckable=false) ← 1️⃣ findAccessibilityNodeInfosByText() finds this
        //   │   └── TextView  "Share phone's Internet..." (isClickable=false, isCheckable=false)
        //   └── LinearLayout  android:id/widget_frame  (isClickable=false, isCheckable=false)
        //       └── Switch  com.android.settings:id/switchWidget  (isClickable=false, isCheckable=true🟢)  ← 2️⃣ THIS is where the Tethering state can be checked
        //

        // a helper to find the switchWidget ( → 2️⃣ )
        fun findSwitchIn(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isCheckable) return node
            for (i in 0 until node.childCount) {
                val found = findSwitchIn(node.getChild(i) ?: continue)
                if (found != null) return found
            }
            return null
        }  // fun findSwitchIn

        // a helper to check if an element is checked
        fun isChecked(node: AccessibilityNodeInfo): Boolean =
            node.checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE

        //if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
        //    event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        //) {
        //    // only react to meaningful changes
        //}
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // small delay before scanning UI: prevents early scanning
                //if (event.eventTime - turnedOnTimestamp < RETRY_DELAY) return

                // filtering: only process Settings UI
                // if (event.packageName?.toString()?.contains("settings") == true)
                if (event.packageName == tetherSettingsName) {  // default: "com.android.settings"
                    //val rootNode = rootInActiveWindow ?: return
                    val rootNode = event.source ?: return

                    // searches for TextView "Ethernet tethering"   ( → 1️⃣ )
                    val nodeList: List<AccessibilityNodeInfo> = rootNode
                        .findAccessibilityNodeInfosByText(tetherSwitchKeyword)  // default: "Ethernet tethering"
                    Log.d(TAG, "Found ${nodeList.size} matching nodes")

                    val seen = mutableSetOf<AccessibilityNodeInfo>()
                    for (node in nodeList) {
                        // Walk up to find the clickable row ancestor LinearLayout  (isClickable=true) ( → 3️⃣ )
                        var ancestor: AccessibilityNodeInfo? = node
                        var path = 0
                        while (ancestor != null) {
                            if (ancestor.isClickable) break
                            ancestor = ancestor.parent
                            path++
                        }
                        val clickableRow = ancestor ?: continue
                        if (!seen.add(clickableRow)) continue  // skip duplicate row
                        Log.d(TAG, "✅ Found clickableRow ${clickableRow.className} ($path levels up from TextView)")

                        // Find the Switch within it to check current state
                        val switch = findSwitchIn(clickableRow) ?: continue
                        val timeSinceLastClick = elapsedRealtime() - turnedOnTimestamp

                        if (!isChecked(switch) && timeSinceLastClick > DEBOUNCE) {
                            // Bail out if a click attempt is already in flight
                            if (!clickPending.compareAndSet(false, true)) {
                                Log.d(TAG, "Click already pending, skipping")
                                return
                            }
                            Log.i(TAG, "Clicking tether row (isClickable ancestor)")
                            clickableRow.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Thread {
                                try {
                                    var confirmed = false
                                    repeat(10) {
                                        if (confirmed) return@repeat
                                        Thread.sleep(POLL_INTERVAL)
                                        switch.refresh()
                                        if (isChecked(switch)) confirmed = true
                                    }
                                    if (confirmed) {
                                        Log.i(TAG, "Tether toggled ✅")
                                        turnedOnTimestamp = elapsedRealtime()
                                    } else {
                                        Log.e(TAG, "Click fired but switch did not flip ❌")
                                    }
                                } finally {
                                    clickPending.set(false)  // always release, even on exception
                                }
                            }.start()
                        }

                    }
                }
            } else -> {
            }
        }
    }  // fun onAccessibilityEvent

    override fun onInterrupt() {}

    companion object {
        const val TAG = "##.TetheringService"
        private const val CLICK_DELAY: Long = 2000
        private const val RETRY_DELAY: Long =  200
        private const val POLL_INTERVAL: Long =  200
        private const val DEBOUNCE: Long = 3000
        private const val ACCESSIBILITY_DELAY: Long = 200
    }
}