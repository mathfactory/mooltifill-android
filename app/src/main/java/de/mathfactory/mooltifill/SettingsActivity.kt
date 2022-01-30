/*
 * Copyright (C) 2021 Bernhard Rauch.
 *
 * This file is part of Mooltifill.
 *
 * Mooltifill is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mooltifill is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mooltifill.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.mathfactory.mooltifill

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.autofill.AutofillManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import kotlinx.coroutines.*


class SettingsActivity : AppCompatActivity() {

    companion object {
        fun debugLevel(context: Context): Int = parsedIntSetting(context, "debug_level", 0)
        fun isDebugEnabled(context: Context): Boolean = parsedIntSetting(context, "debug_level", 0) > 0
        fun isDebugVerbose(context: Context): Boolean = parsedIntSetting(context, "debug_level", 0) > 1
        fun isAwarenessEnabled(context: Context): Boolean = booleanSetting(context, "awareness", true)
        fun pwgenLength(context: Context): Int = parsedIntSetting(context, "pwgen_length", 12) // TODO no hardcoded default
        fun pwgenChset(context: Context): String? = stringSetting(context, "pwgen_chset", context.getString(R.string.default_pwgen_chset))

        private fun <T> castChecked(block: () -> T): T? =
            try { block() } catch(e: ClassCastException) { null }

        private fun booleanSetting(context: Context, key: String, default: Boolean) =
            castChecked { PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, default) } ?: default

        private fun intSetting(context: Context, key: String, default: Int) =
            castChecked { PreferenceManager.getDefaultSharedPreferences(context).getInt(key, default) } ?: default

        private fun stringSetting(context: Context, key: String, default: String?) =
            castChecked { PreferenceManager.getDefaultSharedPreferences(context).getString(key, default) } ?: default

        private fun parsedIntSetting(context: Context, key: String, default: Int) =
            stringSetting(context, key, null)?.toIntOrNull() ?: default
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment())
                    .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)

        permissionSetup(this)
        AwarenessService.ensureService(this)
    }

    private fun permissionSetup(context: Context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if(isDebugEnabled(context)) {
                    Log.d("Mooltifill", "permission result: $it")
                }
            }
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
//        if (ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            if (ActivityCompat.shouldShowRequestPermissionRationale(
//                    this,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                )
//            ) {
//                val builder = AlertDialog.Builder(this)
//                builder.setTitle("This app needs location access")
//                builder.setMessage("Please grant location access so this app can detect beacons in the background.")
//                builder.setPositiveButton(android.R.string.ok, null)
//                builder.setOnDismissListener {
//                    requestPermissions(
//                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
//                        1
//                    )
//                }
//                builder.show()
//            } else {
//                // No explanation needed, we can request the permission.
//                requestPermissions(
//                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
//                    1
//                )
//
//                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
//                // app-defined int constant. The callback method gets the
//                // result of the request.
//            }
//
//        }
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    class SettingsFragment : PreferenceFragmentCompat() {
        companion object {
            private const val PING_TIMEOUT = 20000L
        }
        private lateinit var mEnableService: ActivityResultLauncher<Intent>
        var mDefaultServiceSet = false

        override fun onCreate(savedInstanceState: Bundle?) {
            mEnableService = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onDefaultServiceSet)
            super.onCreate(savedInstanceState)
        }

        override fun onResume() {
            super.onResume()
            findPreference<SwitchPreference>("enable_mooltifill")?.isChecked = mDefaultServiceSet || hasEnabledMooltifill(requireActivity())
            // only use info from onDefaultServiceSet once, as it may change later manually by the user
            mDefaultServiceSet = false
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
//            findPreference<Preference>("enable_mooltifill")?.setOnPreferenceClickListener {
//                enableService(requireContext().applicationContext)
//                true
//            }
            findPreference<SwitchPreference>("enable_mooltifill")?.setOnPreferenceChangeListener { _, newValue ->
                val activity = requireActivity()
                if (newValue == true) {
                    enableMooltifill(activity)
                    hasEnabledMooltifill(activity)
                } else {
                    disableMooltifill(activity)
                    true
                }
            }
            findPreference<ListPreference>("debug_level")?.setOnPreferenceChangeListener { _, newValue ->
                if(newValue is String) {
                    newValue.toIntOrNull()?.let(AwarenessService::setDebug)
                }
                true
            }
            findPreference<SwitchPreference>("awareness")?.setOnPreferenceChangeListener { _, newValue ->
                if(newValue == false) AwarenessService.stopService(requireContext())
                else AwarenessService.ensureService(requireContext(), null, true)
                true
            }
            findPreference<Preference>("test_ping")?.setOnPreferenceClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    it.summary = "Waiting for device access..."
                    val ping = withContext(Dispatchers.IO) {
                        MooltifillActivity.ping(requireContext())
                    }
                    it.summary = ping.getOrNull() ?: ping.exceptionOrNull()?.message
                }
                true
            }
            findPreference<Preference>("pwgen_cmd")?.setOnPreferenceClickListener {
                showPwGenDialog { service, login, password ->
                    CoroutineScope(Dispatchers.IO).launch {
                        MooltifillActivity.setCredentials(requireContext(), service, login, password)
                    }
                }
                true
            }
        }

        private fun showPwGenDialog(block: (String, String, String) -> Unit) {
            val ctx = requireContext()
            val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
            builder.setTitle("Password Generator")
            val viewInflated =
                LayoutInflater.from(context).inflate(R.layout.pwgen_dialog, view as ViewGroup?, false)

            builder.setView(viewInflated)

            builder.setPositiveButton("Generate") { _, _ ->
                val service = viewInflated.findViewById<EditText>(R.id.pwgen_service).text.toString()
                val login = viewInflated.findViewById<EditText>(R.id.pwgen_login).text.toString()
                val pw = generatePassword()
                if (service.isNotBlank() && login.isNotBlank() && !pw.isNullOrBlank()) {
                    block(service, login, pw)
                }
            }
            builder.setNegativeButton("Cancel") {
                    dialog, _ -> dialog.cancel()
            }

            builder.show()
        }

        private fun generatePassword(): String? {
            val ctx = requireContext()
            val chset = pwgenChset(ctx)
            val length = pwgenLength(ctx)
            if(chset == null || chset.isEmpty() || length < 1) return null
            val builder = StringBuilder()
            for(i in 0 until length) {
                val random = chset.indices.random()
                val c = chset[random]
                builder.append(c)
            }
            return builder.toString()
        }

        private fun hasEnabledMooltifill(context: Activity): Boolean {
            val autofillManager = context.getSystemService(AutofillManager::class.java)
            return autofillManager != null && autofillManager.hasEnabledAutofillServices()
        }

        private fun disableMooltifill(context: Activity) {
            val autofillManager = context.getSystemService(AutofillManager::class.java)
            autofillManager.disableAutofillServices()
        }

        private fun enableMooltifill(context: Activity) {
            if (!hasEnabledMooltifill(context)) {
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                intent.data = Uri.parse("package:de.mathfactory.mooltifill")
                if(isDebugEnabled(context)) {
                    Log.d("Mooltifill", "enableService(): intent=$intent")
                }
                mEnableService.launch(intent)
            } else {
                Toast.makeText(context, "Mooltifill is already enabled. Great!", Toast.LENGTH_SHORT).show()
            }
        }

        private fun onDefaultServiceSet(resultCode: ActivityResult) {
            if(isDebugEnabled(requireContext())) {
                Log.d("Mooltifill", "onDefaultServiceSet() resultCode=$resultCode")
            }
            when (resultCode.resultCode) {
                Activity.RESULT_OK -> {
                    Toast.makeText(requireContext(), "Mooltifill enabled. Great!", Toast.LENGTH_SHORT).show()
                    mDefaultServiceSet = true
                }
                Activity.RESULT_CANCELED -> {

                }
            }
        }
    }
}
