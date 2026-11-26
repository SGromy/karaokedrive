package com.karaokedrive.lyrics

import kotlin.math.abs

/**
 * Choix entre les paroles ligne par ligne (LRCLIB) et le timing mot par mot (Kugou).
 * Le mot par mot est préféré, sauf s'il semble venir d'une autre version du morceau
 * (durée différente ou paroles décalées) : la boule serait alors fausse partout.
 */
object LyricsChoice {

    fun pick(lineLevel: LyricsLookup, wordLevel: SyncedLyrics?): LyricsLookup {
        if (wordLevel == null) return lineLevel
        return when (lineLevel) {
            is LyricsLookup.Found -> {
                val lines = lineLevel.lyrics
                if (sameVersion(lines, wordLevel)) {
                    LyricsLookup.Found(wordLevel.copy(durationMs = wordLevel.durationMs.takeIf { it > 0 } ?: lines.durationMs))
                } else {
                    lineLevel
                }
            }
            LyricsLookup.NotFound, LyricsLookup.Unavailable -> LyricsLookup.Found(wordLevel)
        }
    }

    private fun sameVersion(lines: SyncedLyrics, words: SyncedLyrics): Boolean {
        if (lines.durationMs > 0 && words.durationMs > 0 && abs(lines.durationMs - words.durationMs) > MAX_DURATION_GAP_MS) return false
        val a = firstLineStart(lines) ?: return true
        val b = firstLineStart(words) ?: return false
        return abs(a - b) <= MAX_START_GAP_MS
    }

    private fun firstLineStart(lyrics: SyncedLyrics): Long? =
        LrcParser.parse(lyrics.lrc, lyrics.durationMs.takeIf { it > 0 } ?: ONE_HOUR_MS).firstOrNull()?.startMs

    /** Écart de durée toléré entre deux sources pour le même enregistrement. */
    private const val MAX_DURATION_GAP_MS = 4_000L
    /** Écart toléré sur le début de la première ligne chantée. */
    private const val MAX_START_GAP_MS = 1_500L
    private const val ONE_HOUR_MS = 3_600_000L
}
