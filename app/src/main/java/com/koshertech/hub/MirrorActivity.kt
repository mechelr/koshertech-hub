package com.koshertech.hub

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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

// A simple "mirror": front camera live, with a saved "good look" reference to compare against.
class MirrorActivity : AppCompatActivity() {

    private lateinit var preview: PreviewView
    private val refFile by lazy { File(filesDir, "mirror_reference.jpg") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mirror)
        preview = findViewById(R.id.preview)

        findViewById<View>(R.id.doneBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.saveBtn).setOnClickListener { saveReference() }

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
            FileOutputStream(refFile).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
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
            label.text = "Tap ⭐ to save\na good look"
        }
    }
}
