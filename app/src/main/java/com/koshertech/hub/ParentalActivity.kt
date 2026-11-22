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

    private fun pickTimes(e: ParentalRules.Entry) {
        val s0 = ParentalRules.startMin(this, e.pkg); val e0 = ParentalRules.endMin(this, e.pkg)
        TimePickerDialog(this, { _, startH, startM ->
            TimePickerDialog(this, { _, endH, endM ->
                ParentalRules.setTimes(this, e.pkg, startH * 60 + startM, endH * 60 + endM)
                ParentalRules.setLimited(this, e.pkg, true)
                buildCards()
            }, e0 / 60, e0 % 60, false).also { it.setTitle("Available until"); it.show() }
        }, s0 / 60, s0 % 60, false).also { it.setTitle("Available from"); it.show() }
    }

    private fun buildCards() {
        val container = findViewById<LinearLayout>(R.id.parentalCards)
        container.removeAllViews()
        val apps = ParentalRules.apps(this)
        if (apps.isEmpty()) {
            val t = TextView(this); t.text = "Open the KosherTech home screen once so your apps load here."
            t.setTextColor(0xFF8599C0.toInt()); t.textSize = 13f; t.setPadding(0, dp(16), 0, 0)
            container.addView(t); return
        }
        for (e in apps) container.addView(card(e))
    }

    private fun card(e: ParentalRules.Entry): View {
        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL
        val cbg = GradientDrawable(); cbg.cornerRadius = dp(15).toFloat(); cbg.setColor(0xFF0C1838.toInt()); cbg.setStroke(1, 0xFF1C2A52.toInt())
        card.background = cbg; card.setPadding(dp(14), dp(14), dp(14), dp(14))
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.topMargin = dp(12); card.layoutParams = clp

        val top = LinearLayout(this); top.orientation = LinearLayout.HORIZONTAL; top.gravity = Gravity.CENTER_VERTICAL
        val icon = TextView(this); icon.text = e.name.take(1); icon.gravity = Gravity.CENTER; icon.setTextColor(0xFFFFFFFF.toInt()); icon.setTypeface(icon.typeface, Typeface.BOLD); icon.textSize = 16f
        val ibg = GradientDrawable(); ibg.cornerRadius = dp(10).toFloat(); ibg.setColor(e.color); icon.background = ibg
        icon.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        val nameCol = LinearLayout(this); nameCol.orientation = LinearLayout.VERTICAL
        val ncLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); ncLp.leftMargin = dp(11); nameCol.layoutParams = ncLp
        val nm = TextView(this); nm.text = e.name; nm.setTextColor(0xFFF3F7FF.toInt()); nm.setTypeface(nm.typeface, Typeface.BOLD); nm.textSize = 15f
        nameCol.addView(nm)
        val st = ParentalRules.state(this, e.pkg)
        val now = TextView(this); now.textSize = 11f
        if (st.allowed) { now.text = "● Open right now"; now.setTextColor(0xFF4FD08A.toInt()) }
        else { now.text = "● Locked right now"; now.setTextColor(0xFFE07A86.toInt()) }
        nameCol.addView(now)
        val enabled = ParentalRules.isEnabled(this, e.pkg)
        val sw = SwitchCompat(this); sw.isChecked = enabled
        sw.setOnCheckedChangeListener { _, checked -> ParentalRules.setEnabled(this, e.pkg, checked); buildCards() }
        top.addView(icon); top.addView(nameCol); top.addView(sw)
        card.addView(top)

        if (!enabled) {
            val off = TextView(this)
            off.text = "Turned off — this app is blocked all the time"
            off.setTextColor(0xFF5b6c92.toInt())
            off.textSize = 12f; off.setTypeface(off.typeface, Typeface.BOLD)
            val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); olp.topMargin = dp(11); off.layoutParams = olp
            card.addView(off)
            return card
        }

        val limited = ParentalRules.isLimited(this, e.pkg)
        val pills = LinearLayout(this); pills.orientation = LinearLayout.HORIZONTAL
        val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); plp.topMargin = dp(12); pills.layoutParams = plp
        val anyPill = pill("Anytime", !limited) { ParentalRules.setLimited(this, e.pkg, false); buildCards() }
        val anyLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); anyLp.rightMargin = dp(8); anyPill.layoutParams = anyLp
        val setPill = pill("Set hours", limited) {
            if (!ParentalRules.hasTimes(this, e.pkg)) pickTimes(e)
            else { ParentalRules.setLimited(this, e.pkg, true); buildCards() }
        }
        pills.addView(anyPill); pills.addView(setPill)
        card.addView(pills)

        val status = TextView(this)
        if (limited) {
            status.text = "🕐 Available  " + ParentalRules.fmt(ParentalRules.startMin(this, e.pkg)) + " – " +
                ParentalRules.fmt(ParentalRules.endMin(this, e.pkg)) + "     ✎ Edit"
            status.setTextColor(0xFF22D3EE.toInt())
            status.setOnClickListener { pickTimes(e) }
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
            remove.setOnClickListener { ParentalRules.setLimited(this, e.pkg, false); buildCards() }
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
