package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val cipherTextBase64: String,
    val ivBase64: String,
    val keyFingerprint: String
)

object MessageCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private val secureRandom = SecureRandom()

    private fun deriveSessionKey(userId: String, companionId: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val seed = "havenly-e2e-v1:$userId:$companionId".toByteArray(Charsets.UTF_8)
        val keyBytes = digest.digest(seed)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun fingerprint(userId: String, companionId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = deriveSessionKey(userId, companionId).encoded
        val hash = digest.digest(key)
        val hex = hash.take(6).joinToString("") { "%02X".format(it) }
        return "SHA256:$hex"
    }

    fun encrypt(plaintext: String, userId: String, companionId: String): EncryptedPayload {
        val key = deriveSessionKey(userId, companionId)
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            cipherTextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            keyFingerprint = fingerprint(userId, companionId)
        )
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String, userId: String, companionId: String): String {
        return try {
            val key = deriveSessionKey(userId, companionId)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            "[Encrypted payload unavailable]"
        }
    }
}
