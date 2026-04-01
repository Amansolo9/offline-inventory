package com.eaglepoint.task136.shared.logging

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

object AppLogger {
    var minLevel: LogLevel = LogLevel.INFO

    var sink: (LogLevel, String, String) -> Unit = { level, tag, message ->
        println("[${level.name}] $tag: $message")
    }

    private val sensitivePatterns = listOf(
        Regex("password[\"=:]\\s*[\"']?[^\"'\\s,}]+", RegexOption.IGNORE_CASE),
        Regex("passwordHash[\"=:]\\s*[\"']?[a-f0-9]{8,}", RegexOption.IGNORE_CASE),
        Regex("passwordSalt[\"=:]\\s*[\"']?[a-f0-9]{8,}", RegexOption.IGNORE_CASE),
        Regex("fingerprint[\"=:]\\s*[\"']?[a-f0-9]{8,}", RegexOption.IGNORE_CASE),
        Regex("walletRef[\"=:]\\s*[\"']?[^\"'\\s,}]+", RegexOption.IGNORE_CASE),
        Regex("encryptedWalletRef[\"=:]\\s*[\"']?[^\"'\\s,}]+", RegexOption.IGNORE_CASE),
        Regex("maskedPII[\"=:]\\s*[\"']?[^\"'\\s,}]+", RegexOption.IGNORE_CASE),
    )

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    private fun log(level: LogLevel, tag: String, message: String) {
        if (level.ordinal >= minLevel.ordinal) {
            sink(level, tag, redact(message))
        }
    }

    internal fun redact(message: String): String {
        var result = message
        sensitivePatterns.forEach { pattern ->
            result = pattern.replace(result, "[REDACTED]")
        }
        return result
    }
}
