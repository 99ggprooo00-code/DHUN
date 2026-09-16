package dev.dhun.design

/**
 * Whether [androidx.compose.ui.draw.blur] actually renders a blur on this
 * platform right now.
 *
 * It is not universal: Compose's `blur` is a `RenderEffect` layer, so on
 * Android below API 31 the modifier is a **no-op** — the artwork would be drawn
 * sharp. A full-screen sharp album cover behind Home/Search/Library is exactly
 * the "placeholder album art stretched across the screen" the design forbids,
 * so [dev.dhun.design.components.NowPlayingBackdrop] checks this and leaves the
 * existing default background in place instead. Desktop (Skiko) blurs on every
 * version.
 */
expect val supportsRealtimeBlur: Boolean
