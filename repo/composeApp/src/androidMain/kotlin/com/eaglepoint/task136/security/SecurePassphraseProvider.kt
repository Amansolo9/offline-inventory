package com.eaglepoint.task136.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecurePassphraseProvider(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("task136_secure_db", Context.MODE_PRIVATE)

    fun getOrCreatePassphrase(): ByteArray {
        val encrypted = prefs.getString("encrypted_passphrase", null)
        val iv = prefs.getString("passphrase_iv", null)
        if (encrypted != null && iv != null) {
            return decrypt(Base64.decode(encrypted, Base64.DEFAULT), Base64.decode(iv, Base64.DEFAULT))
        }

        val passphrase = ByteArray(32)
        SecureRandom().nextBytes(passphrase)
        val (cipherBytes, cipherIv) = encrypt(passphrase)
        prefs.edit()
            .putString("encrypted_passphrase", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            .putString("passphrase_iv", Base64.encodeToString(cipherIv, Base64.NO_WRAP))
            .apply()

        return passphrase
    }

    private fun encrypt(cleartext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.doFinal(cleartext) to cipher.iv
    }

    private fun decrypt(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateKey(): SecretKey {
        val alias = "task136-db-key"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }
}
