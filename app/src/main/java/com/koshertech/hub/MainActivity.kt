package com.koshertech.hub

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // Feedback (requests / loopholes / ratings) is emailed here via FormSubmit.
    // The address is never shown anywhere in the app.
    private val FEEDBACK_ENDPOINT = "https://script.google.com/macros/s/AKfycbzeh0yW4sqHXuJZXsg31xZFDXm4q7crX-MIWBCE4bEDyLg7l-yY4askgSfZd9II7_wG/exec"

    // ===== YOUR APPS =====
    // apkUrl: a KosherTech-hosted link to the approved APK. When set, Install downloads
    // and installs it directly (no Play Store). Add the real Zemer/24Six/Zing links here.
    private fun firstInstalled(vararg pkgs: String): String =
        pkgs.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null } ?: pkgs[0]

    private val allApps: List<AppItem> by lazy { listOf(
        AppItem("Claude", "AI assistant", "com.anthropic.claude", 0xFFF97316.toInt(), ""),
        AppItem("Zing", "Music · Streaming", "fm.jewishmusic.application", 0xFF8B5CF6.toInt(), ""),
        AppItem("Zemer", "Music · Jewish songs", "com.jtech.zemer", 0xFFF59E0B.toInt(),
            "https://github.com/mechelr/koshertech-hub/releases/download/latest/zemer.apk"),
        AppItem("24Six", "Music · Jewish streaming", "app.tfs.prod", 0xFFE11D48.toInt(), ""),
        AppItem("Phone", "Make a call", "com.google.android.dialer", 0xFF10B981.toInt(), ""),
        AppItem("Camera", "Take photos", "org.fossify.camera", 0xFF14B8A6.toInt(), ""),
        AppItem("Clock", "Alarms & timers", firstInstalled("com.google.android.deskclock", "com.android.deskclock"), 0xFF0EA5E9.toInt(), ""),
        AppItem("Calculator", "Math", firstInstalled("com.google.android.calculator", "com.android.calculator2"), 0xFF84CC16.toInt(), ""),
        AppItem("Calendar", "Hebrew calendar", "com.koshertech.hub.calendar", 0xFFEC4899.toInt(), "", "calendar"),
        AppItem("Settings", "Controlled settings app", "com.koshertech.settings", 0xFF64748B.toInt(),
            "https://github.com/mechelr/koshertech-hub/releases/download/settings/KosherTechSettings.apk"),
        AppItem("Contacts", "Phone book", "com.google.android.contacts", 0xFF2563EB.toInt(), ""),
        AppItem("Gallery", "Your photos", "org.fossify.gallery", 0xFF6366F1.toInt(), "")
    ) }

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
            "1 device  ·  approved apps  ·  email support", false, 0xFF2563EB.toInt()),
        Plan("Family", "$24.99", "/mo",
            "Up to 4 devices  ·  remote management  ·  priority support", true, 0xFF7C3AED.toInt()),
        Plan("Premium", "$34.99", "/mo",
            "Unlimited devices  ·  custom app approvals  ·  24/7 support", false, 0xFFF97316.toInt()),
        Plan("Yearly", "$299", "/yr",
            "All Premium features  ·  2 months free", false, 0xFF10B981.toInt())
    )

    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ZmanimInfo.load(this)

        val recycler = findViewById<RecyclerView>(R.id.appList)
        recycler.layoutManager = GridLayoutManager(this, 4)
        adapter = AppAdapter(allApps) { app -> openOrInstall(app) }
        recycler.adapter = adapter
        ParentalRules.saveApps(this, allApps)

        findViewById<EditText>(R.id.searchBox).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<View>(R.id.requestButton).setOnClickListener { showRequestDialog() }
        findViewById<View>(R.id.loopholeButton).setOnClickListener { showLoopholeDialog() }
        findViewById<View>(R.id.rateButton).setOnClickListener { showRateDialog() }
        findViewById<View>(R.id.buyPlanButton).setOnClickListener { showBuyPlanDialog() }
        findViewById<View>(R.id.updatePaymentButton).setOnClickListener { showPaymentDialog(null) }
        findViewById<View>(R.id.tileChat).setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        findViewById<View>(R.id.versionLine).setOnClickListener { setChecking(); checkForUpdate() }

        setupPager()
        setupParentalPage()

        findViewById<View>(R.id.tileAutomations).setOnClickListener { startActivity(Intent(this, AutomationsActivity::class.java)) }
        findViewById<View>(R.id.tileHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.tileMirror).setOnClickListener { startActivity(Intent(this, MirrorActivity::class.java)) }
        findViewById<View>(R.id.tileAppLock).setOnClickListener { showAppLock() }

        maybeShowMirror()
        findViewById<View>(R.id.chatFab).setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }

        populateZmanim()
        fetchWeather()
        initUpdateBar()
        startClock()

        // Tap the location label (on the zmanim card) to change city for BOTH zmanim and weather.
        findViewById<View>(R.id.zmanimLocRow).setOnClickListener { showLocationDialog() }
        findViewById<TextView>(R.id.zmanimLoc).setOnClickListener { showLocationDialog() }
        findViewById<TextView>(R.id.zmanimDay).setOnClickListener {
            zmanimOffset = if (zmanimOffset == 0) 1 else 0; zmanimManual = true; populateZmanim()
        }

        deviceId = ensureDeviceId()
        syncBackend()
    }

    // ===== MORNING MIRROR: pop up on first launch and once a day =====
    private fun maybeShowMirror() {
        val p = getSharedPreferences("ktmirror", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (p.getString("lastShown", "") != today) {
            p.edit().putString("lastShown", today).apply()
            findViewById<View>(android.R.id.content).post {
                startActivity(Intent(this, MirrorActivity::class.java))
            }
        }
    }

    // ===== APP LOCK: require the parent PIN to open chosen apps =====
    private fun isLocked(pkg: String): Boolean {
        val locked = getSharedPreferences("ktlock", Context.MODE_PRIVATE).getStringSet("locked", emptySet()) ?: emptySet()
        return locked.contains(pkg)
    }

    private fun pinField(): EditText {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return et
    }

    private fun promptPin(onOk: () -> Unit) {
        val pin = getSharedPreferences("ktparental", Context.MODE_PRIVATE).getString("pin", null)
        if (pin == null) { onOk(); return }  // no PIN set -> nothing to enforce
        val et = pinField()
        AlertDialog.Builder(this).setTitle("Enter PIN to open")
            .setView(et)
            .setPositiveButton("Open") { _, _ ->
                if (et.text.toString() == pin) onOk()
                else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showAppLock() {
        val pp = getSharedPreferences("ktparental", Context.MODE_PRIVATE)
        if (pp.getString("pin", null) == null) {
            val et = pinField()
            AlertDialog.Builder(this).setTitle("Create a 4-digit PIN for App Lock")
                .setView(et)
                .setPositiveButton("Save") { _, _ ->
                    val p = et.text.toString()
                    if (p.length >= 4) { pp.edit().putString("pin", p).apply(); openLockChooser() }
                    else Toast.makeText(this, "Use at least 4 digits", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null).show()
            return
        }
        promptPin { openLockChooser() }
    }

    private fun openLockChooser() {
        val list = adapter.allApps()
        val names = list.map { it.name }.toTypedArray()
        val pkgs = list.map { it.packageName }
        val lp = getSharedPreferences("ktlock", Context.MODE_PRIVATE)
        val locked = HashSet(lp.getStringSet("locked", emptySet()) ?: emptySet())
        val checked = BooleanArray(pkgs.size) { locked.contains(pkgs[it]) }
        AlertDialog.Builder(this)
            .setTitle("Lock apps (ask PIN to open)")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) locked.add(pkgs[which]) else locked.remove(pkgs[which])
            }
            .setPositiveButton("Save") { _, _ ->
                lp.edit().putStringSet("locked", locked).apply()
                Toast.makeText(this, "App lock saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ===== BACKEND SYNC (device identity, remote app push, request status, announcements) =====
    private lateinit var deviceId: String

    private fun ensureDeviceId(): String {
        val p = getSharedPreferences("ktdevice", Context.MODE_PRIVATE)
        var id = p.getString("deviceId", null)
        if (id == null) { id = java.util.UUID.randomUUID().toString(); p.edit().putString("deviceId", id).apply() }
        return id
    }

    private fun syncBackend() {
        val ep = FEEDBACK_ENDPOINT
        if (ep.isBlank() || ep.contains("PLACEHOLDER")) return
        Thread {
            try {
                val reg = JSONObject().apply { put("action", "register"); put("deviceId", deviceId); put("name", Build.MODEL); put("model", Build.MODEL) }
                val c = URL(ep).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json")
                c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = true; c.doOutput = true
                c.outputStream.use { it.write(reg.toString().toByteArray(Charsets.UTF_8)) }; c.responseCode; c.disconnect()
            } catch (e: Exception) {}
            try {
                val conn = URL(ep + "?action=sync&deviceId=" + deviceId).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000; conn.instanceFollowRedirects = true
                val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()
                val o = JSONObject(text)
                runOnUiThread { applySync(o.optJSONArray("apps"), o.optJSONArray("requests"), o.optJSONArray("announcements")) }
            } catch (e: Exception) { /* offline / blocked — keep the built-in apps */ }
        }.start()
    }

    private fun applySync(apps: JSONArray?, reqs: JSONArray?, anns: JSONArray?) {
        if (apps != null) {
            val merged = allApps.toMutableList()
            val have = merged.map { it.packageName }.toHashSet()
            for (i in 0 until apps.length()) {
                val a = apps.getJSONObject(i)
                val pkg = a.optString("packageName", "")
                if (pkg.isBlank() || have.contains(pkg)) continue
                var cs = a.optString("color", "#FFFFFF"); if (!cs.startsWith("#")) cs = "#$cs"
                val color = try { android.graphics.Color.parseColor(cs) } catch (e: Exception) { 0xFFFFFFFF.toInt() }
                merged.add(AppItem(a.optString("name", "App"), a.optString("category", ""), pkg, color, a.optString("apkUrl", "")))
                have.add(pkg)
            }
            adapter.setApps(merged)
            ParentalRules.saveApps(this, merged)   // new pushed apps show up in Parental Controls
        }
        val dp = getSharedPreferences("ktdismiss", Context.MODE_PRIVATE)

        // ----- request statuses (each row dismissible by the customer) -----
        val dreq = HashSet(dp.getStringSet("req", emptySet()) ?: emptySet())
        val title = findViewById<TextView>(R.id.requestStatusTitle)
        val list = findViewById<LinearLayout>(R.id.requestStatusList)
        list.removeAllViews()
        var shownReq = 0
        var waiting = 0
        lastRequests = reqs
        if (reqs != null) {
            for (i in 0 until reqs.length()) {
                val r = reqs.getJSONObject(i)
                val status = r.optString("status", "Pending")
                val name = r.optString("appName", "")
                if (status.equals("Pending", true)) waiting++
                val key = name + "||" + status
                if (dreq.contains(key)) continue
                val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
                val tv = TextView(this)
                tv.text = name + "   ·   " + statusLabel(status)
                tv.setTextColor(0xFF1E1B4B.toInt()); tv.setTypeface(tv.typeface, Typeface.BOLD)
                tv.textSize = 15f
                row.setBackgroundResource(R.drawable.bg_card); row.setPadding(dpx(16), dpx(12), dpx(10), dpx(12))
                tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val x = TextView(this); x.text = "✕"; x.setTextColor(0xFF64748B.toInt()); x.textSize = 15f
                x.setPadding(dpx(12), dpx(4), dpx(2), dpx(4))
                x.setOnClickListener {
                    dreq.add(key); dp.edit().putStringSet("req", dreq).apply()
                    list.removeView(row); if (list.childCount == 0) title.visibility = View.GONE
                }
                row.addView(tv); row.addView(x)
                val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); rlp.topMargin = dpx(8); row.layoutParams = rlp
                list.addView(row); shownReq++
            }
        }
        title.visibility = if (shownReq > 0) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.reqCount).text = waiting.toString()

        // ----- announcements (each dismissible by the customer) -----
        val dann = HashSet(dp.getStringSet("ann", emptySet()) ?: emptySet())
        val annBox = findViewById<LinearLayout>(R.id.announcementList)
        annBox.removeAllViews()
        var shownAnn = 0
        if (anns != null) {
            for (i in 0 until anns.length()) {
                val msg = anns.getString(i)
                if (dann.contains(msg)) continue
                val card = LinearLayout(this); card.orientation = LinearLayout.HORIZONTAL; card.gravity = Gravity.CENTER_VERTICAL
                card.setBackgroundResource(R.drawable.bg_update); card.setPadding(dpx(14), dpx(12), dpx(12), dpx(12))
                val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.bottomMargin = dpx(8); card.layoutParams = clp
                val tv = TextView(this); tv.text = "📢  " + msg; tv.setTextColor(0xFF1E1B4B.toInt()); tv.textSize = 14f; tv.setTypeface(tv.typeface, Typeface.BOLD)
                tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val x = TextView(this); x.text = "✕"; x.setTextColor(0xFF64748B.toInt()); x.textSize = 15f
                x.setPadding(dpx(12), 0, dpx(2), 0)
                x.setOnClickListener {
                    dann.add(msg); dp.edit().putStringSet("ann", dann).apply()
                    annBox.removeView(card); if (annBox.childCount == 0) annBox.visibility = View.GONE
                }
                card.addView(tv); card.addView(x)
                annBox.addView(card); shownAnn++
            }
        }
        annBox.visibility = if (shownAnn > 0) View.VISIBLE else View.GONE
    }

    // ===== LOCATION PICKER (shared by zmanim + weather) =====
    private fun showLocationDialog() {
        val input = EditText(this)
        input.hint = "City or 5-digit ZIP (e.g. Lakewood or 08701)"
        input.setPadding(dpx(16), dpx(16), dpx(16), dpx(16))
        AlertDialog.Builder(this)
            .setTitle("Set location")
            .setMessage("Used for both the Zmanim and the Weather.\nCurrently: " + ZmanimInfo.locationName())
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) resolveAndSaveLocation(q)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 10000; conn.instanceFollowRedirects = true
        val t = conn.inputStream.bufferedReader().readText(); conn.disconnect(); return t
    }

    private fun resolveTz(lat: Double, lng: Double): String {
        return try {
            val o = JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&timezone=auto&forecast_days=1"))
            o.optString("timezone", "")
        } catch (e: Exception) { "" }
    }

    private fun resolveAndSaveLocation(query: String) {
        Toast.makeText(this, "Looking up \"$query\"…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                var name = ""; var lat = 0.0; var lng = 0.0; var tz = ""
                val q = query.trim()
                // 1) Open-Meteo geocoding (cities, towns worldwide; returns timezone)
                val gurl = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    URLEncoder.encode(q, "UTF-8") + "&count=1&language=en&format=json"
                val arr = JSONObject(httpGet(gurl)).optJSONArray("results")
                if (arr != null && arr.length() > 0) {
                    val r = arr.getJSONObject(0)
                    lat = r.getDouble("latitude"); lng = r.getDouble("longitude")
                    tz = r.optString("timezone", "")
                    val base = r.optString("name", ""); val admin1 = r.optString("admin1", "")
                    name = if (admin1.isNotBlank()) "$base, $admin1" else base
                } else if (q.matches(Regex("\\d{5}"))) {
                    // 2) US ZIP fallback via Zippopotam
                    val places = JSONObject(httpGet("https://api.zippopotam.us/us/$q")).getJSONArray("places").getJSONObject(0)
                    lat = places.getString("latitude").toDouble()
                    lng = places.getString("longitude").toDouble()
                    name = places.getString("place name") + ", " + places.getString("state abbreviation")
                } else throw Exception("not found")

                if (tz.isBlank()) tz = resolveTz(lat, lng)
                if (tz.isBlank()) tz = "America/New_York"

                ZmanimInfo.setLocation(this, name, lat, lng, tz)
                runOnUiThread {
                    Toast.makeText(this, "Location set to " + name, Toast.LENGTH_LONG).show()
                    populateZmanim()
                    fetchWeather()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Couldn't find that. Try a city name or a 5-digit ZIP.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ===== WEATHER (display only — no taps, goes nowhere) =====
    // Uses the free Open-Meteo API (no key). Same location as the zmanim card.
    private fun fetchWeather() = fetchWeather(0)

    private fun fetchWeather(attempt: Int) {
        if (attempt == 0) {
            findViewById<TextView>(R.id.weatherDesc).text = "Loading weather…"
            findViewById<TextView>(R.id.weatherIcon).text = "⏳"
        }
        Thread {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=" + ZmanimInfo.lat() +
                    "&longitude=" + ZmanimInfo.lng() +
                    "&current=temperature_2m,weather_code" +
                    "&hourly=temperature_2m&forecast_hours=6" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&temperature_unit=fahrenheit&timezone=auto&forecast_days=4"
                val o = JSONObject(httpGet(url))
                val cur = o.getJSONObject("current")
                val nowTemp = Math.round(cur.getDouble("temperature_2m")).toInt()
                val nowCode = cur.optInt("weather_code", 0)
                val daily = o.getJSONObject("daily")
                val times = daily.getJSONArray("time")
                val codes = daily.getJSONArray("weather_code")
                val maxs = daily.getJSONArray("temperature_2m_max")
                val mins = daily.getJSONArray("temperature_2m_min")
                val hiToday = Math.round(maxs.getDouble(0)).toInt(); val loToday = Math.round(mins.getDouble(0)).toInt()
                val days = ArrayList<Triple<String, String, String>>()
                var i = 1
                while (i < times.length() && days.size < 3) {
                    val d = LocalDate.parse(times.getString(i))
                    days.add(Triple(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), emojiFor(codes.getInt(i)),
                        Math.round(maxs.getDouble(i)).toString() + "° / " + Math.round(mins.getDouble(i)) + "°"))
                    i++
                }
                val hours = ArrayList<Pair<String, String>>()
                val hourly = o.optJSONObject("hourly")
                if (hourly != null) {
                    val ht = hourly.getJSONArray("time"); val hv = hourly.getJSONArray("temperature_2m")
                    var k = 1
                    while (k < ht.length() && hours.size < 4) {
                        val hr = ht.getString(k).substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
                        val label = when { hr == 0 -> "12 AM"; hr < 12 -> "$hr AM"; hr == 12 -> "12 PM"; else -> "${hr - 12} PM" }
                        hours.add(Pair(label, Math.round(hv.getDouble(k)).toString() + "°")); k++
                    }
                }
                runOnUiThread { showWeather(nowTemp, nowCode, hiToday, loToday, hours, days) }
            } catch (e: Exception) {
                // No connection or blocked by the device filter — retry a couple of times, then say so.
                if (attempt < 2) {
                    findViewById<View>(R.id.weatherCard).postDelayed({ fetchWeather(attempt + 1) }, 6000)
                } else {
                    runOnUiThread {
                        findViewById<TextView>(R.id.weatherNow).text = "—"
                        findViewById<TextView>(R.id.weatherIcon).text = "🌡️"
                        findViewById<TextView>(R.id.weatherDesc).text = "Weather unavailable — no connection"
                        findViewById<LinearLayout>(R.id.weatherHours).removeAllViews()
                        findViewById<LinearLayout>(R.id.weatherDays).removeAllViews()
                    }
                }
            }
        }.start()
    }

    private fun showWeather(nowTemp: Int, nowCode: Int, hi: Int, lo: Int,
                            hours: List<Pair<String, String>>, days: List<Triple<String, String, String>>) {
        findViewById<TextView>(R.id.weatherNow).text = "$nowTemp°"
        findViewById<TextView>(R.id.weatherDesc).text = descFor(nowCode) + " · H $hi° L $lo°"
        findViewById<TextView>(R.id.weatherIcon).text = emojiFor(nowCode)
        val hrow = findViewById<LinearLayout>(R.id.weatherHours); hrow.removeAllViews()
        for ((k, h) in hours.withIndex()) {
            val cell = LinearLayout(this); cell.orientation = LinearLayout.VERTICAL; cell.gravity = Gravity.CENTER
            cell.setBackgroundResource(R.drawable.bg_hour); cell.setPadding(0, dpx(12), 0, dpx(12))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); if (k > 0) lp.marginStart = dpx(8); cell.layoutParams = lp
            val t1 = TextView(this); t1.text = h.first; t1.setTextColor(0xFFFFFFFF.toInt()); t1.textSize = 15f; t1.setTypeface(t1.typeface, Typeface.BOLD); t1.gravity = Gravity.CENTER
            val t2 = TextView(this); t2.text = h.second; t2.setTextColor(0xFFFFFFFF.toInt()); t2.textSize = 17f; t2.setTypeface(t2.typeface, Typeface.BOLD); t2.gravity = Gravity.CENTER
            cell.addView(t1); cell.addView(t2); hrow.addView(cell)
        }
        val drow = findViewById<LinearLayout>(R.id.weatherDays); drow.removeAllViews()
        for (d in days) {
            val tv = TextView(this); tv.text = d.first + " " + d.second + " " + d.third
            tv.setTextColor(0xFFDBEAFE.toInt()); tv.textSize = 13f; tv.setTypeface(tv.typeface, Typeface.BOLD); tv.gravity = Gravity.CENTER
            tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            drow.addView(tv)
        }
        findViewById<View>(R.id.weatherCard).visibility = View.VISIBLE
    }

    private fun dpx(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun emojiFor(code: Int): String = when (code) {
        0 -> "☀️"; 1, 2 -> "🌤️"; 3 -> "☁️"; 45, 48 -> "🌫️"
        in 51..57 -> "🌦️"; in 61..67 -> "🌧️"; in 71..77 -> "🌨️"
        in 80..82 -> "🌧️"; in 85..86 -> "🌨️"; in 95..99 -> "⛈️"; else -> "🌡️"
    }

    private fun descFor(code: Int): String = when (code) {
        0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"
        in 80..82 -> "Showers"; in 85..86 -> "Snow showers"; in 95..99 -> "Thunderstorm"; else -> "—"
    }

    // ===== UPDATE CHECKER =====
    // On launch, check version.json published with each release. If a newer build
    // exists, show the banner; tapping it downloads and installs the new APK.
    private val VERSION_URL = "https://github.com/mechelr/koshertech-hub/releases/download/latest/version.json"
    private var pendingApkUrl: String? = null

    private fun installedVersionCode(): Int {
        return try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
        } catch (e: Exception) { 1 }
    }

    private fun installedVersionName(): String {
        return try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
    }

    private fun initUpdateBar() {
        findViewById<TextView>(R.id.updateSub).text = "Version " + installedVersionName()
        findViewById<TextView>(R.id.versionLine).text = "Version " + installedVersionName() + "  ·  Check for updates"
        findViewById<View>(R.id.updateBanner).setOnClickListener {
            val url = pendingApkUrl
            if (url != null) downloadAndInstall(url)
            else { setChecking(); checkForUpdate() }
        }
        checkForUpdate()
    }

    private fun setChecking() {
        findViewById<TextView>(R.id.versionLine).text = "Checking for updates…"
        findViewById<TextView>(R.id.updateIcon).text = "↻"
        findViewById<TextView>(R.id.updateTitle).text = "Checking for updates…"
        findViewById<View>(R.id.updateAction).visibility = View.GONE
    }

    private fun checkForUpdate() {
        Thread {
            try {
                val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = true
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val obj = JSONObject(text)
                val remoteCode = obj.optInt("versionCode", 0)
                val remoteName = obj.optString("versionName", "")
                val apkUrl = obj.optString("url", "")
                val notes = obj.optString("notes", "")
                runOnUiThread {
                    if (remoteCode > installedVersionCode() && apkUrl.isNotBlank()) setUpdateAvailable(remoteName, apkUrl, notes)
                    else { setUpToDate(); maybeShowWhatsNew(remoteCode, notes) }
                }
            } catch (e: Exception) {
                runOnUiThread { setCheckError() }
            }
        }.start()
    }

    private fun setUpdateAvailable(versionName: String, apkUrl: String, notes: String) {
        pendingApkUrl = apkUrl
        findViewById<TextView>(R.id.updateIcon).text = "↑"
        findViewById<TextView>(R.id.updateTitle).text = "Update available — tap to install"
        findViewById<TextView>(R.id.updateSub).text = when {
            notes.isNotBlank() -> notes
            versionName.isBlank() -> "Tap to update now"
            else -> "Version " + versionName + " — tap to update"
        }
        findViewById<View>(R.id.updateAction).visibility = View.VISIBLE
        findViewById<View>(R.id.updateBanner).visibility = View.VISIBLE
    }

    // After the user updates and opens the app, show once what the new version improved.
    private fun maybeShowWhatsNew(remoteCode: Int, notes: String) {
        if (notes.isBlank() || remoteCode != installedVersionCode()) return
        val p = getSharedPreferences("ktmain", Context.MODE_PRIVATE)
        if (installedVersionCode() > p.getInt("whatsnewShown", 0)) {
            p.edit().putInt("whatsnewShown", installedVersionCode()).apply()
            AlertDialog.Builder(this)
                .setTitle("What's new in v" + installedVersionName())
                .setMessage(notes)
                .setPositiveButton("Got it", null)
                .show()
        }
    }

    private fun setUpToDate() {
        pendingApkUrl = null
        findViewById<View>(R.id.updateBanner).visibility = View.GONE
        findViewById<TextView>(R.id.versionLine).text = "Version " + installedVersionName() + "  ·  You're up to date ✓"
        findViewById<TextView>(R.id.updateIcon).text = "✓"
        findViewById<TextView>(R.id.updateTitle).text = "You're on the latest version"
        findViewById<TextView>(R.id.updateSub).text = "Version " + installedVersionName() + " · tap to check again"
        findViewById<View>(R.id.updateAction).visibility = View.GONE
    }

    private fun setCheckError() {
        pendingApkUrl = null
        findViewById<View>(R.id.updateBanner).visibility = View.GONE
        findViewById<TextView>(R.id.versionLine).text = "Version " + installedVersionName() + "  ·  Check for updates"
        findViewById<TextView>(R.id.updateIcon).text = "↻"
        findViewById<TextView>(R.id.updateTitle).text = "Tap to check for updates"
        findViewById<TextView>(R.id.updateSub).text = "Version " + installedVersionName()
        findViewById<View>(R.id.updateAction).visibility = View.GONE
    }

    private fun downloadAndInstall(apkUrl: String) {
        Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                val apk = File(getExternalFilesDir(null), "KosherTechHub-update.apk")
                conn.inputStream.use { input -> FileOutputStream(apk).use { out -> input.copyTo(out) } }
                conn.disconnect()
                runOnUiThread { launchInstall(apk) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Update failed: " + e.message, Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun launchInstall(apk: File) {
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", apk)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open installer: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    // Re-check parental time windows every 30s so tiles gray out / unlock on time.
    private val parentalTick = android.os.Handler(android.os.Looper.getMainLooper())
    private val parentalRun = object : Runnable {
        override fun run() { adapter.notifyDataSetChanged(); refreshScreenTime(); parentalTick.postDelayed(this, 30_000) }
    }

    override fun onPause() {
        super.onPause()
        parentalTick.removeCallbacks(parentalRun)
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
        refreshScreenTime(); refreshParentalRows(); tickClock()
        parentalTick.removeCallbacks(parentalRun)
        parentalTick.postDelayed(parentalRun, 30_000)
        // Re-sync on every foreground so dashboard changes (approved requests,
        // pushed apps, announcements) show up without a full restart.
        if (::deviceId.isInitialized) syncBackend()
    }

    private var zmanimOffset = 0
    private var zmanimManual = false

    private fun populateZmanim() {
        if (!zmanimManual) zmanimOffset = if (ZmanimInfo.afterTzeis()) 1 else 0
        findViewById<TextView>(R.id.hebrewDate).text = ZmanimInfo.hebrewDate()
        findViewById<TextView>(R.id.englishDate).text = ZmanimInfo.englishDate()
        findViewById<TextView>(R.id.zmanimLoc).text = ZmanimInfo.locationName() + "  ✎"
        findViewById<TextView>(R.id.zmanimDay).text = if (zmanimOffset == 1) "Tomorrow" else "Today"
        findViewById<TextView>(R.id.candleVal).text = ZmanimInfo.candleLighting()

        val styles = listOf(0xFFEDE9FE to 0xFF6D28D9, 0xFFFEF3C7 to 0xFFB45309, 0xFFDBEAFE to 0xFF1D4ED8,
                            0xFFD1FAE5 to 0xFF047857, 0xFFFFE4E6 to 0xFFBE123C, 0xFFCCFBF1 to 0xFF0F766E)
        val grid = findViewById<LinearLayout>(R.id.zmanimGrid)
        grid.removeAllViews()
        val list = ZmanimInfo.zmanimFor(zmanimOffset)
        var i = 0
        while (i < list.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(12)
            row.layoutParams = lp
            row.addView(zmanCell(list[i], styles[i % 6]))
            val sp = View(this); sp.layoutParams = LinearLayout.LayoutParams(dp(12), 1); row.addView(sp)
            if (i + 1 < list.size) row.addView(zmanCell(list[i + 1], styles[(i + 1) % 6]))
            else { val f = View(this); f.layoutParams = LinearLayout.LayoutParams(0, 1, 1f); row.addView(f) }
            grid.addView(row)
            i += 2
        }
    }

    private fun zmanCell(z: Zman, style: Pair<Long, Long>): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val bg = GradientDrawable(); bg.cornerRadius = dp(22).toFloat(); bg.setColor(style.first.toInt()); cell.background = bg
        cell.setPadding(dp(16), dp(12), dp(10), dp(12))
        val label = TextView(this); label.text = z.label; label.textSize = 15f
        label.setTextColor(style.second.toInt()); label.setTypeface(label.typeface, Typeface.BOLD)
        label.maxLines = 1; label.ellipsize = android.text.TextUtils.TruncateAt.END
        val value = TextView(this); value.text = z.time; value.textSize = 23f
        value.setTextColor(0xFF1E1B4B.toInt())
        try { value.typeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.inter_black) } catch (e: Exception) {}
        cell.addView(label); cell.addView(value)
        return cell
    }

    // ===== BIG CLOCK =====
    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clockRun = object : Runnable { override fun run() { tickClock(); clockHandler.postDelayed(this, 10_000) } }
    private fun startClock() { clockHandler.removeCallbacks(clockRun); clockHandler.post(clockRun) }
    private var lastDay = -1
    private fun tickClock() {
        val c = java.util.Calendar.getInstance()
        var h = c.get(java.util.Calendar.HOUR); if (h == 0) h = 12
        findViewById<TextView>(R.id.clockTime).text = String.format(Locale.US, "%d:%02d", h, c.get(java.util.Calendar.MINUTE))
        findViewById<TextView>(R.id.clockAmPm).text = if (c.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
        val day = c.get(java.util.Calendar.DAY_OF_YEAR)
        if (day != lastDay || (!zmanimManual && ZmanimInfo.afterTzeis() != (zmanimOffset == 1))) { lastDay = day; populateZmanim() }
    }

    // ===== SWIPE PAGES: Home · My Apps · Parental Controls =====
    private val hints = arrayOf("Swipe for your apps →", "← Home  ·  Parental controls →", "← Swipe back to apps")
    private val dotColors = intArrayOf(0xFF2563EB.toInt(), 0xFFEC4899.toInt(), 0xFF10B981.toInt())
    private val dotSoft = intArrayOf(0xFFC7D2FE.toInt(), 0xFFFBCFE8.toInt(), 0xFFA7F3D0.toInt())
    private fun setupPager() {
        val pager = findViewById<SwipePager>(R.id.pager)
        pager.onPage = { paintDots(it) }
        paintDots(0)
        findViewById<View>(R.id.pagerHint).setOnClickListener {
            pager.goTo(if (pager.page == 2) 1 else pager.page + 1)
        }
    }
    private fun paintDots(page: Int) {
        findViewById<TextView>(R.id.pagerHint).text = hints[page]
        val row = findViewById<LinearLayout>(R.id.dots); row.removeAllViews()
        for (i in 0 until 3) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(dp(if (i == page) 34 else 10), dp(10)); if (i > 0) lp.marginStart = dp(10)
            v.layoutParams = lp
            val g = GradientDrawable(); g.cornerRadius = dp(5).toFloat(); g.setColor(if (i == page) dotColors[page] else dotSoft[page])
            v.background = g; row.addView(v)
        }
    }

    // ===== PARENTAL CONTROLS PAGE =====
    private var lastRequests: JSONArray? = null
    private var paintingRows = false

    private fun statusLabel(st: String): String = when {
        st.equals("Approved", true) -> "✓ Approved"
        st.equals("Declined", true) -> "✕ Declined"
        else -> "⏳ Waiting"
    }

    private fun styleSwitch(sw: androidx.appcompat.widget.SwitchCompat, on: Int) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.trackTintList = android.content.res.ColorStateList(states, intArrayOf(on, 0xFFCBD5E1.toInt()))
        sw.thumbTintList = android.content.res.ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()))
    }

    private fun setupParentalPage() {
        val swBed = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swBedtime)
        val swInst = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swInstalls)
        styleSwitch(swBed, 0xFF7C3AED.toInt()); styleSwitch(swInst, 0xFFF97316.toInt())
        refreshParentalRows()
        swBed.setOnCheckedChangeListener { _, checked ->
            if (paintingRows) return@setOnCheckedChangeListener
            refreshParentalRows()   // put it back until the PIN is entered
            requirePin { ParentalRules.setBedtime(this, checked); refreshParentalRows(); adapter.notifyDataSetChanged() }
        }
        swInst.setOnCheckedChangeListener { _, checked ->
            if (paintingRows) return@setOnCheckedChangeListener
            refreshParentalRows()
            requirePin { ParentalRules.setBlockInstalls(this, checked); refreshParentalRows() }
        }
        findViewById<View>(R.id.bedtimeText).setOnClickListener { requirePin { pickBedtime() } }
        findViewById<View>(R.id.tileAppHours).setOnClickListener { startActivity(Intent(this, ParentalActivity::class.java)) }
        findViewById<View>(R.id.tileChangePin).setOnClickListener { requirePin { createPin("Set a new PIN") {} } }
        findViewById<View>(R.id.tileRequests).setOnClickListener { showRequestsList() }
        findViewById<View>(R.id.screenCard).setOnClickListener {
            if (!ParentalRules.hasUsageAccess(this)) {
                Toast.makeText(this, "Turn on Usage access for KosherTech Hub", Toast.LENGTH_LONG).show()
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (e: Exception) {}
            } else requirePin { pickLimit() }
        }
        refreshScreenTime()
    }

    private fun refreshParentalRows() {
        paintingRows = true
        findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swBedtime).isChecked = ParentalRules.bedtimeOn(this)
        findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swInstalls).isChecked = ParentalRules.blockInstalls(this)
        paintingRows = false
        findViewById<TextView>(R.id.bedtimeSub).text =
            ParentalRules.fmt(ParentalRules.bedStart(this)) + " – " + ParentalRules.fmt(ParentalRules.bedEnd(this))
    }

    private fun pickBedtime() {
        val s0 = ParentalRules.bedStart(this); val e0 = ParentalRules.bedEnd(this)
        android.app.TimePickerDialog(this, { _, sh, sm ->
            android.app.TimePickerDialog(this, { _, eh, em ->
                ParentalRules.setBedTimes(this, sh * 60 + sm, eh * 60 + em)
                refreshParentalRows(); adapter.notifyDataSetChanged()
            }, e0 / 60, e0 % 60, false).also { it.setTitle("Bedtime ends (morning)"); it.show() }
        }, s0 / 60, s0 % 60, false).also { it.setTitle("Bedtime starts"); it.show() }
    }

    private fun pickLimit() {
        val labels = arrayOf("No limit", "1 hour", "2 hours", "3 hours", "4 hours", "5 hours")
        val mins = intArrayOf(0, 60, 120, 180, 240, 300)
        AlertDialog.Builder(this).setTitle("Daily screen-time limit")
            .setItems(labels) { _, w -> ParentalRules.setDailyLimit(this, mins[w]); refreshScreenTime(); adapter.notifyDataSetChanged() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun fmtMins(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

    private fun refreshScreenTime() {
        val bar = findViewById<LinearLayout>(R.id.screenBar)
        val lim = ParentalRules.dailyLimit(this)
        findViewById<TextView>(R.id.screenLimit).text = if (lim > 0) "Limit ${lim / 60}h" else "No limit"
        if (!ParentalRules.hasUsageAccess(this)) {
            findViewById<TextView>(R.id.screenTotal).text = "—"
            findViewById<View>(R.id.screenHint).visibility = View.VISIBLE
            bar.removeAllViews(); return
        }
        findViewById<View>(R.id.screenHint).visibility = View.GONE
        val u = ParentalRules.usageToday(this)
        val music = setOf("com.jtech.zemer", "app.tfs.prod", "fm.jewishmusic.application")
        var claude = 0L; var mus = 0L; var other = 0L
        for (e in ParentalRules.apps(this)) {
            val ms = u[e.pkg] ?: 0L
            when { e.pkg == "com.anthropic.claude" -> claude += ms; e.pkg in music -> mus += ms; else -> other += ms }
        }
        val total = ((claude + mus + other) / 60000L).toInt()
        findViewById<TextView>(R.id.screenTotal).text = fmtMins(total)
        // Bar is scaled to the limit (or 3 hours when there is no limit).
        val scale = (if (lim > 0) lim else 180).toLong() * 60000L
        bar.removeAllViews()
        val parts = listOf(claude to 0xFFF97316.toInt(), mus to 0xFFFACC15.toInt(), other to 0xFF34D399.toInt())
        var used = 0f
        for ((ms, col) in parts) {
            val w = (ms.toFloat() / scale).coerceAtMost(1f - used); if (w <= 0f) continue
            used += w
            val v = View(this); v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w)
            val g = GradientDrawable(); g.setColor(col); g.cornerRadius = dp(8).toFloat(); v.background = g
            bar.addView(v)
        }
        if (used < 1f) { val rest = View(this); rest.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - used); bar.addView(rest) }
    }

    private fun showRequestsList() {
        val reqs = lastRequests
        val lines = ArrayList<String>()
        if (reqs != null) for (i in 0 until reqs.length()) {
            val r = reqs.getJSONObject(i); lines.add(r.optString("appName", "") + "  ·  " + statusLabel(r.optString("status", "Pending")))
        }
        AlertDialog.Builder(this).setTitle("App requests")
            .setMessage(if (lines.isEmpty()) "No app requests yet." else lines.joinToString("\n"))
            .setPositiveButton("OK", null).show()
    }

    // PIN gate for every parental change. No PIN yet → make one first.
    private fun requirePin(onOk: () -> Unit) {
        val pin = getSharedPreferences("ktparental", Context.MODE_PRIVATE).getString("pin", null)
        if (pin == null) { createPin("Create a parent PIN (4+ digits)", onOk); return }
        promptPin(onOk)
    }

    private fun createPin(title: String, then: () -> Unit) {
        val et = pinField()
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("Save") { _, _ ->
                val p = et.text.toString()
                if (p.length >= 4) {
                    getSharedPreferences("ktparental", Context.MODE_PRIVATE).edit().putString("pin", p).apply()
                    Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show(); then()
                } else Toast.makeText(this, "Use at least 4 digits", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showHelp() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_help, null)
        val dialog = customDialog(view)
        view.findViewById<View>(R.id.closeX).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.primaryBtn).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.helpChat).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ChatActivity::class.java))
        }
        dialog.show()
    }

    // Installed -> open it. NOT installed -> ask KosherTech to add it.
    // IMPORTANT: this never opens the Play Store or any app store. Approved apps
    // are installed by KosherTech (via MDM), so the customer can never browse a store.
    private fun openOrInstall(app: AppItem) {
        // Locked app? Require the parent PIN before opening.
        if (isLocked(app.packageName)) {
            promptPin { doOpenOrInstall(app) }
            return
        }
        doOpenOrInstall(app)
    }

    private fun doOpenOrInstall(app: AppItem) {
        if (!ParentalRules.state(this, app.packageName).allowed) {
            Toast.makeText(this, app.name + " is locked by Parental Controls", Toast.LENGTH_SHORT).show()
            return
        }
        if (app.internal == "calendar") { startActivity(Intent(this, CalendarActivity::class.java)); return }
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch != null) {
            startActivity(launch)   // already installed -> just open it
            return
        }
        if (app.apkUrl.isNotBlank() && !ParentalRules.blockInstalls(this)) {
            // Approved APK hosted by KosherTech -> install it directly, no Play Store.
            Toast.makeText(this, "Installing " + app.name + "…", Toast.LENGTH_SHORT).show()
            downloadAndInstall(app.apkUrl)
        } else {
            showInstallRequestDialog(app)   // no hosted APK yet -> safe request, no store
        }
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
                payload.put("deviceId", if (::deviceId.isInitialized) deviceId else "")
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
