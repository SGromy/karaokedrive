package com.karaokedrive.karaoke

import com.karaokedrive.media.NowPlaying

/** Que faire quand l'application média du téléphone publie une nouvelle information. */
sealed interface MediaDecision {
    /** Rien de nouveau (même morceau, karaoké arrêté, enregistrement…). */
    data object Ignore : MediaDecision

    /** Le morceau a changé mais sans position fiable (radio) : réidentifier tout de suite. */
    data object Rescan : MediaDecision

    /** L'application donne la position exacte : on se cale dessus, sans micro. */
    data class FollowApp(val nowPlaying: NowPlaying) : MediaDecision
}

object MediaPolicy {

    /**
     * @param previousMediaKey morceau publié par l'application à l'information précédente
     * @param recognizedKey morceau actuellement affiché (reconnu ou suivi)
     * @param isKaraokeActive écoute en cours, hors enregistrement
     */
    fun decide(
        nowPlaying: NowPlaying?,
        previousMediaKey: String?,
        recognizedKey: String?,
        isKaraokeActive: Boolean,
    ): MediaDecision {
        if (!isKaraokeActive || nowPlaying == null) return MediaDecision.Ignore
        if (nowPlaying.hasPosition) return MediaDecision.FollowApp(nowPlaying)
        val key = TrackKey.of(nowPlaying.title, nowPlaying.artist)
        return if (key == previousMediaKey || key == recognizedKey) MediaDecision.Ignore else MediaDecision.Rescan
    }
}
