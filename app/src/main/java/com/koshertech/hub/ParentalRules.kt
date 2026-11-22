package com.koshertech.hub

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// One place that decides whether an app may be opened right now.
// Used by the home screen (to gray out locked apps) and by Parental Controls (to edit rules).
// Rules are keyed by package name, so every app on the home screen — built-in or pushed
// from the dashboard — gets its own rule automatically.
object ParentalRules {

    data class Entry(val name: String, val pkg: String, val color: Int)
    data class State(val allowed: Boolean, val label: String)

    // Apps that can never be locked (calling must always work, e.g. emergencies).
    private val neverLock = setOf("com.google.android.dialer")

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ktparental", Context.MODE_PRIVATE)

    // Called by the home screen whenever its app list changes (startup + every dashboard sync).
    // New apps show up in Parental Controls automatically, allowed anytime until a parent changes it.
    fun saveApps(ctx: Context, apps: List<AppItem>) {
        val arr = JSONArray()
        for (a in apps) {
            if (a.packageName in neverLock) continue
            arr.put(JSONObject().put("n", a.name).put("p", a.packageName).put("c", a.color))
        }
        val p = prefs(ctx)
        p.edit().putString("apps", arr.toString()).apply()
        migrateOld(p, apps)
    }

    fun apps(ctx: Context): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            val arr = JSONArray(prefs(ctx).getString("apps", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Entry(o.optString("n"), o.optString("p"), o.optInt("c", 0xFF22D3EE.toInt())))
            }
        } catch (e: Exception) {}
        return out
    }

    // ----- per-app settings (keyed by package) -----
    fun isEnabled(ctx: Context, pkg: String) = prefs(ctx).getBoolean("on_$pkg", true)
    fun isLimited(ctx: Context, pkg: String) = prefs(ctx).getBoolean("lim_$pkg", false)
    fun startMin(ctx: Context, pkg: String) = prefs(ctx).getInt("start_$pkg", 15 * 60)
    fun endMin(ctx: Context, pkg: String) = prefs(ctx).getInt("end_$pkg", 20 * 60)
    fun hasTimes(ctx: Context, pkg: String) = prefs(ctx).contains("start_$pkg")

    fun setEnabled(ctx: Context, pkg: String, v: Boolean) = prefs(ctx).edit().putBoolean("on_$pkg", v).apply()
    fun setLimited(ctx: Context, pkg: String, v: Boolean) = prefs(ctx).edit().putBoolean("lim_$pkg", v).apply()
    fun setTimes(ctx: Context, pkg: String, start: Int, end: Int) =
        prefs(ctx).edit().putInt("start_$pkg", start).putInt("end_$pkg", end).apply()

    // Is this app usable right now?
    fun state(ctx: Context, pkg: String): State {
        if (pkg in neverLock) return State(true, "")
        if (!isEnabled(ctx, pkg)) return State(false, "Blocked")
        if (!isLimited(ctx, pkg)) return State(true, "")
        val s = startMin(ctx, pkg); val e = endMin(ctx, pkg)
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val inside = when {
            s == e -> true                       // same start/end = whole day
            s < e -> now in s until e            // e.g. 3 PM – 8 PM
            else -> now >= s || now < e          // overnight, e.g. 8 PM – 7 AM
        }
        return if (inside) State(true, "") else State(false, "Opens " + fmt(s))
    }

    fun fmt(min: Int): String {
        val h = min / 60; val m = min % 60
        val ampm = if (h < 12) "AM" else "PM"; var hh = h % 12; if (hh == 0) hh = 12
        return String.format("%d:%02d %s", hh, m, ampm)
    }

    // Older versions saved rules by app NAME for Zemer/24Six/Zing. Carry those settings over once.
    private fun migrateOld(p: SharedPreferences, apps: List<AppItem>) {
        if (p.getBoolean("migrated_v2", false)) return
        val ed = p.edit()
        for (a in apps) {
            val n = a.name; val pkg = a.packageName
            if (p.contains("enabled_$n")) ed.putBoolean("on_$pkg", p.getBoolean("enabled_$n", true))
            if (p.contains("limited_$n")) ed.putBoolean("lim_$pkg", p.getBoolean("limited_$n", false))
            if (p.contains("${n}_sh")) {
                ed.putInt("start_$pkg", p.getInt("${n}_sh", 15) * 60 + p.getInt("${n}_sm", 0))
                ed.putInt("end_$pkg", p.getInt("${n}_eh", 20) * 60 + p.getInt("${n}_em", 0))
            }
        }
        ed.putBoolean("migrated_v2", true).apply()
    }
}
