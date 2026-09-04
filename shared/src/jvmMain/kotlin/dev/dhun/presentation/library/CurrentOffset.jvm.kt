package dev.dhun.presentation.library

actual fun currentUtcOffsetMs(): Long {
    val tz = java.util.TimeZone.getDefault()
    return tz.getOffset(System.currentTimeMillis()).toLong()
}
