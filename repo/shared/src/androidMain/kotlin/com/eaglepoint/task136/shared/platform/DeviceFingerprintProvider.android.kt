package com.eaglepoint.task136.shared.platform

import android.os.Build
import com.eaglepoint.task136.shared.security.sha256Hex

actual fun getDeviceFingerprint(): String {
    val raw = "${Build.BOARD}|${Build.BRAND}|${Build.DEVICE}|${Build.MODEL}|${Build.MANUFACTURER}|${Build.HARDWARE}"
    return sha256Hex(raw).take(32)
}
