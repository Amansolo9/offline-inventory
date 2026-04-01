package com.eaglepoint.task136.shared.security

expect fun sha256Hex(value: String): String

expect fun pbkdf2Hash(password: String, salt: String, iterations: Int = 120_000): String

expect fun secureRandomHex(byteCount: Int = 16): String

expect fun constantTimeEquals(a: String, b: String): Boolean
