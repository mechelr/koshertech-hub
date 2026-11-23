package com.koshertech.settings

import android.app.Dialog
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.BatteryManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.tileWifi).setOnClickListener { openWifi() }
        findViewById<View>(R.id.tileBluetooth).setOnClickListener { open(Settings.ACTION_BLUETOOTH_SETTINGS) }
        findViewById<View>(R.id.tileMobile).setOnClickListener { openMobile() }
        findViewById<View>(R.id.tileApn).setOnClickListener { openApn() }
        findViewById<View>(R.id.tileDisplay).setOnClickListener { showDisplayDialog() }
        findViewById<View>(R.id.tileSound).setOnClickListener { openVolume() }
        findViewById<View>(R.id.tileSecurity).setOnClickListener { openScreenLock() }
        findViewById<View>(R.id.tileFlash).setOnClickListener { toggleFlash() }
        findViewById<View>(R.id.tileRotate).setOnClickListener { toggleRotate() }
        findViewById<View>(R.id.tileLed).setOnClickListener { openLed() }
        findViewById<View>(R.id.tileBattery).setOnClickListener { openBattery() }

        initFlash()
        paintRotate()

        findViewById<TextView>(R.id.deviceModel).text = Build.MANUFACTURER + " " + Build.MODEL
    }

    // ---- Flashlight (LED torch) ----
    private var torchOn = false
    private var cameraManager: CameraManager? = null
    private var torchCamId: String? = null

    private fun initFlash() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        try {
            torchCamId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {}
    }

    private fun toggleFlash() {
        val cm = cameraManager; val id = torchCamId
        if (cm == null || id == null) { Toast.makeText(this, "No flashlight on this device.", Toast.LENGTH_SHORT).show(); return }
        try {
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            val tv = findViewById<TextView>(R.id.flashState)
            tv.text = if (torchOn) "On" else "Off"
            tv.setTextColor(if (torchOn) 0xFF22D3EE.toInt() else 0xFF8599C0.toInt())
        } catch (e: Exception) { Toast.makeText(this, "Couldn't toggle the flashlight.", Toast.LENGTH_SHORT).show() }
    }

    // ---- Auto-rotate ----
    private fun isAutoRotate(): Boolean =
        try { Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 } catch (e: Exception) { false }

    private fun paintRotate() {
        val tv = findViewById<TextView>(R.id.rotateState)
        val on = isAutoRotate()
        tv.text = if (on) "On" else "Off"
        tv.setTextColor(if (on) 0xFF22D3EE.toInt() else 0xFF8599C0.toInt())
    }

    private fun toggleRotate() {
        if (!canWrite()) {
            Toast.makeText(this, "Allow 'Modify system settings' for KosherTech Settings, then try again.", Toast.LENGTH_LONG).show()
            try {
                val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS); i.data = Uri.parse("package:" + packageName); startActivity(i)
            } catch (e: Exception) {}
            return
        }
        try {
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (isAutoRotate()) 0 else 1)
            paintRotate()
        } catch (e: Exception) { Toast.makeText(this, "Couldn't change auto-rotate.", Toast.LENGTH_SHORT).show() }
    }

    private fun open(action: String) {
        try { startActivity(Intent(action)) }
        catch (e: Exception) { Toast.makeText(this, "Not available on this device.", Toast.LENGTH_SHORT).show() }
    }

    private fun openWifi() {
        // Open the full Wi-Fi settings page (same approach as Bluetooth, which works in kiosk).
        // The floating Wi-Fi "panel" silently fails to show on locked/kiosk devices, so we skip it.
        try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); return } catch (e: Exception) {}
        try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)); return } catch (e: Exception) {}
        Toast.makeText(this, "Wi-Fi settings not available on this device.", Toast.LENGTH_SHORT).show()
    }

    // Show every reachable light/LED settings screen and let the user pick the LED one.
    private fun openLed() {
        try {
            val pm = packageManager
            val list = pm.queryIntentActivities(Intent().setPackage("com.android.settings"), 0)
            val labels = ArrayList<String>()
            val comps = ArrayList<Pair<String, String>>()
            for (ri in list) {
                val label = ri.loadLabel(pm).toString()
                val l = label.lowercase()
                if (l.contains("led") || l.contains("light") || l.contains("breathing") || l.contains("glow") || l.contains("led")) {
                    if (!labels.contains(label)) { labels.add(label); comps.add(Pair(ri.activityInfo.packageName, ri.activityInfo.name)) }
                }
            }
            if (labels.isEmpty()) {
                Toast.makeText(this, "No LED/Light screen is reachable here. Set it once during setup (it stays off).", Toast.LENGTH_LONG).show()
                return
            }
            AlertDialog.Builder(this)
                .setTitle("Pick the LED / light screen")
                .setItems(labels.toTypedArray()) { _, which ->
                    try {
                        val c = comps[which]
                        startActivity(Intent().setClassName(c.first, c.second).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Android blocked that screen.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't look up the LED setting.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMobile() {
        // Try the cellular/mobile-network pages in order (device makers vary).
        val tries = listOf(
            "android.settings.NETWORK_OPERATOR_SETTINGS",
            Settings.ACTION_DATA_ROAMING_SETTINGS,
            "android.settings.MOBILE_DATA_SETTINGS",
            Settings.ACTION_WIRELESS_SETTINGS
        )
        for (a in tries) { try { startActivity(Intent(a)); return } catch (e: Exception) {} }
        Toast.makeText(this, "Mobile network settings not available on this device.", Toast.LENGTH_SHORT).show()
    }

    private fun openApn() {
        try { startActivity(Intent(Settings.ACTION_APN_SETTINGS)); return } catch (e: Exception) {}
        Toast.makeText(this, "APN settings not available on this device.", Toast.LENGTH_SHORT).show()
    }

    private fun openVolume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { startActivity(Intent(Settings.Panel.ACTION_VOLUME)); return } catch (e: Exception) {}
        }
        open(Settings.ACTION_SOUND_SETTINGS)
    }

    private fun openScreenLock() {
        try { startActivity(Intent("android.app.action.SET_NEW_PASSWORD")); return } catch (e: Exception) {}
        open(Settings.ACTION_SECURITY_SETTINGS)
    }

    // ---- Native Display control (brightness + screen timeout) — no system page ----
    private fun customDialog(view: View): Dialog {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(view)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val w = (resources.displayMetrics.widthPixels * 0.90).toInt()
        d.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return d
    }

    private fun canWrite(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(this) else true
    }

    private fun showDisplayDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_display, null)
        val dialog = customDialog(view)

        val grantBanner = view.findViewById<View>(R.id.grantBanner)
        grantBanner.visibility = if (canWrite()) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.grantBtn).setOnClickListener {
            try {
                val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                i.data = Uri.parse("package:" + packageName)
                startActivity(i)
            } catch (e: Exception) {
                open(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            }
        }

        // Brightness
        val slider = view.findViewById<SeekBar>(R.id.brightnessSlider)
        val pct = view.findViewById<TextView>(R.id.brightnessPct)
        val cur = try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { 128 }
        slider.progress = cur
        pct.text = (cur * 100 / 255).toString() + "%"
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                pct.text = (p * 100 / 255).toString() + "%"
                if (!fromUser) return
                if (canWrite()) {
                    try {
                        Settings.System.putInt(contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS, if (p < 10) 10 else p)
                    } catch (e: Exception) {}
                } else {
                    grantBanner.visibility = View.VISIBLE
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Screen timeout chips
        val row = view.findViewById<LinearLayout>(R.id.timeoutRow)
        val labels = arrayOf("15s", "30s", "1m", "5m", "10m")
        val values = intArrayOf(15000, 30000, 60000, 300000, 600000)
        val curTimeout = try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) } catch (e: Exception) { 30000 }
        val chips = ArrayList<TextView>()
        fun paint(selectedIndex: Int) {
            for (i in chips.indices) {
                val chip = chips[i]
                if (i == selectedIndex) {
                    chip.setBackgroundResource(R.drawable.bg_request)
                    chip.setTextColor(0xFF04121C.toInt())
                } else {
                    chip.setBackgroundResource(R.drawable.bg_ghost)
                    chip.setTextColor(0xFF8599C0.toInt())
                }
            }
        }
        for (i in labels.indices) {
            val chip = TextView(this)
            chip.text = labels[i]
            chip.textSize = 12f
            chip.gravity = Gravity.CENTER
            chip.setPadding(0, dp(9), 0, dp(9))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = dp(7)
            chip.layoutParams = lp
            chip.isClickable = true
            val idx = i
            chip.setOnClickListener {
                if (canWrite()) {
                    try {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, values[idx])
                        paint(idx)
                    } catch (e: Exception) {}
                } else {
                    grantBanner.visibility = View.VISIBLE
                }
            }
            chips.add(chip)
            row.addView(chip)
        }
        var sel = 1
        for (i in values.indices) { if (values[i] == curTimeout) sel = i }
        paint(sel)

        buildThemeRow(view)

        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ===== BATTERY =====
    override fun onResume() {
        super.onResume()
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            findViewById<TextView>(R.id.batteryState).text = if (pct in 0..100) (if (charging) "⚡$pct%" else "$pct%") else ""
        } catch (e: Exception) {}
    }

    private fun openBattery() {
        val actions = arrayOf(Settings.ACTION_BATTERY_SAVER_SETTINGS, Intent.ACTION_POWER_USAGE_SUMMARY,
            "android.settings.BATTERY_SAVER_SETTINGS")
        for (a in actions) {
            try { startActivity(Intent(a)); return } catch (e: Exception) {}
        }
        Toast.makeText(this, "Battery settings aren't available on this phone", Toast.LENGTH_SHORT).show()
    }

    // ===== DARK / LIGHT THEME =====
    private fun isDarkNow(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun setDark(dark: Boolean) {
        val um = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val want = if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        try { um.nightMode = want } catch (e: Exception) {}
        if (um.nightMode == want) {
            Toast.makeText(this, if (dark) "Dark mode on" else "Light mode on", Toast.LENGTH_SHORT).show()
            return
        }
        // Android didn't let us switch it directly: open the phone's own Dark theme switch.
        Toast.makeText(this, "Turn Dark theme " + (if (dark) "ON" else "OFF") + " here", Toast.LENGTH_LONG).show()
        try { startActivity(Intent("android.settings.DARK_THEME_SETTINGS")); return } catch (e: Exception) {}
        try { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } catch (e: Exception) {}
    }

    private fun buildThemeRow(view: View) {
        val row = view.findViewById<LinearLayout>(R.id.themeRow)
        val chips = ArrayList<TextView>()
        fun paint(darkSel: Boolean) {
            for (i in chips.indices) {
                val on = (i == 0) == darkSel
                chips[i].setBackgroundResource(if (on) R.drawable.bg_request else R.drawable.bg_ghost)
                chips[i].setTextColor(if (on) 0xFF04121C.toInt() else 0xFF8599C0.toInt())
            }
        }
        for ((i, label) in arrayOf("🌙  Dark", "☀  Light").withIndex()) {
            val chip = TextView(this)
            chip.text = label; chip.textSize = 13f; chip.gravity = Gravity.CENTER
            chip.setPadding(0, dp(10), 0, dp(10))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = dp(8)
            chip.layoutParams = lp; chip.isClickable = true
            chip.setOnClickListener { setDark(i == 0); paint(i == 0) }
            chips.add(chip); row.addView(chip)
        }
        paint(isDarkNow())
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
