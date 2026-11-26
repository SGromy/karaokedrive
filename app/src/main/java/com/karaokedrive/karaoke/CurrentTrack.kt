package com.karaokedrive.karaoke

/**
 * Le morceau en cours, partagé par l'écoute micro, le suivi d'une application média et
 * l'entraînement : sert à reconnaître un changement de morceau et à ignorer les
 * résultats (pochette, traduction) arrivés pour un morceau déjà passé.
 */
class CurrentTrack {
    /** Identifiant stable ([TrackKey.of]) ; null = aucun morceau. */
    @Volatile var key: String? = null
    /** Durée retenue : celle de la reconnaissance, sinon celle des paroles ; 0 si inconnue. */
    @Volatile var durationMs: Long = 0
    /** Paroles LRC affichées, pour pouvoir les ajouter aux favoris. */
    @Volatile var lrc: String? = null

    fun clear() {
        key = null
        durationMs = 0
        lrc = null
    }
}
