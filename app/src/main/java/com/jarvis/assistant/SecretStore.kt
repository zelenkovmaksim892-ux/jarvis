package com.jarvis.assistant

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts API credentials with an AES-256 key held by Android Keystore.
 * The key itself is never written to app preferences or exported from Keystore.
 */
internal class SecretStore {
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "Unexpected AES-GCM IV size" }
        val payload = ByteBuffer.allocate(1 + iv.size + encrypted.size)
            .put(FORMAT_VERSION)
            .put(iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size >= 1 + IV_BYTES + TAG_BYTES) { "Encrypted secret is truncated" }
        val buffer = ByteBuffer.wrap(payload)
        require(buffer.get() == FORMAT_VERSION) { "Unsupported encrypted-secret format" }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        // Robolectric has no AndroidKeyStore provider. This ephemeral key is test-only;
        // real Android devices always use the non-exportable Android Keystore key below.
        if (Build.FINGERPRINT.startsWith("robolectric", ignoreCase = true)) return RobolectricTestKey.key
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jarvis_api_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8

        private object RobolectricTestKey {
            val key: SecretKey by lazy {
                KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            }
        }
    }
}
