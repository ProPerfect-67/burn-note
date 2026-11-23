package com.example.burnnote

import android.content.Context
import android.util.Base64
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedBundle(
    val noteId: String,
    val cipherCode: String,
    val oneTimeKey: String
)

object BurnCrypto {
    private const val CIPHER_PREFIX = "BURN2:"
    private const val KEY_PREFIX = "KEY2:"
    private const val KEY_SIZE_BYTES = 32 // 256-bit AES
    private const val IV_SIZE_BYTES = 12  // 96-bit GCM nonce
    private const val TAG_SIZE_BITS = 128
    private const val NOTE_ID_BYTES = 8
    private const val B64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    private val secureRandom = SecureRandom()

    /**
     * Encrypts plaintext locally using a fresh random 256-bit AES-GCM key.
     * Wipes input CharArray and temporary key/plaintext byte arrays before returning.
     */
    fun encrypt(plaintext: CharArray): EncryptedBundle {
        val noteIdBytes = ByteArray(NOTE_ID_BYTES)
        val keyBytes = ByteArray(KEY_SIZE_BYTES)
        val ivBytes = ByteArray(IV_SIZE_BYTES)
        var plainBytes: ByteArray? = null

        try {
            secureRandom.nextBytes(noteIdBytes)
            secureRandom.nextBytes(keyBytes)
            secureRandom.nextBytes(ivBytes)

            val noteIdHex = noteIdBytes.joinToString("") { "%02X".format(it) }

            val bb = Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .encode(CharBuffer.wrap(plaintext))
            plainBytes = ByteArray(bb.remaining()).also { bb.get(it) }
            if (bb.hasArray()) bb.array().fill(0)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_SIZE_BITS, ivBytes)
            )
            // Bind Note ID as AAD so it cannot be altered to bypass the burn ledger
            cipher.updateAAD(noteIdHex.toByteArray(Charsets.UTF_8))

            val encrypted = cipher.doFinal(plainBytes)
            val combined = ByteArray(ivBytes.size + encrypted.size)
            System.arraycopy(ivBytes, 0, combined, 0, ivBytes.size)
            System.arraycopy(encrypted, 0, combined, ivBytes.size, encrypted.size)

            val cipherCode = "$CIPHER_PREFIX$noteIdHex:${Base64.encodeToString(combined, B64_FLAGS)}"
            val keyCode = "$KEY_PREFIX${Base64.encodeToString(keyBytes, B64_FLAGS)}"

            return EncryptedBundle(
                noteId = noteIdHex,
                cipherCode = cipherCode,
                oneTimeKey = keyCode
            )
        } finally {
            plaintext.fill('\u0000')
            plainBytes?.fill(0)
            keyBytes.fill(0)
            ivBytes.fill(0)
        }
    }

    /**
     * Parses and validates the Note ID from a BURN2 cipher string.
     */
    fun extractNoteId(rawCipherCode: String): String? {
        val cleaned = rawCipherCode.trim()
        if (!cleaned.startsWith(CIPHER_PREFIX)) return null
        val body = cleaned.removePrefix(CIPHER_PREFIX)
        val parts = body.split(":")
        if (parts.size != 2) return null
        val noteId = parts[0].uppercase()
        if (noteId.length != NOTE_ID_BYTES * 2 || !noteId.all { it in '0'..'9' || it in 'A'..'F' }) {
            return null
        }
        return if (parts[1].isNotEmpty()) noteId else null
    }

    /**
     * Decrypts a BURN2 cipher string with its KEY2 one-time key into a raw UTF-8 ByteArray.
     * Caller MUST overwrite the returned ByteArray with zeros after staging it.
     */
    fun decryptToBytes(rawCipherCode: String, rawKeyCode: String): ByteArray {
        val cleanedCipher = rawCipherCode.trim()
        val cleanedKey = rawKeyCode.trim()

        require(cleanedCipher.startsWith(CIPHER_PREFIX)) { "Encrypted note must start with BURN2:" }
        require(cleanedKey.startsWith(KEY_PREFIX)) { "One-time key must start with KEY2:" }

        val parts = cleanedCipher.removePrefix(CIPHER_PREFIX).split(":")
        require(parts.size == 2) { "Malformed encrypted note format." }

        val noteIdHex = parts[0].uppercase()
        val combined = Base64.decode(parts[1], B64_FLAGS)
        require(combined.size > IV_SIZE_BYTES + 16) { "Corrupted encrypted note payload." }

        val keyBytes = Base64.decode(cleanedKey.removePrefix(KEY_PREFIX), B64_FLAGS)
        val ivBytes = combined.copyOfRange(0, IV_SIZE_BYTES)
        val cipherBytes = combined.copyOfRange(IV_SIZE_BYTES, combined.size)

        try {
            require(keyBytes.size == KEY_SIZE_BYTES) { "Invalid one-time key length." }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_SIZE_BITS, ivBytes)
            )
            cipher.updateAAD(noteIdHex.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(cipherBytes)
        } finally {
            keyBytes.fill(0)
            ivBytes.fill(0)
            combined.fill(0)
        }
    }
}

/**
 * Local anti-replay ledger storing only SHA-256 hashes of opened Note IDs
 * so burned notes cannot be re-opened on the same device.
 */
object BurnLedger {
    private const val PREFS_NAME = "burn_note_ledger"

    private fun hashId(noteId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(noteId.uppercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isBurned(context: Context, noteId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(hashId(noteId), false)
    }

    fun markBurned(context: Context, noteId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Synchronous commit ensures force-closing the app cannot undo the burn
        prefs.edit().putBoolean(hashId(noteId), true).commit()
    }
}
