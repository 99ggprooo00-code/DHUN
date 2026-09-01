package dev.dhun.core

/**
 * Maps low-level JVM throwables to the taxonomy. Single choke point so every
 * call site classifies failures identically. (Platform side of the taxonomy;
 * the Android target gets its equivalent in Phase 03.)
 */
fun Throwable.toDhunError(): DhunError = when (this) {
    is DhunException -> error
    is java.util.concurrent.TimeoutException -> DhunError.Network
    is java.net.UnknownHostException -> DhunError.Network
    is java.net.ConnectException -> DhunError.Network
    is java.io.IOException -> DhunError.Network
    else -> DhunError.Unknown(message)
}
