package com.koshertech.hub

import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
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
    private val FEEDBACK_ENDPOINT = "https://script.google.com/macros/s/AKfycbyItxTmCLC4oLj1Xv2xaoOwNmhl_r2XEln6BqFyoKStbyV5Q-ve-40wXByWJqkC9hDv/exec"

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

        findViewById<View>(R.id.tileCalendar).setOnClickListener { startActivity(Intent(this, CalendarActivity::class.java)) }
        findViewById<View>(R.id.tileParental).setOnClickListener { startActivity(Intent(this, ParentalActivity::class.java)) }
        findViewById<View>(R.id.tileAutomations).setOnClickListener { startActivity(Intent(this, AutomationsActivity::class.java)) }
        findViewById<View>(R.id.tileHelp).setOnClickListener { showHelp() }

        populateZmanim()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    private fun populateZmanim() {
        findViewById<TextView>(R.id.greetingText).text = ZmanimInfo.greeting()
        findViewById<TextView>(R.id.hebrewDate).text = ZmanimInfo.hebrewDate()
        findViewById<TextView>(R.id.englishDate).text = ZmanimInfo.englishDate()
        findViewById<TextView>(R.id.zmanimLoc).text = "🕯️ " + ZmanimInfo.locationName()
        findViewById<TextView>(R.id.candleVal).text = ZmanimInfo.candleLighting()

        val grid = findViewById<LinearLayout>(R.id.zmanimGrid)
        grid.removeAllViews()
        val list = ZmanimInfo.zmanim()
        var i = 0
        while (i < list.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            row.layoutParams = lp
            row.addView(zmanCell(list[i]))
            val sp = View(this)
            sp.layoutParams = LinearLayout.LayoutParams(dp(14), 1)
            row.addView(sp)
            if (i + 1 < list.size) {
                row.addView(zmanCell(list[i + 1]))
            } else {
                val filler = View(this)
                filler.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                row.addView(filler)
            }
            grid.addView(row)
            i += 2
        }
    }

    private fun zmanCell(z: Zman): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.HORIZONTAL
        cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val label = TextView(this)
        label.text = z.label
        label.textSize = 11f
        label.setTextColor(0xFF8599C0.toInt())
        label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val value = TextView(this)
        value.text = z.time
        value.textSize = 12f
        value.setTypeface(value.typeface, android.graphics.Typeface.BOLD)
        value.setTextColor(0xFFF3F7FF.toInt())
        cell.addView(label)
        cell.addView(value)
        return cell
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showHelp() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_help, null)
        val dialog = customDialog(view)
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.primaryBtn).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // Installed -> open it. NOT installed -> ask KosherTech to add it.
    // IMPORTANT: this never opens the Play Store or any app store. Approved apps
    // are installed by KosherTech (via MDM), so the customer can never browse a store.
    private fun openOrInstall(app: AppItem) {
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch != null) {
            startActivity(launch)   // already installed -> just open it
            return
        }
        showInstallRequestDialog(app)   // not installed -> safe request, no store
    }

    private fun showInstallRequestDialog(app: AppItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_install, null)
        val dialog = customDialog(view)
        view.findViewById<TextView>(R.id.installTitle).text = "Add " + app.name
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.primaryBtn).setOnClickListener {
            sendToInbox("Install request", app.name + " — customer asked to install it")
            Toast.makeText(this, app.name + " requested — KosherTech will add it to your device.",
                Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }
        dialog.show()
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
                conn.instanceFollowRedirects = true
                conn.doOutput = true
                val payload = JSONObject()
                payload.put("_subject", "KosherTech Hub: " + kind)
                payload.put("type", kind)
                payload.put("message", body)
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val ok = code in 200..399
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
