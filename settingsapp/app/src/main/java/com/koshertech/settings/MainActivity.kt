package com.koshertech.settings

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.tileWifi).setOnClickListener { openWifi() }
        findViewById<View>(R.id.tileBluetooth).setOnClickListener { open(Settings.ACTION_BLUETOOTH_SETTINGS) }
        findViewById<View>(R.id.tileDisplay).setOnClickListener { open(Settings.ACTION_DISPLAY_SETTINGS) }
        findViewById<View>(R.id.tileSound).setOnClickListener { openVolume() }
        findViewById<View>(R.id.tileSecurity).setOnClickListener { openScreenLock() }

        findViewById<TextView>(R.id.deviceModel).text = Build.MANUFACTURER + " " + Build.MODEL

        startGlow()
    }

    private fun startGlow() {
        val glow = findViewById<View>(R.id.glowBorder)
        val anim = ObjectAnimator.ofFloat(glow, "alpha", 0.35f, 1f)
        anim.duration = 1700
        anim.repeatMode = ValueAnimator.REVERSE
        anim.repeatCount = ValueAnimator.INFINITE
        anim.start()
    }

    private fun open(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    // Wi-Fi: use the inline quick panel on Android 10+ (doesn't expose full Settings).
    private fun openWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { startActivity(Intent(Settings.Panel.ACTION_WIFI)); return } catch (e: Exception) {}
        }
        open(Settings.ACTION_WIFI_SETTINGS)
    }

    private fun openVolume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { startActivity(Intent(Settings.Panel.ACTION_VOLUME)); return } catch (e: Exception) {}
        }
        open(Settings.ACTION_SOUND_SETTINGS)
    }

    // Jump straight to the "set screen lock" flow.
    private fun openScreenLock() {
        try { startActivity(Intent("android.app.action.SET_NEW_PASSWORD")); return } catch (e: Exception) {}
        open(Settings.ACTION_SECURITY_SETTINGS)
    }
}
