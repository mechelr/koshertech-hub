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

class ParentalActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    data class Rule(val name: String, val letter: String, val color: Int, val defOn: Boolean,
                    val defSH: Int, val defSM: Int, val defEH: Int, val defEM: Int)
    private val rules = listOf(
        Rule("Zemer", "Z", 0xFFE0A52B.toInt(), true, 15, 0, 20, 0),
        Rule("24Six", "2", 0xFFE04F5F.toInt(), true, 16, 0, 21, 0),
        Rule("Zing", "Z", 0xFF9333EA.toInt(), false, 9, 0, 21, 0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parental)
        prefs = getSharedPreferences("ktparental", Context.MODE_PRIVATE)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.changePin).setOnClickListener { setPin(true) }
        gate()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun gate() {
        val saved = prefs.getString("pin", null)
        if (saved == null) { setPin(false); return }
        val et = pinInput()
        AlertDialog.Builder(this).setTitle("Enter parent PIN").setView(et).setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                if (et.text.toString() == saved) buildCards()
                else { Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show(); finish() }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }.show()
    }

    private fun setPin(changing: Boolean) {
        val et = pinInput()
        AlertDialog.Builder(this)
            .setTitle(if (changing) "Set a new 4-digit PIN" else "Create a parent PIN (4 digits)")
            .setView(et).setCancelable(!changing)
            .setPositiveButton("Save") { _, _ ->
                val p = et.text.toString()
                if (p.length >= 4) { prefs.edit().putString("pin", p).apply(); Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show(); if (!changing) buildCards() }
                else { Toast.makeText(this, "Use at least 4 digits", Toast.LENGTH_SHORT).show(); if (!changing) finish() }
            }
            .setNegativeButton("Cancel") { _, _ -> if (!changing) finish() }.show()
    }

    private fun pinInput(): EditText {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.hint = "••••"; val pad = dp(16); et.setPadding(pad, pad, pad, pad)
        return et
    }

    private fun fmt(h: Int, m: Int): String {
        val ampm = if (h < 12) "AM" else "PM"; var hh = h % 12; if (hh == 0) hh = 12
        return String.format("%d:%02d %s", hh, m, ampm)
    }

    private fun pickTimes(r: Rule) {
        val sh = prefs.getInt(r.name + "_sh", r.defSH); val sm = prefs.getInt(r.name + "_sm", r.defSM)
        TimePickerDialog(this, { _, startH, startM ->
            val eh = prefs.getInt(r.name + "_eh", r.defEH); val em = prefs.getInt(r.name + "_em", r.defEM)
            TimePickerDialog(this, { _, endH, endM ->
                prefs.edit().putInt(r.name + "_sh", startH).putInt(r.name + "_sm", startM)
                    .putInt(r.name + "_eh", endH).putInt(r.name + "_em", endM).apply()
                buildCards()
            }, eh, em, false).also { it.setTitle("Available until"); it.show() }
        }, sh, sm, false).also { it.setTitle("Available from"); it.show() }
    }

    private fun buildCards() {
        val container = findViewById<LinearLayout>(R.id.parentalCards)
        container.removeAllViews()
        for (r in rules) container.addView(card(r))
    }

    private fun card(r: Rule): View {
        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL
        val cbg = GradientDrawable(); cbg.cornerRadius = dp(15).toFloat(); cbg.setColor(0xFF0C1838.toInt()); cbg.setStroke(1, 0xFF1C2A52.toInt())
        card.background = cbg; card.setPadding(dp(14), dp(14), dp(14), dp(14))
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.topMargin = dp(12); card.layoutParams = clp

        val top = LinearLayout(this); top.orientation = LinearLayout.HORIZONTAL; top.gravity = Gravity.CENTER_VERTICAL
        val icon = TextView(this); icon.text = r.letter; icon.gravity = Gravity.CENTER; icon.setTextColor(0xFFFFFFFF.toInt()); icon.setTypeface(icon.typeface, Typeface.BOLD); icon.textSize = 16f
        val ibg = GradientDrawable(); ibg.cornerRadius = dp(10).toFloat(); ibg.setColor(r.color); icon.background = ibg
        icon.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        val nameCol = LinearLayout(this); nameCol.orientation = LinearLayout.VERTICAL
        val ncLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); ncLp.leftMargin = dp(11); nameCol.layoutParams = ncLp
        val nm = TextView(this); nm.text = r.name; nm.setTextColor(0xFFF3F7FF.toInt()); nm.setTypeface(nm.typeface, Typeface.BOLD); nm.textSize = 15f
        nameCol.addView(nm)
        val enabled = prefs.getBoolean("enabled_" + r.name, r.defOn)
        val sw = SwitchCompat(this); sw.isChecked = enabled
        sw.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("enabled_" + r.name, checked).apply(); buildCards() }
        top.addView(icon); top.addView(nameCol); top.addView(sw)
        card.addView(top)

        val sh = prefs.getInt(r.name + "_sh", r.defSH); val sm = prefs.getInt(r.name + "_sm", r.defSM)
        val eh = prefs.getInt(r.name + "_eh", r.defEH); val em = prefs.getInt(r.name + "_em", r.defEM)

        if (!enabled) {
            val off = TextView(this)
            off.text = "Turned off — this app is blocked all the time"
            off.setTextColor(0xFF5b6c92.toInt())
            off.textSize = 12f; off.setTypeface(off.typeface, Typeface.BOLD)
            val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); olp.topMargin = dp(11); off.layoutParams = olp
            card.addView(off)
            return card
        }

        // App is allowed. Second, separate choice: no time limit vs. a set window.
        val limited = prefs.getBoolean("limited_" + r.name, false)

        val pills = LinearLayout(this); pills.orientation = LinearLayout.HORIZONTAL
        val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); plp.topMargin = dp(12); pills.layoutParams = plp
        val anyPill = pill("Anytime", !limited) {
            prefs.edit().putBoolean("limited_" + r.name, false).apply(); buildCards()
        }
        val anyLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); anyLp.rightMargin = dp(8); anyPill.layoutParams = anyLp
        val setPill = pill("Set hours", limited) {
            prefs.edit().putBoolean("limited_" + r.name, true).apply()
            if (!prefs.contains(r.name + "_sh")) pickTimes(r) else buildCards()
        }
        pills.addView(anyPill); pills.addView(setPill)
        card.addView(pills)

        val status = TextView(this)
        if (limited) {
            status.text = "🕐 Available  " + fmt(sh, sm) + " – " + fmt(eh, em) + "     ✎ Edit"
            status.setTextColor(0xFF22D3EE.toInt())
            status.setOnClickListener { pickTimes(r) }
        } else {
            status.text = "✓ Available anytime — no time limit"
            status.setTextColor(0xFF4FD08A.toInt())
        }
        status.textSize = 12f; status.setTypeface(status.typeface, Typeface.BOLD)
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); slp.topMargin = dp(10); status.layoutParams = slp
        card.addView(status)

        if (limited) {
            val remove = TextView(this)
            remove.text = "✕  Remove time limit  (make available anytime)"
            remove.setTextColor(0xFFE07A86.toInt())
            remove.textSize = 12f; remove.setTypeface(remove.typeface, Typeface.BOLD)
            val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); rlp.topMargin = dp(8); remove.layoutParams = rlp
            remove.setOnClickListener {
                prefs.edit().putBoolean("limited_" + r.name, false).apply(); buildCards()
            }
            card.addView(remove)
        }
        return card
    }

    private fun pill(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text; tv.textSize = 12f; tv.gravity = Gravity.CENTER
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(dp(16), dp(7), dp(16), dp(7))
        val bg = GradientDrawable(); bg.cornerRadius = dp(20).toFloat()
        if (selected) {
            bg.setColor(0x3322D3EE); bg.setStroke(dp(1), 0xFF22D3EE.toInt()); tv.setTextColor(0xFF22D3EE.toInt())
        } else {
            bg.setColor(0x00000000); bg.setStroke(dp(1), 0xFF2A3A63.toInt()); tv.setTextColor(0xFF6E80A8.toInt())
        }
        tv.background = bg
        tv.setOnClickListener { onClick() }
        return tv
    }
}
