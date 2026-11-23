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
                out.add(Entry(o.optString("n"), o.optString("p"), o.optInt("c", 0xFFFFFFFF.toInt())))
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

    // ----- bedtime lock (all apps except Phone) -----
    fun bedtimeOn(ctx: Context) = prefs(ctx).getBoolean("bed_on", false)
    fun bedStart(ctx: Context) = prefs(ctx).getInt("bed_s", 21 * 60)
    fun bedEnd(ctx: Context) = prefs(ctx).getInt("bed_e", 7 * 60)
    fun setBedtime(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("bed_on", on).apply()
    fun setBedTimes(ctx: Context, s: Int, e: Int) = prefs(ctx).edit().putInt("bed_s", s).putInt("bed_e", e).apply()

    // ----- block new installs (on = kids must request apps) -----
    fun blockInstalls(ctx: Context) = prefs(ctx).getBoolean("block_inst", true)
    fun setBlockInstalls(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("block_inst", on).apply()

    // ----- daily screen-time limit in minutes (0 = no limit) -----
    fun dailyLimit(ctx: Context) = prefs(ctx).getInt("limit_min", 0)
    fun setDailyLimit(ctx: Context, m: Int) = prefs(ctx).edit().putInt("limit_min", m).apply()

    private fun nowMin(): Int { val c = Calendar.getInstance(); return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) }
    private fun inWindow(now: Int, s: Int, e: Int) = when { s == e -> true; s < e -> now in s until e; else -> now >= s || now < e }

    // ----- screen time today (needs "Usage access" for the Hub) -----
    fun hasUsageAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) ==
            android.app.AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    private var usageAt = 0L
    private var usageCache: Map<String, Long> = emptyMap()
    // Milliseconds each app was on screen today (cached for a minute).
    fun usageToday(ctx: Context): Map<String, Long> {
        val now = System.currentTimeMillis()
        if (now - usageAt < 60_000) return usageCache
        usageAt = now
        usageCache = try {
            val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            um.queryAndAggregateUsageStats(c.timeInMillis, now).mapValues { it.value.totalTimeInForeground }
        } catch (e: Exception) { emptyMap() }
        return usageCache
    }
    // Total minutes today across the apps on the home screen (Phone not counted).
    fun minutesToday(ctx: Context): Int {
        val u = usageToday(ctx)
        return (apps(ctx).sumOf { u[it.pkg] ?: 0L } / 60000L).toInt()
    }

    // Is this app usable right now?
    fun state(ctx: Context, pkg: String): State {
        if (pkg in neverLock) return State(true, "")
        if (bedtimeOn(ctx) && inWindow(nowMin(), bedStart(ctx), bedEnd(ctx))) return State(false, "Bedtime")
        val lim = dailyLimit(ctx)
        if (lim > 0 && hasUsageAccess(ctx) && minutesToday(ctx) >= lim) return State(false, "Limit reached")
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
