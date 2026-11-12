package com.remotecamera.device

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

object DeviceIdentity {

    private const val PREFS_NAME = "device_identity_prefs"
    private const val KEY_DEVICE_ID_ENCRYPTED = "device_id_encrypted"
    private const val KEY_IV = "device_id_iv"
    private const val KEYSTORE_ALIAS = "remote_camera_device_identity_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingEncrypted = prefs.getString(KEY_DEVICE_ID_ENCRYPTED, null)
        val existingIv = prefs.getString(KEY_IV, null)

        if (existingEncrypted != null && existingIv != null) {
            return decrypt(existingEncrypted, existingIv)
        }

        val newId = UUID.randomUUID().toString()
        val (encrypted, iv) = encrypt(newId)

        prefs.edit()
            .putString(KEY_DEVICE_ID_ENCRYPTED, encrypted)
            .putString(KEY_IV, iv)
            .apply()

        return newId
    }

    private fun getOrCreateSecretKey(): javax.crypto.SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null)
        if (existingKey != null) {
            return existingKey as javax.crypto.SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encodedCipherText = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val encodedIv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        return Pair(encodedCipherText, encodedIv)
    }

    private fun decrypt(encodedCipherText: String, encodedIv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val decryptedBytes = cipher.doFinal(Base64.decode(encodedCipherText, Base64.NO_WRAP))
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
