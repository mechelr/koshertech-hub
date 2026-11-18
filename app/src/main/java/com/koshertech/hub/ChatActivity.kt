package com.koshertech.hub

import android.content.Context
import android.content.SharedPreferences
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

    private val ENDPOINT = "https://script.google.com/macros/s/AKfycbzeh0yW4sqHXuJZXsg31xZFDXm4q7crX-MIWBCE4bEDyLg7l-yY4askgSfZd9II7_wG/exec"

    private lateinit var chatId: String
    private lateinit var prefsChat: SharedPreferences
    private var adminShown = 0
    private var answered = false       // true once a real rep (admin) has replied to this ticket
    private var firstAutoSent = false  // whether the full greeting has gone out this ticket
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
        loadTicketState()

        addBubble("Hi! 👋  How can KosherTech help you today?", false)
        findViewById<TextView>(R.id.chatSend).setOnClickListener { send() }
    }

    private fun loadTicketState() {
        answered = prefsChat.getBoolean("answered_" + chatId, false)
        firstAutoSent = prefsChat.getBoolean("firstauto_" + chatId, false)
        ticketNo = "KT-" + chatId.uppercase().take(6)
    }

    // When a human rep has already answered and the customer starts a fresh question,
    // open a brand-new ticket so the auto-responder runs again until the rep replies.
    private fun startNewTicket() {
        // Keep the same chatId (= deviceId) so the dashboard groups all chat under this device;
        // just reset so the auto-responder runs again until the rep replies.
        answered = false
        firstAutoSent = false
        prefsChat.edit().putBoolean("answered_" + chatId, false).putBoolean("firstauto_" + chatId, false).apply()
    }

    override fun onResume() { super.onResume(); handler.post(poller) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(poller) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun send() {
        val input = findViewById<EditText>(R.id.chatInput)
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return

        // Already answered by a rep? A new message opens a fresh ticket + restarts auto-replies.
        if (answered) startNewTicket()

        addBubble(msg, true)
        input.setText("")

        // Auto-responder: reply automatically until a real rep writes back.
        // A short pause + a "typing…" indicator makes it feel like a real person.
        if (!answered) {
            val first = !firstAutoSent
            firstAutoSent = true
            prefsChat.edit().putBoolean("firstauto_" + chatId, true).apply()
            handler.postDelayed({
                if (answered) return@postDelayed
                val typing = addBubble("✍️  typing…", false)
                handler.postDelayed({
                    removeBubble(typing)
                    if (!answered) {
                        val reply = if (first)
                            "Hi 👋 Thanks for reaching out to KosherTech support. A ticket (#$ticketNo) has been created and we're on it. A member of our team will reply right here as fast as we possibly can."
                        else
                            "Got it 👍 Your message was added to ticket #$ticketNo. We'll reply as soon as a rep is available — thanks for your patience."
                        addBubble(reply, false)
                    }
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
                    val who = if (m.has("from")) m.optString("from") else m.optString("sender")
                    if (who == "admin") admin.add(m.optString("text"))
                }
                if (admin.size > adminShown) {
                    val newOnes = admin.subList(adminShown, admin.size).toList()
                    adminShown = admin.size
                    // A real rep replied → stop the auto-responder for this ticket.
                    if (!answered) {
                        answered = true
                        prefsChat.edit().putBoolean("answered_" + chatId, true).apply()
                    }
                    runOnUiThread { for (t in newOnes) addBubble(t, false) }
                }
            } catch (e: Exception) { /* backend not set up yet or offline — ignore */ }
        }.start()
    }

    private fun removeBubble(bubble: TextView?) {
        if (bubble == null) return
        (bubble.parent as? LinearLayout)?.removeView(bubble)
    }

    private fun addBubble(text: String, outgoing: Boolean): TextView {
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
        return bubble
    }
}
