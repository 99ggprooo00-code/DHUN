package dev.dhun.design

/**
 * Desktop renders `Modifier.blur` through Skia on every supported JVM, so the
 * blurred now-playing backdrop is always available on Windows/Linux/macOS.
 */
actual val supportsRealtimeBlur: Boolean
    get() = true
