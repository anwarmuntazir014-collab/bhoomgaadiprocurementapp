package com.example.data.local

import java.security.MessageDigest
import java.security.SecureRandom

object PinSecurity {
    private const val SALT_BYTES = 16

    fun generateSaltHex(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Multi-round salted SHA-256 key stretching for offline 4-digit PINs
        var current = "$saltHex:$pin:bhoomgaadi-v2".toByteArray(Charsets.UTF_8)
        repeat(10_000) {
            digest.reset()
            current = digest.digest(current)
        }
        return current.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, saltHex: String, expectedHash: String): Boolean {
        val candidate = hashPin(pin, saltHex).toByteArray(Charsets.UTF_8)
        val expected = expectedHash.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(candidate, expected)
    }
}
