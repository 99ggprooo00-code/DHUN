package dev.dhun.legal

/**
 * This build's identity, read from build metadata by the platform shell.
 *
 * Nothing here is ever typed into a document. `legal/about.md` carries
 * `{{appVersion}}`-style tokens and [substitute] replaces them from this value,
 * so the About page cannot show a version that the build does not have.
 *
 * The values differ per platform and deliberately stay that way — Android reads
 * the installed package, Windows reads the installer version handed to the JVM.
 * They do not match each other today (`1.00.001` vs `1.0.6`); that is an open
 * maintainer decision, and showing each platform's own number honestly is better
 * than showing one invented shared number.
 *
 * @param versionName Human version. Never blank: a platform that cannot read its
 *   metadata must pass an explicit sentinel such as `"unknown"`.
 * @param versionCode Android's integer version code; null on desktop, where no
 *   equivalent exists.
 * @param releaseChannel Where this build came from (`public pre-release`,
 *   `development`, …).
 * @param platform `Android` / `Windows` / `JVM desktop`.
 */
data class DhunAppInfo(
    val versionName: String,
    val versionCode: String?,
    val releaseChannel: String,
    val platform: String,
) {
    companion object {
        /**
         * Used when the platform cannot read its own metadata. A visible
         * "unknown" is honest; a plausible-looking guess is not.
         */
        const val UNKNOWN = "unknown"

        val Unknown = DhunAppInfo(
            versionName = UNKNOWN,
            versionCode = null,
            releaseChannel = UNKNOWN,
            platform = UNKNOWN,
        )
    }
}

/**
 * Replaces the render-time tokens the legal generator leaves in place.
 *
 * Only the four tokens [LegalContent] declares are substituted, and any token
 * left over is reported by [unsubstitutedTokens] so a UI can refuse to render a
 * page that still contains one. Showing a reader the literal `{{appVersion}}`
 * is better than showing them a wrong version, but a test should catch it first.
 */
object LegalTokens {

    fun substitute(markdown: String, info: DhunAppInfo): String =
        markdown
            .replace(LegalContent.TOKEN_APP_VERSION, info.versionName)
            .replace(LegalContent.TOKEN_APP_VERSION_CODE, info.versionCode ?: DhunAppInfo.UNKNOWN)
            .replace(LegalContent.TOKEN_RELEASE_CHANNEL, info.releaseChannel)
            .replace(LegalContent.TOKEN_PLATFORM, info.platform)

    private val ANY_TOKEN = Regex("\\{\\{[a-zA-Z][a-zA-Z0-9]*\\}\\}")

    /** Token literals still present after [substitute]; empty means the page is clean. */
    fun unsubstitutedTokens(markdown: String): List<String> =
        ANY_TOKEN.findAll(markdown).map { it.value }.distinct().toList()
}
