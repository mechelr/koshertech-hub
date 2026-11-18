package com.koshertech.hub

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private val ENDPOINT = "https://script.google.com/macros/s/AKfycbzeh0yW4sqHXuJZXsg31xZFDXm4q7crX-MIWBCE4bEDyLg7l-yY4askgSfZd9II7_wG/exec"

    private lateinit var chatId: String
    private lateinit var prefsChat: SharedPreferences
    private var adminShown = 0
    private var autoReplied = false    // one auto-reply per time the chat screen is opened
    private var ticketNo = ""
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

        prefsChat = getSharedPreferences("ktchat", Context.MODE_PRIVATE)
        // Use the device's ID as the chat ID so the admin dashboard ties chat to the device.
        val devId = getSharedPreferences("ktdevice", Context.MODE_PRIVATE).getString("deviceId", null)
        chatId = devId ?: prefsChat.getString("chatId", null) ?: UUID.randomUUID().toString().substring(0, 8).also {
            prefsChat.edit().putString("chatId", it).apply()
        }

        addBubble("Hi! 👋  How can KosherTech help you today?", false)
        findViewById<TextView>(R.id.chatSend).setOnClickListener { send() }
    }

    // A fresh (fake) ticket number every time a conversation starts.
    private fun newTicketNo(): String = "KT-" + (10000 + java.util.Random().nextInt(89999))

    override fun onResume() { super.onResume(); handler.post(poller) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(poller) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun send() {
        val input = findViewById<EditText>(R.id.chatInput)
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return

        addBubble(msg, true)
        input.setText("")

        // Auto-reply fires only ONCE — on the first message after opening the chat.
        // After that, no more auto-replies this session (even once the rep replies).
        if (!autoReplied) {
            autoReplied = true
            ticketNo = newTicketNo()
            handler.postDelayed({
                val typing = addBubble("✍️  typing…", false)
                handler.postDelayed({
                    removeBubble(typing)
                    addBubble(
                        "Hi 👋 Thanks for reaching out to KosherTech support. A ticket (#$ticketNo) has been created and we're on it. A member of our team will reply right here as fast as we possibly can.",
                        false
                    )
                }, 2800)
            }, 1200)
        }

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
                val conn = URL(ENDPOINT + "?action=chat&chatId=" + chatId).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                conn.instanceFollowRedirects = true
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val obj = JSONObject(text)
                val arr = obj.optJSONArray("messages") ?: return@Thread
                val admin = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val who = if (m.has("from")) m.optString("from") else m.optString("sender")
                    if (who == "admin") admin.add(m.optString("text"))
                }
                if (admin.size > adminShown) {
                    val newOnes = admin.subList(adminShown, admin.size).toList()
                    adminShown = admin.size
                    runOnUiThread { for (t in newOnes) addBubble(t, false) }
                }
            } catch (e: Exception) { /* backend not set up yet or offline — ignore */ }
        }.start()
    }

    private fun removeBubble(v: View?) {
        if (v == null) return
        (v.parent as? ViewGroup)?.removeView(v)
    }

    private fun addBubble(text: String, outgoing: Boolean): View {
        val container = findViewById<LinearLayout>(R.id.chatMessages)

        // Wrapper so an incoming (support) message can carry a profile header above it.
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.gravity = if (outgoing) Gravity.END else Gravity.START
        val wlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        wlp.topMargin = dp(8)
        wlp.leftMargin = if (outgoing) dp(50) else 0
        wlp.rightMargin = if (outgoing) 0 else dp(50)
        wrap.layoutParams = wlp

        if (!outgoing) {
            val header = LinearLayout(this); header.orientation = LinearLayout.HORIZONTAL; header.gravity = Gravity.CENTER_VERTICAL
            val avatar = ImageView(this)
            avatar.setImageResource(R.drawable.ic_logo)
            val alp = LinearLayout.LayoutParams(dp(22), dp(22)); alp.rightMargin = dp(6); avatar.layoutParams = alp
            val nm = TextView(this)
            nm.text = "KosherTech Employee"
            nm.setTextColor(0xFF22D3EE.toInt()); nm.textSize = 11f; nm.setTypeface(nm.typeface, Typeface.BOLD)
            header.addView(avatar); header.addView(nm)
            val hlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); hlp.bottomMargin = dp(3)
            header.layoutParams = hlp
            wrap.addView(header)
        }

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
        bubble.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        wrap.addView(bubble)

        container.addView(wrap)
        val scroll = findViewById<ScrollView>(R.id.chatScroll)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return wrap
    }
}
