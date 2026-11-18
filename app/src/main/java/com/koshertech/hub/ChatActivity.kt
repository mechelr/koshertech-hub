package com.koshertech.hub

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private val ENDPOINT = "https://script.google.com/macros/s/AKfycbyItxTmCLC4oLj1Xv2xaoOwNmhl_r2XEln6BqFyoKStbyV5Q-ve-40wXByWJqkC9hDv/exec"

    private lateinit var chatId: String
    private var adminShown = 0
    private val handler = Handler(Looper.getMainLooper())
    private val poller = object : Runnable {
        override fun run() {
            pollReplies()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        val prefs = getSharedPreferences("ktchat", Context.MODE_PRIVATE)
        chatId = prefs.getString("chatId", null) ?: UUID.randomUUID().toString().substring(0, 8).also {
            prefs.edit().putString("chatId", it).apply()
        }

        addBubble("Hi! 👋  How can KosherTech help you today?", false)
        findViewById<TextView>(R.id.chatSend).setOnClickListener { send() }
    }

    override fun onResume() { super.onResume(); handler.post(poller) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(poller) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun send() {
        val input = findViewById<EditText>(R.id.chatInput)
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return
        addBubble(msg, true)
        input.setText("")
        Thread {
            try {
                val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15000; conn.readTimeout = 15000
                conn.instanceFollowRedirects = true; conn.doOutput = true
                val p = JSONObject()
                p.put("_subject", "KosherTech Hub: Live Chat")
                p.put("type", "Live chat")
                p.put("chatId", chatId)
                p.put("message", msg)
                conn.outputStream.use { it.write(p.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) { }
        }.start()
    }

    // Poll the backend for replies from the KosherTech rep (sender == "admin").
    private fun pollReplies() {
        Thread {
            try {
                val conn = URL(ENDPOINT + "?chatId=" + chatId).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                conn.instanceFollowRedirects = true
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val obj = JSONObject(text)
                val arr = obj.optJSONArray("messages") ?: return@Thread
                val admin = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    if (m.optString("sender") == "admin") admin.add(m.optString("text"))
                }
                if (admin.size > adminShown) {
                    val newOnes = admin.subList(adminShown, admin.size).toList()
                    adminShown = admin.size
                    runOnUiThread { for (t in newOnes) addBubble(t, false) }
                }
            } catch (e: Exception) { /* backend not set up yet or offline — ignore */ }
        }.start()
    }

    private fun addBubble(text: String, outgoing: Boolean) {
        val container = findViewById<LinearLayout>(R.id.chatMessages)
        val bubble = TextView(this)
        bubble.text = text
        bubble.textSize = 14f
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10))
        if (outgoing) {
            bubble.setBackgroundResource(R.drawable.bg_bubble_out)
            bubble.setTextColor(0xFF04121C.toInt())
        } else {
            bubble.setBackgroundResource(R.drawable.bg_bubble_in)
            bubble.setTextColor(0xFFF3F7FF.toInt())
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = if (outgoing) Gravity.END else Gravity.START
        lp.topMargin = dp(6)
        lp.leftMargin = if (outgoing) dp(50) else 0
        lp.rightMargin = if (outgoing) 0 else dp(50)
        bubble.layoutParams = lp
        container.addView(bubble)

        val scroll = findViewById<ScrollView>(R.id.chatScroll)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
