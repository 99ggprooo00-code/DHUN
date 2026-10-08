package dev.dhun.design

import android.os.Build

/**
 * `Modifier.blur` is a `RenderEffect` on Android: real from API 31, silently
 * ignored below it (minSdk is 24, so API 24–30 is a real population).
 */
actual val supportsRealtimeBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
