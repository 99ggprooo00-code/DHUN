package dev.dhun.design

/**
 * Whether [androidx.compose.ui.draw.blur] actually renders a blur on this
 * platform right now.
 *
 * Compose's `blur` is a `RenderEffect` layer, so on Android below API 31 the
 * modifier is a **no-op**. Callers still paint the now-playing thumbnail on
 * those devices (dimmed, user-controlled brightness) rather than hiding it.
 * Desktop (Skiko) blurs on every version.
 */
expect val supportsRealtimeBlur: Boolean
