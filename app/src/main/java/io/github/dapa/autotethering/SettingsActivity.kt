package io.github.dapa.autotethering

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit


/**
 * UI for configuring the keyword used to locate the tethering toggle.
 *
 * Allows adapting to:
 * - Different ROM wording
 * - Localization differences
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "SettingsActivity::onCreate...")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.settings_activity)
        val sharedPrefs = this.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)

        val ethernetKeywordInput = findViewById<EditText>(R.id.ethernet_keyword_text_input)
        ethernetKeywordInput.hint = getString(R.string.custom_ethernet_tether_checkbox_text_hint)
        ethernetKeywordInput.setText(sharedPrefs.getString(ETHERNET_KEYWORD_TEXT_ID, getString(R.string.custom_ethernet_tether_checkbox_text_val)))
        ethernetKeywordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                Log.i(TAG, "SettingsActivity::afterTextChanged for ethernetKeywordInput...")
                sharedPrefs.edit { putString(ETHERNET_KEYWORD_TEXT_ID, s.toString()) }
            }
        })

        val ethernetSettingsInput = findViewById<EditText>(R.id.ethernet_settings_text_input)
        ethernetSettingsInput.hint = getString(R.string.custom_ethernet_tether_settings_text_hint)
        ethernetSettingsInput.setText(sharedPrefs.getString(ETHERNET_SETTINGS_TEXT_ID, getString(R.string.custom_ethernet_tether_settings_text_val)))
        ethernetSettingsInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                Log.i(TAG, "SettingsActivity::afterTextChanged for ethernetActivityInput...")
                sharedPrefs.edit { putString(ETHERNET_SETTINGS_TEXT_ID, s.toString()) }
            }
        })

        val ethernetActivityInput = findViewById<EditText>(R.id.ethernet_activity_text_input)
        ethernetActivityInput.hint = getString(R.string.custom_ethernet_tether_activity_text_hint)
        ethernetActivityInput.setText(sharedPrefs.getString(ETHERNET_ACTIVITY_TEXT_ID, getString(R.string.custom_ethernet_tether_activity_text_val)))
        ethernetActivityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                Log.i(TAG, "SettingsActivity::afterTextChanged for ethernetActivityInput...")
                sharedPrefs.edit { putString(ETHERNET_ACTIVITY_TEXT_ID, s.toString()) }
            }
        })
    }

    companion object {
        const val SHARED_PREFS_NAME = "auto_tethering_settings"
        const val ETHERNET_KEYWORD_TEXT_ID = "ethernet_keyword_text_id"
        const val ETHERNET_SETTINGS_TEXT_ID = "ethernet_settings_text_id"
        const val ETHERNET_ACTIVITY_TEXT_ID = "ethernet_activity_text_id"
        const val TAG = "##.SettingsActivity"
    }
}