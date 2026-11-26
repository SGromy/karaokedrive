package com.karaokedrive.karaoke

import com.karaokedrive.lyrics.LyricLine

/**
 * Position de la boule : elle vole du mot [fromWord] vers le mot [toWord].
 * `-1` = hors écran à gauche, `words.size` = après le dernier mot.
 * [t] ∈ [0,1] avancement horizontal, [height] ∈ [0,1] hauteur de l'arc (0 = posée).
 */
data class Ball(val fromWord: Int, val toWord: Int, val t: Float, val height: Float)

/** Instantané de ce qu'il faut dessiner à une position donnée du morceau. */
data class KaraokeFrame(
    val lineIndex: Int,
    val wordFills: List<Float>,
    val ball: Ball,
    val isWaiting: Boolean,
    val isFinished: Boolean,
) {
    companion object {
        /** Durée de l'entrée de la boule avant le premier mot d'une ligne. */
        const val LEAD_IN_MS = 1_200L

        private val EMPTY = KaraokeFrame(-1, emptyList(), Ball(-1, 0, 0f, 0f), true, false)

        fun compute(lines: List<LyricLine>, positionMs: Long): KaraokeFrame {
            if (lines.isEmpty()) return EMPTY

            val started = lines.indexOfLast { it.startMs <= positionMs }
            val isLast = started == lines.lastIndex
            val inGap = started == -1 || (positionMs >= lines[started].endMs && !isLast)

            return if (inGap) waitingFrame(lines, started + 1, positionMs)
            else singingFrame(lines, started, positionMs)
        }

        private fun waitingFrame(lines: List<LyricLine>, index: Int, pos: Long): KaraokeFrame {
            val line = lines[index]
            val t = (1f - (line.startMs - pos).toFloat() / LEAD_IN_MS).coerceIn(0f, 1f)
            // Première ligne : la boule entre par la gauche. Ensuite elle attend en bout de ligne
            // (là où elle s'est posée sur le dernier mot) puis saute vers le premier mot.
            val from = if (index == 0) -1 else line.words.size
            return KaraokeFrame(
                lineIndex = index,
                wordFills = List(line.words.size) { 0f },
                ball = Ball(from, 0, t, arc(t)),
                isWaiting = true,
                isFinished = false,
            )
        }

        private fun singingFrame(lines: List<LyricLine>, index: Int, pos: Long): KaraokeFrame {
            val words = lines[index].words
            val fills = words.map { w -> progress(pos, w.startMs, w.endMs) }

            val current = words.indexOfLast { it.startMs <= pos }.coerceAtLeast(0)
            val from = words[current]
            val nextStart = words.getOrNull(current + 1)?.startMs ?: from.endMs
            val t = progress(pos, from.startMs, nextStart)
            // Sur le dernier mot, la boule s'y pose et y reste jusqu'à la fin de la ligne
            val ball = if (current == words.lastIndex || pos >= lines[index].endMs) Ball(words.lastIndex, words.lastIndex, 0f, 0f)
            else Ball(current, current + 1, t, arc(t))

            return KaraokeFrame(
                lineIndex = index,
                wordFills = fills,
                ball = ball,
                isWaiting = false,
                isFinished = index == lines.lastIndex && pos >= lines[index].endMs,
            )
        }

        private fun progress(pos: Long, start: Long, end: Long): Float = when {
            pos <= start -> 0f
            pos >= end -> 1f
            else -> (pos - start).toFloat() / (end - start)
        }

        /** Parabole 0 → 1 → 0 : la boule rebondit sur chaque mot. */
        private fun arc(t: Float): Float = 4f * t * (1f - t)
    }
}
