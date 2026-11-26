package com.karaokedrive.karaoke

/**
 * Quand réécouter. Une détection, une confirmation (une mesure isolée peut se tromper de
 * dizaines de secondes sur un refrain répété), puis une surveillance légère : juste après la
 * dernière ligne des paroles, juste après la fin prévue du morceau, et au moins une fois par
 * minute (la radio coupe ou enchaîne parfois plus tôt que prévu).
 */
object ListenSchedule {

    /** Délai de la mesure de confirmation (et des départages si les mesures divergent). */
    const val CONFIRM_INTERVAL_MS = 10_000L
    /** Au-delà, on arrête de départager (le vote a tranché). */
    const val MAX_TRACK_MEASUREMENTS = 5
    /** Une fois calé : vérification au moins une fois par minute (morceau coupé, enchaînement…). */
    const val WATCH_INTERVAL_MS = 60_000L
    /** Réécoute juste après la fin prévue, pour attraper le début du morceau suivant. */
    const val END_MARGIN_MS = 3_000L
    /** Réécoute peu après la dernière ligne chantée : l'outro est souvent coupée par la radio. */
    const val LYRICS_END_MARGIN_MS = 4_000L
    const val MIN_DELAY_MS = 2_000L
    /** Pubs, animateur, silence : attentes croissantes 3 s, 6 s, 12 s… jusqu'à 60 s. */
    const val MISS_BASE_MS = 3_000L
    const val MISS_MAX_MS = 60_000L

    /**
     * Délai après une reconnaissance réussie.
     * @param measurements mesures déjà faites sur ce morceau (celle-ci comprise)
     * @param confirmed la dernière mesure concorde avec la position affichée
     * @param remainingMs temps restant avant la fin du morceau, null si inconnu
     * @param lyricsEndInMs temps restant avant la fin de la dernière ligne des paroles (négatif
     *   si elle est passée), null sans paroles
     */
    fun afterMatch(measurements: Int, confirmed: Boolean, remainingMs: Long?, lyricsEndInMs: Long? = null): Long {
        val settled = (measurements >= 2 && confirmed) || measurements >= MAX_TRACK_MEASUREMENTS
        val planned = if (settled) WATCH_INTERVAL_MS else CONFIRM_INTERVAL_MS
        val deadlines = listOfNotNull(
            remainingMs?.let { it + END_MARGIN_MS },
            lyricsEndInMs?.takeIf { it > 0 }?.let { it + LYRICS_END_MARGIN_MS },
        )
        return (deadlines + planned).min().coerceAtLeast(MIN_DELAY_MS)
    }

    /** Délai après [consecutiveMisses] échecs d'affilée (morceau non reconnu, erreur réseau…). */
    fun afterMiss(consecutiveMisses: Int): Long {
        val exponent = (consecutiveMisses - 1).coerceIn(0, 10)
        return minOf(MISS_BASE_MS shl exponent, MISS_MAX_MS)
    }
}
