package com.koshertech.hub

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class AutomationsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    data class Auto(val key: String, val icon: String, val title: String, val desc: String, val when_: String, val toggle: Boolean, val defOn: Boolean)
    private val autos = listOf(
        Auto("shabbos", "🕯️", "Shabbos & Yom Tov Mode", "Locks all apps at candle-lighting and unlocks after Shabbos / Yom Tov ends — using the live zmanim.", "Next: Fri 4:19 PM → Sat 5:18 PM", true, true),
        Auto("bedtime", "🌙", "Bedtime Lock", "Locks apps overnight so the device rests when the family does.", "Every night · 9:00 PM – 7:00 AM", true, true),
        Auto("learning", "📖", "Davening / Learning Lock", "Locks apps during the times you set for seder and tefillah.", "Sun–Thu · 8:00–9:00 AM, 2:00–4:00 PM", true, false),
        Auto("report", "📧", "Weekly Usage Report", "Emails the parent a summary of which apps were used and for how long.", "Every Motzei Shabbos", true, true),
        Auto("zreminders", "🔔", "Zmanim Reminders", "A gentle alert before candle-lighting, Mincha, and Shkiah.", "15 min before", true, true),
        Auto("update", "⬆️", "Auto Update Check", "Checks daily for a new version and offers to update.", "", false, true),
        Auto("reset", "🔄", "Daily Limit Reset", "Each app's daily time limit resets every morning automatically.", "", false, true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_automations)
        prefs = getSharedPreferences("ktautomations", Context.MODE_PRIVATE)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        val container = findViewById<LinearLayout>(R.id.autoCards)
        for (a in autos) container.addView(card(a))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun card(a: Auto): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.TOP
        val cbg = GradientDrawable(); cbg.cornerRadius = dp(15).toFloat(); cbg.setColor(0xFF0C1838.toInt()); cbg.setStroke(1, 0xFF1C2A52.toInt())
        card.background = cbg
        card.setPadding(dp(15), dp(15), dp(15), dp(15))
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.topMargin = dp(12)
        card.layoutParams = clp

        val ic = TextView(this); ic.text = a.icon; ic.textSize = 22f
        ic.layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)

        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val t = TextView(this); t.text = a.title; t.setTextColor(0xFFF3F7FF.toInt()); t.setTypeface(t.typeface, Typeface.BOLD); t.textSize = 14f
        val d = TextView(this); d.text = a.desc; d.setTextColor(0xFF8599C0.toInt()); d.textSize = 11f
        (d.layoutParams ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).let {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(3); d.layoutParams = lp
        }
        col.addView(t); col.addView(d)
        if (a.when_.isNotBlank()) {
            val w = TextView(this); w.text = a.when_; w.setTextColor(0xFF22D3EE.toInt()); w.setTypeface(w.typeface, Typeface.BOLD); w.textSize = 11f
            val wlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); wlp.topMargin = dp(5); w.layoutParams = wlp
            col.addView(w)
        }

        card.addView(ic); card.addView(col)

        if (a.toggle) {
            val sw = SwitchCompat(this)
            sw.isChecked = prefs.getBoolean(a.key, a.defOn)
            sw.setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(a.key, c).apply() }
            card.addView(sw)
        } else {
            val always = TextView(this); always.text = "ALWAYS ON"; always.setTextColor(0xFF34D399.toInt()); always.setTypeface(always.typeface, Typeface.BOLD); always.textSize = 10f
            card.addView(always)
        }
        return card
    }
}
