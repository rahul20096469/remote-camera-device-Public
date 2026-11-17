package com.remotecamera.viewer

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Shown after the viewer scans a valid pairing code. This screen requests
 * a connection via Firebase signaling and displays the remote video once
 * the host accepts. No media capture happens on this device - it only
 * receives and displays the remote stream.
 */
class ViewerCallActivity : AppCompatActivity() {

    private lateinit var webRtcViewerClient: WebRtcViewerClient
    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra("deviceId") ?: run {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusText = TextView(this).apply {
            text = getString(R.string.connecting_text)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(32, 64, 32, 32)
        }
        root.addView(statusText)

        remoteRenderer = SurfaceViewRenderer(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(remoteRenderer)

        val disconnectButton = Button(this).apply {
            text = getString(R.string.disconnect_button)
        }
        disconnectButton.setOnClickListener { finish() }
        root.addView(disconnectButton)

        setContentView(root)

        webRtcViewerClient = WebRtcViewerClient(this, deviceId)
        webRtcViewerClient.initialize()
        remoteRenderer.init(webRtcViewerClient.eglContext(), null)

        webRtcViewerClient.connect(object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                statusText.text = getString(R.string.connected_text)
                remoteRenderer.onFrame(frame)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webRtcViewerClient.isInitialized) {
            webRtcViewerClient.stop()
        }
    }
}
