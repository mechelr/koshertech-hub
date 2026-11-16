package com.koshertech.hub

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    // ===== YOUR APPS =====
    // To add an app: copy a line, change the name/category/package/color.
    // The "package" is the app's exact ID on the phone. We read the real ones off
    // the Jelly Star together (Zemer's is a placeholder until then).
    private val allApps = listOf(
        AppItem("Zemer", "Music · Jewish songs", "PACKAGE_FOR_ZEMER", 0xFFE0A52B.toInt()),
        AppItem("24Six", "Music · Jewish streaming", "app.tfs.prod", 0xFFE04F5F.toInt()),
        AppItem("Zing", "Music · Streaming", "fm.jewishmusic.application", 0xFF9333EA.toInt())
    )

    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recycler = findViewById<RecyclerView>(R.id.appList)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(allApps) { app -> openApp(app) }
        recycler.adapter = adapter

        findViewById<EditText>(R.id.searchBox).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<TextView>(R.id.requestButton).setOnClickListener { showRequestDialog() }
        findViewById<TextView>(R.id.loopholeButton).setOnClickListener { showLoopholeDialog() }
        findViewById<TextView>(R.id.rateButton).setOnClickListener { showRateDialog() }
    }

    // Opens the real installed app. If it isn't on the phone yet, it says so.
    private fun openApp(app: AppItem) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, app.name + " isn't installed yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRequestDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_request, null)
        val appName = view.findViewById<EditText>(R.id.reqAppName)
        val reason = view.findViewById<EditText>(R.id.reqReason)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Send request") { _, _ ->
                sendToInbox("App request", appName.text.toString() + " — " + reason.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoopholeDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loophole, null)
        val desc = view.findViewById<EditText>(R.id.loopDesc)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Send report") { _, _ ->
                sendToInbox("Loophole report", desc.text.toString())
                Toast.makeText(this, "Loophole reported — thank you!", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRateDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_rate, null)
        val bar = view.findViewById<RatingBar>(R.id.ratingBar)
        val comment = view.findViewById<EditText>(R.id.rateComment)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Send rating") { _, _ ->
                sendToInbox("Rating", bar.rating.toInt().toString() + " stars — " + comment.text.toString())
                Toast.makeText(this, "Thanks for rating us!", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // All three (request / loophole / rating) go here.
    // Next, we set up a tiny free backend (Google Apps Script or Formspree) that
    // emails these to you, and paste its web address in here.
    private fun sendToInbox(kind: String, body: String) {
        Toast.makeText(this, kind + " sent", Toast.LENGTH_SHORT).show()
    }
}
