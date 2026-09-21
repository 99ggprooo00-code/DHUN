package dev.dhun.extraction

/**
 * A stored absolute path is not a URL: Windows separators/drive letters and
 * literal %, #, ?, spaces and Unicode must survive the URL parser unchanged.
 * Kept platform-independent so Windows paths are regression-tested on Linux CI.
 */
internal fun localAudioFileUri(path: String): String {
    val windowsDrive = path.length >= 3 && path[0].isLetter() &&
        path[1] == ':' && (path[2] == '\\' || path[2] == '/')
    val unc = path.startsWith("\\\\")
    val normalized = if (windowsDrive || unc) path.replace('\\', '/') else path
    val prefix = if (windowsDrive) "file:///" else "file://"
    val uriPath = if (unc) normalized.removePrefix("//") else normalized
    return prefix + buildString {
        for (byte in uriPath.encodeToByteArray()) {
            val value = byte.toInt() and 0xff
            val char = value.toChar()
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' ||
                char in "-._~/:"
            ) {
                append(char)
            } else {
                append('%')
                append("0123456789ABCDEF"[value ushr 4])
                append("0123456789ABCDEF"[value and 15])
            }
        }
    }
}
