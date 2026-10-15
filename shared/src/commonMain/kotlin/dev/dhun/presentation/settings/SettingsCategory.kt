package dev.dhun.presentation.settings

/** Only categories backed by working controls. Search is local and never persisted/uploaded. */
enum class SettingsCategory(
    val id: String,
    val title: String,
    val summary: String,
    val group: String,
    private val keywords: String,
) {
    APPEARANCE("appearance", "Appearance", "Theme, dynamic artwork colors, accents and backdrop", "Interface", "light dark black amoled oled system palette blur brightness"),
    PLAYBACK("playback", "Player & audio", "Quality, seek controls, radio and available equalizer", "Music", "stream download resume shuffle repeat mute sound"),
    LYRICS("lyrics", "Lyrics & romanization", "Providers, synced words, following and transliteration", "Music", "lrc karaoke language text size romanized japanese korean chinese russian"),
    CONTENT("content", "Content & discovery", "Region and explicit-content preferences", "Music", "country filter recommendations"),
    PRIVACY("privacy", "Privacy & history", "Listening history controls and local-data cleanup", "Your data", "record pause private clear searches listening"),
    STORAGE("storage", "Storage & cache", "Streamed audio budget and cache cleanup", "Your data", "space disk downloads offline clear"),
    DESKTOP("desktop", "Desktop & window", "Tray behavior and window placement", "System", "close minimize geometry reset"),
    ABOUT("about", "About & legal", "Privacy notices, licenses, support and reset", "System", "terms version help feedback defaults"),
    ;

    companion object {
        fun search(query: String, isDesktop: Boolean): List<SettingsCategory> {
            val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            return entries.filter { category ->
                (isDesktop || category != DESKTOP) && tokens.all {
                    it in "${category.title} ${category.summary} ${category.keywords}".lowercase()
                }
            }
        }
    }
}
