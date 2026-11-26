package com.karaokedrive.media

/**
 * Ce que joue une application du téléphone, d'après sa session média.
 *
 * [positionMs] n'est fiable que si [hasPosition] : les radios en direct publient
 * le morceau mais pas la position dans le morceau.
 */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    /** Horloge monotone (elapsedRealtime) à laquelle [positionMs] a été mesurée. */
    val positionUpdatedAtMs: Long,
    val speed: Float,
    /** Nom lisible de l'application (« Radios France », « Spotify »…). */
    val appLabel: String,
) {
    val hasPosition: Boolean get() = durationMs > 0 && positionMs > 0

    /** Position extrapolée à l'instant [nowMs] (même horloge que [positionUpdatedAtMs]). */
    fun positionAt(nowMs: Long): Long = positionMs + ((nowMs - positionUpdatedAtMs) * speed).toLong()
}

object NowPlayingParser {

    /** « Artiste — Titre » tel que publié par les radios dans un seul champ. */
    private val COMBINED = Regex("""^\s*(.+?)\s+[—–-]\s+(.+?)\s*$""")

    /** Infos de station publiées entre deux morceaux (« Fleurance - 91.0 FM »). */
    private val STATION_INFO = Regex("""\b\d{2,3}(?:[.,]\d{1,2})?\s*(?:FM|MHz)\b|\bDAB\+?""", RegexOption.IGNORE_CASE)

    fun parse(
        packageName: String,
        title: String?,
        artist: String?,
        displaySubtitle: String?,
        durationMs: Long,
        positionMs: Long,
        positionUpdatedAtMs: Long,
        speed: Float,
        appLabel: String = packageName,
    ): NowPlaying? {
        val (songArtist, songTitle) = songOf(title, artist, displaySubtitle) ?: return null
        if (STATION_INFO.containsMatchIn(songArtist) || STATION_INFO.containsMatchIn(songTitle)) return null
        return NowPlaying(packageName, songTitle, songArtist, durationMs, positionMs, positionUpdatedAtMs, speed, appLabel)
    }

    /** (artiste, titre) du morceau, ou null si les métadonnées ne décrivent pas de chanson. */
    private fun songOf(title: String?, artist: String?, subtitle: String?): Pair<String, String>? {
        // Radios : le morceau est souvent « Artiste — Titre » dans l'artiste ou le sous-titre,
        // tandis que le titre contient le nom de la station.
        listOf(artist, subtitle).firstNotNullOfOrNull { field ->
            field?.let(COMBINED::find)?.destructured?.let { (a, t) -> a to t }
        }?.let { return it }

        val cleanTitle = title?.trim().orEmpty()
        val cleanArtist = (artist ?: subtitle)?.trim().orEmpty()
        return if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) cleanArtist to cleanTitle else null
    }
}
