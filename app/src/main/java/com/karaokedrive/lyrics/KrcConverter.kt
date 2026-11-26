package com.karaokedrive.lyrics

import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Format KRC de Kugou : paroles avec le vrai timing de chaque mot (début et durée).
 * Fichier chiffré (« krc1 » + XOR avec une clé publique + zlib), puis lignes
 * `[début,durée]<décalage,durée,0>mot<décalage,durée,0>mot…` en millisecondes.
 * Format documenté notamment par Lyricify-Lyrics-Helper (Apache-2.0). Calcul pur et testable.
 */
object KrcConverter {

    /** Clé XOR publique du format KRC. */
    val KEY = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)

    private val HEADER = "krc1".toByteArray()
    private val LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val WORD = Regex("""<(\d+),(\d+),\d+>([^<]*)""")
    /** Lignes de crédits (« Lyrics by： », « 作词： »…) : deux-points chinois ou mots-clés chinois. */
    private val CREDIT = Regex("""[：]|作词|作曲|编曲|制作|混音|和声|监制""")
    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")

    /** Contenu texte d'un fichier KRC chiffré ; null s'il n'est pas au bon format. */
    fun decrypt(data: ByteArray): String? {
        if (data.size <= HEADER.size || !data.copyOfRange(0, HEADER.size).contentEquals(HEADER)) return null
        val body = ByteArray(data.size - HEADER.size) { i -> (data[i + HEADER.size].toInt() xor KEY[i % KEY.size]).toByte() }
        val inflater = Inflater()
        return try {
            inflater.setInput(body)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
            }
            out.toString(Charsets.UTF_8.name())
        } catch (e: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    /**
     * LRC enrichi (`[mm:ss.mmm]<mm:ss.mmm>mot …`) : une balise par mot, et une balise de fin
     * quand le mot s'arrête avant le suivant (la boule attend au lieu de glisser).
     * Les syllabes découpées (« chan » + « te ») sont regroupées en un mot.
     * @return null si aucune ligne chantée
     */
    fun toEnhancedLrc(krc: String, title: String): String? {
        val lines = krc.lineSequence().mapNotNull { LINE.find(it.trim()) }.toList()
        val sung = lines.mapIndexedNotNull { index, match ->
            val (start, _, body) = match.destructured
            val words = mergeSyllables(start.toLong(), body)
            val text = words.joinToString(" ") { it.text }
            when {
                words.isEmpty() -> null
                CREDIT.containsMatchIn(text) -> null
                // En-tête « titre - artiste » en tout début de fichier
                index < HEADER_LINES && text.contains(" - ") && normalize(text).contains(normalize(title)) -> null
                else -> formatLine(start.toLong(), words)
            }
        }
        return sung.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private data class Word(val text: String, val startMs: Long, val endMs: Long)

    private fun mergeSyllables(lineStart: Long, body: String): List<Word> {
        val words = mutableListOf<Word>()
        var pending: Word? = null
        for (token in WORD.findAll(body)) {
            val (offset, duration, raw) = token.destructured
            val start = lineStart + offset.toLong()
            val end = start + duration.toLong()
            val text = raw.trim()
            if (text.isNotEmpty()) {
                pending = pending?.let { it.copy(text = it.text + text, endMs = end) } ?: Word(text, start, end)
            }
            // Un espace (ou la fin) termine le mot en cours
            if (raw.isEmpty() || raw.last().isWhitespace()) {
                pending?.let(words::add)
                pending = null
            }
        }
        pending?.let(words::add)
        return words
    }

    private fun formatLine(lineStart: Long, words: List<Word>): String = buildString {
        append('[').append(time(lineStart)).append(']')
        words.forEachIndexed { i, word ->
            append('<').append(time(word.startMs)).append('>').append(word.text).append(' ')
            val next = words.getOrNull(i + 1)?.startMs
            if (next == null || next - word.endMs > PAUSE_MS) append('<').append(time(word.endMs)).append('>')
        }
    }.trimEnd()

    private fun time(ms: Long) = "%02d:%02d.%03d".format(ms / 60_000, ms / 1000 % 60, ms % 1000)

    private fun normalize(value: String): String {
        val ascii = DIACRITICS.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(ascii.lowercase(), " ").trim()
    }

    /** Écart au-delà duquel on marque la fin du mot (la boule s'arrête pendant la pause). */
    private const val PAUSE_MS = 250L
    /** Le titre et les crédits occupent les toutes premières lignes. */
    private const val HEADER_LINES = 3
    private const val BUFFER_SIZE = 8 * 1024
}
