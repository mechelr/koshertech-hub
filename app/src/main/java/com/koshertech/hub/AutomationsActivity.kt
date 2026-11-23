package com.koshertech.hub

import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class AutomationsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    data class Auto(
        val key: String, val icon: String, val title: String, val desc: String,
        val prefix: String,       // text before the time range (e.g. "Every night · ")
        val toggle: Boolean, val defOn: Boolean,
        val editTime: Boolean,
        val defSH: Int, val defSM: Int, val defEH: Int, val defEM: Int
    )

    private val autos = listOf(
        Auto("shabbos", "🕯️", "Shabbos & Yom Tov Mode",
            "Locks all apps at candle-lighting and unlocks after Shabbos / Yom Tov ends — using the live zmanim.",
            "Next: Fri 4:19 PM → Sat 5:18 PM", true, true, false, 0, 0, 0, 0),
        Auto("bedtime", "🌙", "Bedtime Lock",
            "Locks apps overnight so the device rests when the family does.",
            "Every night · ", true, true, true, 21, 0, 7, 0),
        Auto("learning", "📖", "Davening / Learning Lock",
            "Locks apps during the times you set for seder and tefillah.",
            "Sun–Thu · ", true, false, true, 8, 0, 9, 0),
        Auto("report", "📧", "Weekly Usage Report",
            "Emails the parent a summary of which apps were used and for how long.",
            "Every Motzei Shabbos", true, true, false, 0, 0, 0, 0),
        Auto("zreminders", "🔔", "Zmanim Reminders",
            "A gentle alert before candle-lighting, Mincha, and Shkiah.",
            "15 min before", true, true, false, 0, 0, 0, 0),
        Auto("update", "⬆️", "Auto Update Check",
            "Checks daily for a new version and offers to update.", "", false, true, false, 0, 0, 0, 0),
        Auto("reset", "🔄", "Daily Limit Reset",
            "Each app's daily time limit resets every morning automatically.", "", false, true, false, 0, 0, 0, 0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_automations)
        prefs = getSharedPreferences("ktautomations", Context.MODE_PRIVATE)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        gate()
    }

    // Locked behind the same parent PIN so a kid can't change automations or the report email.
    private fun gate() {
        val pin = getSharedPreferences("ktparental", Context.MODE_PRIVATE).getString("pin", null)
        if (pin == null) { rebuild(); return }
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.hint = "••••"; val pad = dp(16); et.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this).setTitle("Enter parent PIN").setView(et).setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                if (et.text.toString() == pin) rebuild()
                else { Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show(); finish() }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }.show()
    }

    private fun editEmail() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        et.hint = "parent@example.com"
        et.setText(prefs.getString("reportEmail", ""))
        val pad = dp(16); et.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this).setTitle("Email weekly reports to").setView(et)
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString("reportEmail", et.text.toString().trim()).apply(); rebuild() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rebuild() {
        val container = findViewById<LinearLayout>(R.id.autoCards)
        container.removeAllViews()
        for (a in autos) container.addView(card(a))
    }

    private fun fmt(h: Int, m: Int): String {
        val ampm = if (h < 12) "AM" else "PM"
        var hh = h % 12; if (hh == 0) hh = 12
        return String.format("%d:%02d %s", hh, m, ampm)
    }

    private fun whenText(a: Auto): String {
        if (!a.editTime) return a.prefix
        val sh = prefs.getInt(a.key + "_sh", a.defSH); val sm = prefs.getInt(a.key + "_sm", a.defSM)
        val eh = prefs.getInt(a.key + "_eh", a.defEH); val em = prefs.getInt(a.key + "_em", a.defEM)
        return a.prefix + fmt(sh, sm) + " – " + fmt(eh, em) + "   ✎ tap to set"
    }

    private fun pickTimes(a: Auto) {
        val sh = prefs.getInt(a.key + "_sh", a.defSH); val sm = prefs.getInt(a.key + "_sm", a.defSM)
        TimePickerDialog(this, { _, startH, startM ->
            val eh = prefs.getInt(a.key + "_eh", a.defEH); val em = prefs.getInt(a.key + "_em", a.defEM)
            TimePickerDialog(this, { _, endH, endM ->
                prefs.edit()
                    .putInt(a.key + "_sh", startH).putInt(a.key + "_sm", startM)
                    .putInt(a.key + "_eh", endH).putInt(a.key + "_em", endM).apply()
                rebuild()
            }, eh, em, false).also { it.setTitle("End time"); it.show() }
        }, sh, sm, false).also { it.setTitle("Start time"); it.show() }
    }

    private fun card(a: Auto): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.TOP
        val cbg = GradientDrawable(); cbg.cornerRadius = dp(15).toFloat(); cbg.setColor(0xFF161616.toInt()); cbg.setStroke(1, 0xFF2A2A2A.toInt())
        card.background = cbg
        card.setPadding(dp(15), dp(15), dp(15), dp(15))
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.topMargin = dp(12)
        card.layoutParams = clp

        val ic = TextView(this); ic.text = a.icon; ic.textSize = 22f
        ic.layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)

        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val t = TextView(this); t.text = a.title; t.setTextColor(0xFFF5F5F5.toInt()); t.setTypeface(t.typeface, Typeface.BOLD); t.textSize = 14f
        val d = TextView(this); d.text = a.desc; d.setTextColor(0xFF8C8C8C.toInt()); d.textSize = 11f
        val dlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); dlp.topMargin = dp(3); d.layoutParams = dlp
        col.addView(t); col.addView(d)
        if (a.prefix.isNotBlank()) {
            val w = TextView(this); w.text = whenText(a); w.setTextColor(0xFFFFFFFF.toInt()); w.setTypeface(w.typeface, Typeface.BOLD); w.textSize = 11f
            val wlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); wlp.topMargin = dp(5); w.layoutParams = wlp
            if (a.editTime) w.setOnClickListener { pickTimes(a) }
            col.addView(w)
        }

        if (a.key == "report") {
            val email = prefs.getString("reportEmail", "") ?: ""
            val emTv = TextView(this)
            emTv.text = if (email.isBlank()) "📧 Send to: tap to set an email" else "📧 Send to: " + email + "   ✎"
            emTv.setTextColor(0xFFFFFFFF.toInt()); emTv.textSize = 11f; emTv.setTypeface(emTv.typeface, Typeface.BOLD)
            val elp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); elp.topMargin = dp(6); emTv.layoutParams = elp
            emTv.setOnClickListener { editEmail() }
            col.addView(emTv)
        }

        card.addView(ic); card.addView(col)

        if (a.toggle) {
            val sw = SwitchCompat(this)
            sw.isChecked = prefs.getBoolean(a.key, a.defOn)
            sw.setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(a.key, c).apply() }
            card.addView(sw)
        } else {
            val always = TextView(this); always.text = "ALWAYS ON"; always.setTextColor(0xFFFFFFFF.toInt()); always.setTypeface(always.typeface, Typeface.BOLD); always.textSize = 10f
            card.addView(always)
        }
        return card
    }
}
