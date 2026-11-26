package com.karaokedrive.lyrics

/**
 * Variantes de recherche des paroles : la reconnaissance renvoie souvent des titres
 * « décorés » (« (Single Edit) », « - Remastered 2011 », « feat. … ») sous lesquels la
 * base de paroles ne trouve rien, alors que le titre simple existe.
 */
object LyricsQuery {

    private val FEATURING = Regex("""\s+[(\[]?(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val DASH_SUFFIX = Regex("""\s+[-–—]\s+.*\b(remaster\w*|edit|version|mix|live|radio|single|mono|stereo)\b.*$""", RegexOption.IGNORE_CASE)
    private val ARTIST_SEPARATOR = Regex("""\s*(?:,|&|/|\bx\b|\bet\b|\band\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)

    /** (titre, artiste) à essayer dans l'ordre, du plus précis au plus large, sans doublon. */
    fun variants(title: String, artist: String): List<Pair<String, String>> {
        val cleanTitle = cleanTitle(title)
        val cleanArtist = FEATURING.replace(artist, "").trim()
        val mainArtist = ARTIST_SEPARATOR.split(cleanArtist).first().trim()
        return listOf(
            title.trim() to artist.trim(),
            cleanTitle to cleanArtist,
            cleanTitle to mainArtist,
        ).filter { (t, a) -> t.isNotBlank() && a.isNotBlank() }.distinct()
    }

    fun cleanTitle(title: String): String =
        DASH_SUFFIX.replace(BRACKETS.replace(FEATURING.replace(title, ""), ""), "").trim()
}
