package dev.dhun.domain

/** Explicit topic searches, not invented YouTube personalized mood endpoints. */
enum class HomeMood(val label: String, val query: String? = null) {
    FOR_YOU("For you"),
    FOCUS("Focus", "focus instrumental music"),
    CHILL("Chill", "chill music"),
    WORKOUT("Workout", "workout music"),
    PARTY("Party", "party music"),
}
