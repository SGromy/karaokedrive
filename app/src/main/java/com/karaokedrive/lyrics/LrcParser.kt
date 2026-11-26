package com.karaokedrive.lyrics

/**
 * Parse le format LRC (`[mm:ss.xx]texte`) et sa variante enrichie
 * (`[mm:ss.xx]<mm:ss.xx>mot <mm:ss.xx>mot`).
 *
 * Quand la source ne fournit que le timing des lignes, le timing des mots est
 * estimé au prorata de leurs syllabes, à un débit de chant plafonné : après une
 * phrase courte, la boule attend la ligne suivante au lieu de ramper sur la pause.
 */
object LrcParser {

    /** Durée max d'une ligne (évite une dernière ligne étirée sur un solo). */
    const val MAX_LINE_MS = 8_000L

    /** Durée max d'une syllabe chantée : au-delà, on considère que c'est une pause. */
    const val MAX_SYLLABLE_MS = 550L

    /** Durée max d'un mot balisé (LRC enrichi) : une note tenue, pas un silence. */
    const val MAX_TAGGED_WORD_MS = 3_000L

    private val VOWEL_GROUP = Regex("[aeiouyàâäéèêëîïôöùûüÿœæ]+", RegexOption.IGNORE_CASE)
    private val SILENT_ENDING = Regex("(?<=[^aeiouyàâäéèêëîïôöùûüÿœæ])e(s|nt)?$", RegexOption.IGNORE_CASE)

    private val LINE_TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")

    private data class RawLine(val startMs: Long, val body: String)

    fun parse(lrc: String, trackDurationMs: Long): List<LyricLine> {
        val raw = lrc.lineSequence()
            .flatMap(::extractRawLines)
            .filter { plainText(it.body).isNotBlank() }
            .sortedBy { it.startMs }
            .toList()

        return raw.mapIndexed { index, line ->
            val nextStart = raw.getOrNull(index + 1)?.startMs ?: trackDurationMs
            val endMs = minOf(nextStart, line.startMs + MAX_LINE_MS)
                .coerceAtLeast(line.startMs + 1)
            LyricLine(line.startMs, endMs, parseWords(line.body, line.startMs, endMs))
        }
    }

    private fun extractRawLines(source: String): List<RawLine> {
        val tags = generateSequence(LINE_TAG.matchAt(source, 0)) { prev ->
            LINE_TAG.matchAt(source, prev.range.last + 1)
        }.toList()
        if (tags.isEmpty()) return emptyList()
        val body = source.substring(tags.last().range.last + 1).trim()
        return tags.map { RawLine(toMs(it), body) }
    }

    private fun parseWords(body: String, lineStart: Long, lineEnd: Long): List<LyricWord> {
        val tagged = parseTaggedWords(body, lineEnd)
        return tagged ?: interpolateWords(plainText(body), lineStart, lineEnd)
    }

    private fun parseTaggedWords(body: String, lineEnd: Long): List<LyricWord>? {
        val tags = WORD_TAG.findAll(body).toList()
        if (tags.isEmpty()) return null

        // Chaque balise ouvre un segment ; un segment peut contenir plusieurs mots.
        val segments = tags.mapIndexed { i, tag ->
            val textEnd = tags.getOrNull(i + 1)?.range?.first ?: body.length
            val segEnd = tags.getOrNull(i + 1)?.let(::toMs) ?: lineEnd
            Triple(toMs(tag), segEnd, body.substring(tag.range.last + 1, textEnd).trim())
        }.filter { it.third.isNotEmpty() }

        return segments.flatMap { (start, end, text) ->
            val safeEnd = maxOf(end, start + 1)
            // Mot balisé seul : vrai timing de la source (note tenue comprise), dans une limite raisonnable
            if (text.none(Char::isWhitespace)) listOf(LyricWord(text, start, minOf(safeEnd, start + MAX_TAGGED_WORD_MS)))
            else interpolateWords(text, start, safeEnd)
        }.ifEmpty { null }
    }

    private fun interpolateWords(text: String, start: Long, end: Long): List<LyricWord> {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val weights = tokens.map(::syllables)
        val totalWeight = weights.sum().coerceAtLeast(1)
        val span = minOf(end - start, totalWeight * MAX_SYLLABLE_MS)
        var cursor = 0
        return tokens.mapIndexed { i, token ->
            val wordStart = start + span * cursor / totalWeight
            cursor += weights[i]
            val wordEnd = start + span * cursor / totalWeight
            LyricWord(token, wordStart, wordEnd)
        }
    }

    /**
     * Estimation grossière du nombre de syllabes (français / anglais) : groupes de
     * voyelles, sans le « e » muet final quand le mot en a d'autres.
     */
    fun syllables(word: String): Int {
        val letters = word.filter(Char::isLetter)
        val groups = VOWEL_GROUP.findAll(letters).count()
        val muted = if (groups > 1 && SILENT_ENDING.containsMatchIn(letters)) 1 else 0
        return (groups - muted).coerceAtLeast(1)
    }

    private fun plainText(body: String): String = WORD_TAG.replace(body, "").trim()

    private fun toMs(match: MatchResult): Long {
        val (min, sec, frac) = match.destructured
        val fracMs = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').toLong()
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
    }
}
