package com.remotecamera.device

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink
import org.webrtc.VideoFrame
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.view.ViewGroup
import android.view.Gravity

/**
 * Shown when a Viewer requests to connect. The host must explicitly tap
 * "Accept" here before the camera or microphone starts - this screen
 * always appears first and streaming never begins without this tap.
 * While active, a persistent notification (via the foreground service,
 * added in a later step) stays visible to show the camera is in use.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var webRtcClient: WebRtcClient
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewerId = intent.getStringExtra("viewerId") ?: run {
            finish()
            return
        }
        val deviceId = DeviceIdentity.getOrCreateDeviceId(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusText = TextView(this).apply {
            text = getString(R.string.incoming_request_text, viewerId.take(8))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(32, 64, 32, 32)
        }
        root.addView(statusText)

        localRenderer = SurfaceViewRenderer(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(localRenderer)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 64)
        }

        val acceptButton = Button(this).apply { text = getString(R.string.accept_button) }
        val declineButton = Button(this).apply { text = getString(R.string.decline_button) }
        buttonRow.addView(acceptButton)
        buttonRow.addView(declineButton)
        root.addView(buttonRow)

        setContentView(root)

        webRtcClient = WebRtcClient(this, deviceId, viewerId)
        webRtcClient.initialize()
        localRenderer.init(webRtcClient.eglContext(), null)

        acceptButton.setOnClickListener {
            // Explicit host confirmation happens here - only now does
            // camera/mic capture and streaming begin.
            statusText.text = getString(R.string.streaming_active_text)
            buttonRow.visibility = android.view.View.GONE
            webRtcClient.start(object : VideoSink {
                override fun onFrame(frame: VideoFrame) {
                    localRenderer.onFrame(frame)
                }
            })
        }

        declineButton.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webRtcClient.isInitialized) {
            webRtcClient.stop()
        }
    }
}
