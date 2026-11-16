package com.remotecamera.device

import android.content.Context
import org.webrtc.*
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject

/**
 * Wraps the WebRTC engine: creates the local camera+mic track, builds the
 * peer connection, and exchanges the offer/answer/ICE messages through
 * Firebase Realtime Database under this device's node.
 *
 * This class only ever sends media after start() is called explicitly from
 * CallActivity - which itself only calls start() after the host has tapped
 * "Accept" on an incoming request. It never starts on its own.
 */
class WebRtcClient(
    private val context: Context,
    private val deviceId: String,
    private val viewerId: String
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private val eglBase = EglBase.create()

    private val signalingRef = FirebaseDatabase.getInstance()
        .getReference("devices").child(deviceId).child("sessions").child(viewerId)

    fun eglContext(): EglBase.Context = eglBase.eglBaseContext

    fun initialize() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    /**
     * Starts the local camera + microphone capture and creates the peer
     * connection. Called only after explicit host confirmation - see
     * CallActivity.
     */
    fun start(localVideoSink: VideoSink) {
        val factory = peerConnectionFactory ?: return

        // Camera capture
        val cameraEnumerator = Camera2Enumerator(context)
        val cameraName = cameraEnumerator.deviceNames.firstOrNull {
            cameraEnumerator.isBackFacing(it)
        } ?: cameraEnumerator.deviceNames.first()

        videoCapturer = cameraEnumerator.createCapturer(cameraName, null)

        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val videoSource = factory.createVideoSource(false)
        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("local_video", videoSource)
        localVideoTrack?.addSink(localVideoSink)

        // Microphone capture
        val audioConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("local_audio", audioSource)

        // Peer connection
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                sendIceCandidate(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        peerConnection?.addTrack(localVideoTrack)
        peerConnection?.addTrack(localAudioTrack)

        listenForAnswerAndIce()
        createOffer()
    }

    private fun createOffer() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), desc)
                signalingRef.child("offer").setValue(
                    mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description)
                )
            }
        }, constraints)
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val payload = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }
        signalingRef.child("hostIceCandidates").push().setValue(payload.toString())
    }

    private fun listenForAnswerAndIce() {
        signalingRef.child("answer").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val type = snapshot.child("type").getValue(String::class.java)
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                if (type != null && sdp != null) {
                    val desc = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                    peerConnection?.setRemoteDescription(SdpObserverAdapter(), desc)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        signalingRef.child("viewerIceCandidates").addChildEventListener(object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                val raw = snapshot.getValue(String::class.java) ?: return
                val json = JSONObject(raw)
                val candidate = IceCandidate(
                    json.getString("sdpMid"),
                    json.getInt("sdpMLineIndex"),
                    json.getString("candidate")
                )
                peerConnection?.addIceCandidate(candidate)
            }
            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {}
            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    /** Stops capture and tears down the connection. Clears signaling data too. */
    fun stop() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        peerConnection?.close()
        signalingRef.removeValue()
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
