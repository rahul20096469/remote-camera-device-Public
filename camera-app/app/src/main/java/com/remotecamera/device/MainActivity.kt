package com.remotecamera.device

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.remotecamera.device.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var deviceId: String = ""
    private lateinit var pairingManager: PairingManager
    private var callStarted = false

    private val permissionsNeeded = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            binding.statusText.text = getString(R.string.permissions_granted)
            showPermanentQrCode()
            startPairingListener()
        } else {
            binding.statusText.text = getString(R.string.permissions_denied)
            Toast.makeText(
                this,
                getString(R.string.permissions_required_explanation),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceId = DeviceIdentity.getOrCreateDeviceId(this)
        binding.deviceIdText.text = getString(R.string.device_id_label, deviceId.take(8))
        pairingManager = PairingManager(deviceId)

        if (hasAllPermissions()) {
            binding.statusText.text = getString(R.string.permissions_granted)
            showPermanentQrCode()
            startPairingListener()
        } else {
            binding.statusText.text = getString(R.string.permissions_not_yet_granted)
            binding.grantPermissionsButton.setOnClickListener {
                permissionLauncher.launch(permissionsNeeded)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        callStarted = false
    }

    private fun hasAllPermissions(): Boolean {
        return permissionsNeeded.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startPairingListener() {
        pairingManager.start(
            onReady = {
                pairingManager.listenForIncomingRequest { viewerId ->
                    if (!callStarted) {
                        callStarted = true
                        val intent = Intent(this, CallActivity::class.java)
                        intent.putExtra("viewerId", viewerId)
                        startActivity(intent)
                    }
                }
            },
            onError = { e ->
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showPermanentQrCode() {
        binding.grantPermissionsButton.visibility = android.view.View.GONE
        val payload = "remotecamera-pair:$deviceId"
        val bitmap = generateQrBitmap(payload, 600, 600)
        binding.qrImageView.setImageBitmap(bitmap)
        binding.qrImageView.visibility = android.view.View.VISIBLE
    }

    private fun generateQrBitmap(content: String, width: Int, height: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
