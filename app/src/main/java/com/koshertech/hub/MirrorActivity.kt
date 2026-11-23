package com.koshertech.hub

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

// A "mirror": front camera live. Save a reference "good look", then Check compares a fresh
// photo to it and gives a rough match read-out. All on-device — no photo leaves the phone.
class MirrorActivity : AppCompatActivity() {

    private lateinit var preview: PreviewView
    private val refFile by lazy { File(filesDir, "mirror_reference.jpg") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mirror)
        preview = findViewById(R.id.preview)

        findViewById<View>(R.id.doneBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.saveBtn).setOnClickListener { saveReference() }
        findViewById<View>(R.id.checkBtn).setOnClickListener { checkLook() }
        findViewById<View>(R.id.backBtn).setOnClickListener { showLive() }

        loadReference()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 71)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            val hint = findViewById<TextView>(R.id.permHint)
            hint.text = "Camera access is needed for the mirror.\nAllow it in settings (or let KosherTech grant it)."
            hint.visibility = View.VISIBLE
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val useCase = Preview.Builder().build()
                useCase.setSurfaceProvider(preview.surfaceProvider)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't start the camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun saveReference() {
        val bmp = preview.bitmap
        if (bmp == null) { Toast.makeText(this, "Give the camera a second, then try again.", Toast.LENGTH_SHORT).show(); return }
        try {
            FileOutputStream(refFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            loadReference()
            Toast.makeText(this, "Saved! This is your reference look.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save the photo.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadReference() {
        val img = findViewById<ImageView>(R.id.reference)
        val label = findViewById<TextView>(R.id.refLabel)
        if (refFile.exists()) {
            img.setImageBitmap(BitmapFactory.decodeFile(refFile.absolutePath))
            label.text = "Your good look"
        } else {
            img.setImageDrawable(null)
            label.text = "Tap ⭐ Save look\nfirst"
        }
    }

    private fun checkLook() {
        if (!refFile.exists()) {
            Toast.makeText(this, "First tap ⭐ Save look on a good day, then Check.", Toast.LENGTH_LONG).show()
            return
        }
        val now = preview.bitmap
        if (now == null) { Toast.makeText(this, "Give the camera a second, then try again.", Toast.LENGTH_SHORT).show(); return }
        val ref = BitmapFactory.decodeFile(refFile.absolutePath) ?: return

        findViewById<ImageView>(R.id.shotNow).setImageBitmap(now)
        findViewById<ImageView>(R.id.shotRef).setImageBitmap(ref)

        val match = similarity(now, ref)  // 0..100
        val verdict = findViewById<TextView>(R.id.verdict)
        val sub = findViewById<TextView>(R.id.verdictSub)
        when {
            match >= 68 -> { verdict.text = "👍 You're good to go — looking like your best."; verdict.setTextColor(0xFFFFFFFF.toInt()) }
            match >= 45 -> { verdict.text = "🙂 Close. Maybe a quick check of your hair & collar."; verdict.setTextColor(0xFFFFFFFF.toInt()) }
            else        -> { verdict.text = "🪞 Looks different today — give yourself a once-over."; verdict.setTextColor(0xFFFFFFFF.toInt()) }
        }
        sub.text = "About $match% match to your saved look. Lighting and angle affect this — your own eyes are the final say."
        findViewById<View>(R.id.resultLayer).visibility = View.VISIBLE
    }

    private fun showLive() {
        findViewById<View>(R.id.resultLayer).visibility = View.GONE
    }

    // Rough on-device similarity: downscale both to 16x16 grayscale and compare.
    private fun similarity(a: Bitmap, b: Bitmap): Int {
        val n = 16
        val ga = gray(Bitmap.createScaledBitmap(a, n, n, true))
        val gb = gray(Bitmap.createScaledBitmap(b, n, n, true))
        var diff = 0L
        for (i in ga.indices) diff += abs(ga[i] - gb[i])
        val maxDiff = 255L * ga.size
        val sim = 100.0 * (1.0 - diff.toDouble() / maxDiff)
        return sim.toInt().coerceIn(0, 100)
    }

    private fun gray(bmp: Bitmap): IntArray {
        val out = IntArray(bmp.width * bmp.height)
        var k = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            out[k++] = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
        }
        return out
    }
}
