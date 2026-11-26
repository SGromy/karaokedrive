package com.karaokedrive.karaoke

import java.text.Normalizer

/**
 * Identifiant stable d'un morceau : la reconnaissance renvoie parfois le même titre
 * avec des variantes (’ vs ', « (Radio Edit) », « feat. »…), qui ne doivent pas
 * être prises pour un changement de morceau.
 */
object TrackKey {

    private val BRACKETS = Regex("""[(\[].*?[)\]]""")
    private val FEATURING = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")
    private val FILE_UNSAFE = Regex("""[^a-z0-9]+""")

    fun of(title: String, artist: String): String = "${clean(artist)}|${clean(title)}"

    /** Nom du fichier de paroles alignées (même règle que tools/aligner/align.py). */
    fun fileName(title: String, artist: String): String = fileStem(of(title, artist)) + ".lrc"

    /** Version d'une clé [of] utilisable comme nom de fichier (caches). */
    fun fileStem(key: String): String = FILE_UNSAFE.replace(key, "_")

    private fun clean(value: String): String {
        val stripped = FEATURING.replace(BRACKETS.replace(value, " "), "")
        val ascii = DIACRITICS.replace(Normalizer.normalize(stripped, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(ascii.lowercase(), " ").trim()
    }
}
