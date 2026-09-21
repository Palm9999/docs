package com.sitorplay.app.domain.model

/**
 * How much of the week's practice a player took part in.
 *
 * This is a far stronger signal than the game-day designation it accompanies:
 * across 2023-2025, Questionable players who practised in full played 65% of the
 * time against 40% for those who did not practise at all. Sleeper returns it on
 * the same payload the app already reads for injury status.
 */
enum class PracticeParticipation(val label: String) {
    FULL("Full practice"),
    LIMITED("Limited practice"),
    DID_NOT_PRACTICE("Did not practice"),
    UNKNOWN("No practice report");

    companion object {
        /** Parses Sleeper's `practice_participation`, which is free text. */
        fun fromSleeper(raw: String?): PracticeParticipation {
            val value = raw?.trim()?.lowercase().orEmpty()
            return when {
                value.isEmpty() -> UNKNOWN
                value.startsWith("full") -> FULL
                value.startsWith("limited") -> LIMITED
                value.startsWith("did not") || value == "dnp" -> DID_NOT_PRACTICE
                else -> UNKNOWN
            }
        }
    }
}
