package com.remotecamera.viewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Lets the user scan the pairing QR code shown on the camera device app.
 * Scanning only reads the device's pairing ID - it does not itself start
 * any camera or microphone session. The actual session still needs the
 * host device to tap "Accept" (handled in the camera app's CallActivity).
 */
class MainActivity : AppCompatActivity() {

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (scanned != null && scanned.startsWith("remotecamera-pair:")) {
            val deviceId = scanned.removePrefix("remotecamera-pair:")
            val intent = Intent(this, ViewerCallActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            startActivity(intent)
        } else if (scanned != null) {
            statusText.text = getString(R.string.invalid_qr_code)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchScanner()
        } else {
            statusText.text = getString(R.string.camera_permission_needed)
        }
    }

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = getString(R.string.viewer_title)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        root.addView(title)

        statusText = TextView(this).apply {
            text = getString(R.string.scan_instruction)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        root.addView(statusText)

        val scanButton = Button(this).apply {
            text = getString(R.string.scan_button)
        }
        scanButton.setOnClickListener {
            checkPermissionAndScan()
        }
        root.addView(scanButton)

        setContentView(root)
    }

    private fun checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt(getString(R.string.scan_instruction))
        options.setBeepEnabled(false)
        options.setOrientationLocked(true)
        scanLauncher.launch(options)
    }
}
