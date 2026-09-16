package dev.dhun.design

import android.os.Build

/**
 * `Modifier.blur` is a `RenderEffect` on Android: real from API 31, silently
 * ignored below it (minSdk here is 26).
 */
actual val supportsRealtimeBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
