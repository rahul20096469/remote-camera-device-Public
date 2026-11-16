package com.remotecamera.device

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PairingManager(private val deviceId: String) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val deviceRef = database.getReference("devices").child(deviceId)

    private var statusListener: ValueEventListener? = null

    fun start(onReady: () -> Unit, onError: (Exception) -> Unit) {
        if (auth.currentUser != null) {
            markOnline()
            onReady()
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener {
                markOnline()
                onReady()
            }
            .addOnFailureListener { e -> onError(e) }
    }

    private fun markOnline() {
        deviceRef.child("status").setValue("online")
        deviceRef.child("lastSeen").setValue(System.currentTimeMillis())
    }

    fun listenForIncomingRequest(onRequest: (viewerId: String) -> Unit) {
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val viewerId = snapshot.child("pendingViewer").getValue(String::class.java)
                if (!viewerId.isNullOrEmpty()) {
                    onRequest(viewerId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef.addValueEventListener(statusListener as ValueEventListener)
    }

    fun clearPendingRequest() {
        deviceRef.child("pendingViewer").removeValue()
    }

    fun stop() {
        statusListener?.let { deviceRef.removeEventListener(it) }
        deviceRef.child("status").setValue("offline")
    }
}
