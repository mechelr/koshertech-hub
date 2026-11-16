package com.koshertech.hub

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    // ===== EMAIL DELIVERY =====
    // The build machine swaps this placeholder for your hidden FORMSUBMIT_URL secret.
    // Your real email never lives in the project files — only in the locked secret.
    // If no secret is set, it stays a placeholder and the app just says "saved".
    private val FEEDBACK_ENDPOINT = "FEEDBACK_ENDPOINT_PLACEHOLDER"

    // ===== YOUR APPS =====
    // To add an app: copy a line, change the name/category/package/color.
    private val allApps = listOf(
        AppItem("Zemer", "Music · Jewish songs", "PACKAGE_FOR_ZEMER", 0xFFE0A52B.toInt()),
        AppItem("24Six", "Music · Jewish streaming", "app.tfs.prod", 0xFFE04F5F.toInt()),
        AppItem("Zing", "Music · Streaming", "fm.jewishmusic.application", 0xFF9333EA.toInt())
    )

    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recycler = findViewById<RecyclerView>(R.id.appList)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(allApps) { app -> openOrInstall(app) }
        recycler.adapter = adapter

        findViewById<EditText>(R.id.searchBox).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<TextView>(R.id.requestButton).setOnClickListener { showRequestDialog() }
        findViewById<TextView>(R.id.loopholeButton).setOnClickListener { showLoopholeDialog() }
        findViewById<TextView>(R.id.rateButton).setOnClickListener { showRateDialog() }
        findViewById<TextView>(R.id.buyPlanButton).setOnClickListener { showBuyPlanDialog() }
        findViewById<TextView>(R.id.updatePaymentButton).setOnClickListener { showPaymentDialog(null) }

        startGlow()
    }

    override fun onResume() {
        super.onResume()
        // Re-check which apps are installed each time the screen comes back.
        adapter.notifyDataSetChanged()
    }

    // Gemini-style breathing glow around the whole screen.
    private fun startGlow() {
        val glow = findViewById<View>(R.id.glowBorder)
        val anim = ObjectAnimator.ofFloat(glow, "alpha", 0.35f, 1f)
        anim.duration = 1700
        anim.repeatMode = ValueAnimator.REVERSE
        anim.repeatCount = ValueAnimator.INFINITE
        anim.start()
    }

    // Installed -> open it. Not installed -> send them to install it.
    private fun openOrInstall(app: AppItem) {
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch != null) {
            startActivity(launch)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app.packageName)))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + app.packageName)))
            } catch (e2: Exception) {
                Toast.makeText(this, "Can't open the store for " + app.name + " yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== PLANS (demo only — made-up pricing you can edit anytime) =====
    private val plans = arrayOf(
        "Basic — $14.99/mo   ·   1 device, approved apps, email support",
        "Family — $24.99/mo   ·   up to 4 devices, remote management",
        "Premium — $34.99/mo   ·   unlimited devices, priority 24/7 support",
        "Yearly — $299/yr   ·   Premium features, 2 months free"
    )

    private fun showBuyPlanDialog() {
        AlertDialog.Builder(this)
            .setTitle("Choose your plan")
            .setItems(plans) { _, which -> showPaymentDialog(plans[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Demo card entry. Submit just shows a confirmation — no real payment happens.
    private fun showPaymentDialog(plan: String?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val title = view.findViewById<TextView>(R.id.payTitle)
        if (plan != null) title.text = "Plan: " + plan
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Submit") { _, _ ->
                Toast.makeText(this, "Payment submitted ✓  Thank you!", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRequestDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_request, null)
        val appName = view.findViewById<EditText>(R.id.reqAppName)
        val reason = view.findViewById<EditText>(R.id.reqReason)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Send request") { _, _ ->
                sendToInbox("App request", appName.text.toString() + " — " + reason.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoopholeDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loophole, null)
        val desc = view.findViewById<EditText>(R.id.loopDesc)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Send report") { _, _ ->
                sendToInbox("Loophole report", desc.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRateDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_rate, null)
        val bar = view.findViewById<RatingBar>(R.id.ratingBar)
        val comment = view.findViewById<EditText>(R.id.rateComment)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Send rating") { _, _ ->
                sendToInbox("Rating", bar.rating.toInt().toString() + " stars — " + comment.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Emails the request/loophole/rating to you when FEEDBACK_ENDPOINT is set.
    private fun sendToInbox(kind: String, body: String) {
        val endpoint = FEEDBACK_ENDPOINT
        if (endpoint.isBlank() || endpoint.contains("PLACEHOLDER")) {
            Toast.makeText(this, "Saved. (Email delivery isn't switched on yet.)", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Sending…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true
                val payload = JSONObject()
                payload.put("_subject", "KosherTech Hub: " + kind)
                payload.put("type", kind)
                payload.put("message", body)
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val ok = conn.responseCode in 200..299
                val code = conn.responseCode
                conn.disconnect()
                runOnUiThread {
                    Toast.makeText(this,
                        if (ok) "Sent — thank you!" else "Couldn't send (error " + code + ")",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Couldn't send: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
