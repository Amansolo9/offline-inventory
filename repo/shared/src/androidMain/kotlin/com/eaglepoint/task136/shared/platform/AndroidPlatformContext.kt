package com.eaglepoint.task136.shared.platform

import android.content.Context

object AndroidPlatformContext {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun require(): Context = checkNotNull(appContext) {
        "AndroidPlatformContext is not initialized"
    }
}
