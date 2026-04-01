package com.eaglepoint.task136.shared.logging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLoggerTest {

    @Test
    fun `redacts password field`() {
        val input = """password="Admin1234!" other="safe""""
        val result = AppLogger.redact(input)
        assertFalse(result.contains("Admin1234!"))
        assertTrue(result.contains("[REDACTED]"))
        assertTrue(result.contains("safe"))
    }

    @Test
    fun `redacts passwordHash field`() {
        val input = "passwordHash=abcdef1234567890abcdef"
        val result = AppLogger.redact(input)
        assertFalse(result.contains("abcdef1234567890"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun `redacts fingerprint field`() {
        val input = "fingerprint=a1b2c3d4e5f6a7b8c9d0"
        val result = AppLogger.redact(input)
        assertFalse(result.contains("a1b2c3d4e5f6a7b8c9d0"))
    }

    @Test
    fun `redacts walletRef field`() {
        val input = """encryptedWalletRef="secret-wallet-123""""
        val result = AppLogger.redact(input)
        assertFalse(result.contains("secret-wallet-123"))
    }

    @Test
    fun `does not redact safe content`() {
        val input = "Order ord-123 confirmed for user admin"
        val result = AppLogger.redact(input)
        assertTrue(result == input)
    }

    @Test
    fun `log level filtering works`() {
        val captured = mutableListOf<String>()
        val originalSink = AppLogger.sink
        val originalLevel = AppLogger.minLevel
        try {
            AppLogger.sink = { _, _, msg -> captured.add(msg) }
            AppLogger.minLevel = LogLevel.WARN

            AppLogger.d("tag", "debug msg")
            AppLogger.i("tag", "info msg")
            AppLogger.w("tag", "warn msg")
            AppLogger.e("tag", "error msg")

            assertTrue(captured.size == 2)
            assertTrue(captured[0] == "warn msg")
            assertTrue(captured[1] == "error msg")
        } finally {
            AppLogger.sink = originalSink
            AppLogger.minLevel = originalLevel
        }
    }
}
