package com.koshertech.hub

import android.graphics.Typeface
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

class ChatActivity : AppCompatActivity() {

    private val ENDPOINT = "https://script.google.com/macros/s/AKfycbyItxTmCLC4oLj1Xv2xaoOwNmhl_r2XEln6BqFyoKStbyV5Q-ve-40wXByWJqkC9hDv/exec"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        addBubble("Hi! 👋  How can KosherTech help you today?", false)

        findViewById<TextView>(R.id.chatSend).setOnClickListener { send() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun send() {
        val input = findViewById<EditText>(R.id.chatInput)
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return
        addBubble(msg, true)
        input.setText("")
        sendToSupport(msg)
        // friendly auto-acknowledgement (a real rep reply arrives by the backend we add next)
        Handler(Looper.getMainLooper()).postDelayed({
            addBubble("Thanks! A KosherTech rep will get back to you shortly. 🙏", false)
        }, 700)
    }

    private fun sendToSupport(msg: String) {
        Thread {
            try {
                val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                conn.doOutput = true
                val p = JSONObject()
                p.put("_subject", "KosherTech Hub: Live Chat")
                p.put("type", "Live chat")
                p.put("message", msg)
                conn.outputStream.use { it.write(p.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) { /* offline — message still shows in the chat */ }
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
