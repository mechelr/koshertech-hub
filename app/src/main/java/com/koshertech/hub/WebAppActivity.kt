package com.koshertech.hub

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * A website shown as a locked "app":
 *  - only pages on the same website are allowed (any other link is ignored),
 *  - no new tabs / pop-up windows, no downloads, no address bar, no long-press menu,
 *  - Play Store / market:// / intent:// links can never open from here.
 */
class WebAppActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var site = ""                      // e.g. "example.com"
    private var fullView: View? = null
    private var fullCb: WebChromeClient.CustomViewCallback? = null
    private lateinit var root: FrameLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun baseDomain(host: String?): String {
        val h = (host ?: "").lowercase().removePrefix("www.")
        val parts = h.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else h
    }

    private fun allowed(uri: Uri): Boolean {
        val scheme = (uri.scheme ?: "").lowercase()
        if (scheme != "https" && scheme != "http") return false
        val host = (uri.host ?: "").lowercase()
        return host == site || host.endsWith(".$site")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: run { finish(); return }
        val title = intent.getStringExtra("title") ?: "App"
        site = baseDomain(Uri.parse(url).host)

        root = FrameLayout(this)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.parseColor("#F5F7FD"))

        // top bar: back arrow + name (no address bar)
        val bar = LinearLayout(this); bar.orientation = LinearLayout.HORIZONTAL; bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(8), dp(8), dp(16), dp(8)); bar.setBackgroundColor(Color.WHITE)
        val back = ImageView(this); back.setImageResource(R.drawable.ic_x_chevron); back.rotation = 180f
        back.setColorFilter(Color.parseColor("#1E1B4B")); back.setPadding(dp(10), dp(10), dp(10), dp(10))
        back.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        back.setOnClickListener { if (web.canGoBack()) web.goBack() else finish() }
        val name = TextView(this); name.text = title; name.textSize = 18f
        name.setTextColor(Color.parseColor("#1E1B4B")); name.setTypeface(name.typeface, android.graphics.Typeface.BOLD)
        name.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val home = TextView(this); home.text = "Close"; home.textSize = 14f
        home.setTextColor(Color.parseColor("#7C3AED")); home.setPadding(dp(10), dp(8), dp(4), dp(8))
        home.setOnClickListener { finish() }
        bar.addView(back); bar.addView(name); bar.addView(home)
        val line = View(this); line.setBackgroundColor(Color.parseColor("#E2E8F0"))
        line.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        progress.max = 100

        web = WebView(this)
        web.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        col.addView(bar); col.addView(line); col.addView(progress); col.addView(web)
        root.addView(col)
        setContentView(root)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.setSupportMultipleWindows(false)              // "open in new tab" loads here instead
        s.javaScriptCanOpenWindowsAutomatically = false // no pop-ups
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportZoom(false)
        CookieManager.getInstance().setAcceptCookie(true)   // stay signed in to the site

        web.isLongClickable = false
        web.setOnLongClickListener { true }               // no "open in new tab / copy link" menu
        web.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, "Downloads are turned off", Toast.LENGTH_SHORT).show()
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (allowed(request.url)) return false
                Toast.makeText(this@WebAppActivity, "That link is blocked", Toast.LENGTH_SHORT).show()
                return true                                // stay on the page
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progress.progress = p; progress.visibility = if (p >= 100) View.GONE else View.VISIBLE
            }
            // full-screen video (live streams)
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullView = view; fullCb = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            override fun onHideCustomView() {
                fullView?.let { root.removeView(it) }; fullView = null; fullCb?.onCustomViewHidden(); fullCb = null
            }
        }
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            fullView != null -> web.webChromeClient?.onHideCustomView()
            web.canGoBack() -> web.goBack()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try { web.destroy() } catch (e: Exception) {}
        super.onDestroy()
    }
}
