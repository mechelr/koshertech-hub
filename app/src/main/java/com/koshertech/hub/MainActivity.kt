package com.koshertech.hub

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    // Feedback (requests / loopholes / ratings) is emailed here via FormSubmit.
    // The address is never shown anywhere in the app.
    private val FEEDBACK_ENDPOINT = "https://formsubmit.co/ajax/mechelr362@gmail.com"

    // ===== YOUR APPS =====
    private val allApps = listOf(
        AppItem("Zemer", "Music · Jewish songs", "PACKAGE_FOR_ZEMER", 0xFFE0A52B.toInt()),
        AppItem("24Six", "Music · Jewish streaming", "app.tfs.prod", 0xFFE04F5F.toInt()),
        AppItem("Zing", "Music · Streaming", "fm.jewishmusic.application", 0xFF9333EA.toInt())
    )

    // ===== PLANS (demo pricing — edit freely) =====
    data class Plan(
        val name: String,
        val price: String,
        val period: String,
        val features: String,
        val popular: Boolean,
        val accent: Int
    )

    private val plans = listOf(
        Plan("Basic", "$14.99", "/mo",
            "1 device  ·  approved apps  ·  email support", false, 0xFF22D3EE.toInt()),
        Plan("Family", "$24.99", "/mo",
            "Up to 4 devices  ·  remote management  ·  priority support", true, 0xFF9333EA.toInt()),
        Plan("Premium", "$34.99", "/mo",
            "Unlimited devices  ·  custom app approvals  ·  24/7 support", false, 0xFFE0A52B.toInt()),
        Plan("Yearly", "$299", "/yr",
            "All Premium features  ·  2 months free", false, 0xFF34D399.toInt())
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

    // ---- Custom popup: rounded card, its own X and buttons (no default Submit/Cancel) ----
    private fun customDialog(view: View): Dialog {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(view)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val w = (resources.displayMetrics.widthPixels * 0.90).toInt()
        d.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return d
    }

    private fun showRequestDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_request, null)
        val dialog = customDialog(view)
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.primaryBtn).setOnClickListener {
            val appName = view.findViewById<EditText>(R.id.reqAppName).text.toString()
            val reason = view.findViewById<EditText>(R.id.reqReason).text.toString()
            sendToInbox("App request", appName + " — " + reason)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showLoopholeDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loophole, null)
        val dialog = customDialog(view)
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.primaryBtn).setOnClickListener {
            val desc = view.findViewById<EditText>(R.id.loopDesc).text.toString()
            sendToInbox("Loophole report", desc)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showRateDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_rate, null)
        val dialog = customDialog(view)
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.primaryBtn).setOnClickListener {
            val bar = view.findViewById<RatingBar>(R.id.ratingBar)
            val comment = view.findViewById<EditText>(R.id.rateComment).text.toString()
            sendToInbox("Rating", bar.rating.toInt().toString() + " stars — " + comment)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showBuyPlanDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_buyplan, null)
        val dialog = customDialog(view)
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }

        val container = view.findViewById<ViewGroup>(R.id.planContainer)
        val inflater = LayoutInflater.from(this)
        for (plan in plans) {
            val card = inflater.inflate(R.layout.item_plan, container, false)
            card.findViewById<TextView>(R.id.planName).text = plan.name
            card.findViewById<TextView>(R.id.planName).setTextColor(plan.accent)
            card.findViewById<TextView>(R.id.planPrice).text = plan.price
            card.findViewById<TextView>(R.id.planPeriod).text = plan.period
            card.findViewById<TextView>(R.id.planFeatures).text = plan.features
            card.findViewById<TextView>(R.id.planCta).setTextColor(plan.accent)
            if (plan.popular) {
                card.findViewById<TextView>(R.id.planBadge).visibility = View.VISIBLE
            }
            // colored border to match the plan's accent
            val bg = card.findViewById<View>(R.id.planCard).background.mutate() as GradientDrawable
            bg.setStroke(if (plan.popular) 3 else 2, plan.accent)

            card.setOnClickListener {
                dialog.dismiss()
                showPaymentDialog(plan.name + " — " + plan.price + plan.period)
            }
            container.addView(card)
        }
        dialog.show()
    }

    // Demo card entry. Submit just shows a confirmation — no real payment happens.
    private fun showPaymentDialog(plan: String?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = customDialog(view)
        if (plan != null) view.findViewById<TextView>(R.id.payTitle).text = plan
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.primaryBtn).setOnClickListener {
            Toast.makeText(this, "Payment submitted ✓  Thank you!", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    // Emails the request/loophole/rating to you.
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
