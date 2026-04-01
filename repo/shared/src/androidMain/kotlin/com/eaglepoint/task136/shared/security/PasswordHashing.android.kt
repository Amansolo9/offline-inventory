package com.eaglepoint.task136.shared.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual fun sha256Hex(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { each -> "%02x".format(each) }
}

actual fun pbkdf2Hash(password: String, salt: String, iterations: Int): String {
    val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), iterations, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val hash = factory.generateSecret(spec).encoded
    return hash.joinToString("") { "%02x".format(it) }
}

actual fun secureRandomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

actual fun constantTimeEquals(a: String, b: String): Boolean {
    return MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
