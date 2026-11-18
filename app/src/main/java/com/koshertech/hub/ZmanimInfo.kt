package com.koshertech.hub

import android.content.Context
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Zman(val label: String, val time: String)

object ZmanimInfo {

    // Location — defaults to Brooklyn, overridden by whatever the user sets.
    private var CITY = "Brooklyn, NY"
    private var LAT = 40.6782
    private var LNG = -73.9442
    private const val ELEV = 10.0
    private var TZID = "America/New_York"

    // Load the saved location (call once on startup).
    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("ktloc", Context.MODE_PRIVATE)
        CITY = p.getString("city", CITY) ?: CITY
        LAT = p.getFloat("lat", LAT.toFloat()).toDouble()
        LNG = p.getFloat("lng", LNG.toFloat()).toDouble()
        TZID = p.getString("tz", TZID) ?: TZID
    }

    // Save a new location (used by the Set Location picker).
    fun setLocation(ctx: Context, city: String, lat: Double, lng: Double, tz: String) {
        CITY = city; LAT = lat; LNG = lng; TZID = tz
        ctx.getSharedPreferences("ktloc", Context.MODE_PRIVATE).edit()
            .putString("city", city).putFloat("lat", lat.toFloat())
            .putFloat("lng", lng.toFloat()).putString("tz", tz).apply()
    }

    fun locationName(): String = CITY
    fun lat(): Double = LAT
    fun lng(): Double = LNG
    fun tzId(): String = TZID

    private fun fmt(d: Date?): String {
        if (d == null) return "--"
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone(TZID)
        return sdf.format(d)
    }

    fun hebrewDate(): String {
        return try {
            val jc = JewishCalendar()
            val hdf = HebrewDateFormatter()
            hdf.isHebrewFormat = true
            hdf.format(jc)
        } catch (e: Exception) { "" }
    }

    fun englishDate(): String {
        val sdf = SimpleDateFormat("EEEE · MMMM d", Locale.US)
        return sdf.format(Date())
    }

    // Parsha for the coming/!current Shabbos, or "" if none found.
    fun parsha(): String {
        return try {
            val jc = JewishCalendar()
            val hdf = HebrewDateFormatter()
            hdf.isHebrewFormat = false
            // advance to Shabbos
            var guard = 0
            while (jc.dayOfWeek != 7 && guard < 8) { jc.forward(Calendar.DATE, 1); guard++ }
            val p = hdf.formatParsha(jc)
            if (p.isNullOrBlank()) "" else p
        } catch (e: Exception) { "" }
    }

    fun zmanim(): List<Zman> {
        return try {
            val tz = TimeZone.getTimeZone(TZID)
            val geo = GeoLocation(CITY, LAT, LNG, ELEV, tz)
            val z = ComplexZmanimCalendar(geo)
            z.calendar = Calendar.getInstance(tz)
            listOf(
                Zman("Sunrise", fmt(z.sunrise)),
                Zman("Sof Zman Shma", fmt(z.sofZmanShmaGRA)),
                Zman("Chatzos", fmt(z.chatzos)),
                Zman("Mincha Gedola", fmt(z.minchaGedola)),
                Zman("Shkiah", fmt(z.sunset)),
                Zman("Tzeis", fmt(z.tzais))
            )
        } catch (e: Exception) { emptyList() }
    }

    // Candle lighting for the coming Friday (18 min before shkiah).
    fun candleLighting(): String {
        return try {
            val tz = TimeZone.getTimeZone(TZID)
            val geo = GeoLocation(CITY, LAT, LNG, ELEV, tz)
            val z = ComplexZmanimCalendar(geo)
            val cal = Calendar.getInstance(tz)
            var guard = 0
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY && guard < 8) {
                cal.add(Calendar.DATE, 1); guard++
            }
            z.calendar = cal
            fmt(z.candleLighting)
        } catch (e: Exception) { "--" }
    }

    fun greeting(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(TZID))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        // Shabbos (Saturday until evening) → Good Shabbos
        if (dow == Calendar.SATURDAY && h < 19) return "Good Shabbos"
        if (dow == Calendar.FRIDAY && h >= 15) return "Good Shabbos"
        return when {
            h < 12 -> "Good morning"
            h < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
