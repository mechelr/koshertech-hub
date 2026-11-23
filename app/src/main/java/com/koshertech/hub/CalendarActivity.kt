package com.koshertech.hub

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        findViewById<TextView>(R.id.calParsha).text = ZmanimInfo.parsha().ifBlank { "—" }
        findViewById<TextView>(R.id.calCandle).text = ZmanimInfo.candleLighting()

        buildMonth()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildMonth() {
        val hdf = HebrewDateFormatter()
        hdf.isHebrewFormat = true

        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH)
        val todayDom = now.get(Calendar.DAY_OF_MONTH)

        // header
        try {
            val mid = Calendar.getInstance(); mid.set(year, month, 15)
            val jd = JewishDate(mid)
            findViewById<TextView>(R.id.calMonth).text = hdf.formatMonth(jd)
        } catch (e: Exception) {}
        findViewById<TextView>(R.id.calMonthEn).text =
            SimpleDateFormat("MMMM yyyy", Locale.US).format(now.time)

        val first = Calendar.getInstance(); first.set(year, month, 1)
        val firstDow = first.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)

        val grid = findViewById<LinearLayout>(R.id.calGrid)
        grid.removeAllViews()

        var day = 1
        var started = false
        while (day <= daysInMonth) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            for (col in 1..7) {
                if (!started && col < firstDow) {
                    row.addView(emptyCell()); continue
                }
                started = true
                if (day > daysInMonth) { row.addView(emptyCell()); continue }
                row.addView(dayCell(year, month, day, col == 7, day == todayDom))
                day++
            }
            grid.addView(row)
        }
    }

    private fun emptyCell(): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
        return v
    }

    private fun dayCell(year: Int, month: Int, day: Int, isSaturday: Boolean, isToday: Boolean): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(0, dp(46), 1f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        cell.layoutParams = lp

        val bg = android.graphics.drawable.GradientDrawable()
        bg.cornerRadius = dp(9).toFloat()
        bg.setColor(if (isSaturday) 0xFF1A1A1A.toInt() else 0xFF161616.toInt())
        if (isToday) bg.setStroke(dp(2), 0xFFFFFFFF.toInt())
        else bg.setStroke(1, 0xFF1A1A1A.toInt())
        cell.background = bg

        var heb = ""
        try {
            val c = Calendar.getInstance(); c.set(year, month, day)
            val jd = JewishDate(c)
            val hdf = HebrewDateFormatter()
            heb = hdf.formatHebrewNumber(jd.jewishDayOfMonth)
        } catch (e: Exception) {}

        val h = TextView(this)
        h.text = heb
        h.setTextColor(0xFFF5F5F5.toInt())
        h.setTypeface(h.typeface, Typeface.BOLD)
        h.textSize = 13f
        val en = TextView(this)
        en.text = day.toString()
        en.setTextColor(0xFF8C8C8C.toInt())
        en.textSize = 8f
        cell.addView(h)
        cell.addView(en)
        return cell
    }
}
