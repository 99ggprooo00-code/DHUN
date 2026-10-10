package dev.dhun.design

import android.os.Build

/**
 * `Modifier.blur` is a `RenderEffect` on Android: real from API 31, silently
 * ignored below it (minSdk is 24). API 24–30 still show the now-playing
 * thumbnail, dimmed, with a brightness control instead of blur.
 */
actual val supportsRealtimeBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
