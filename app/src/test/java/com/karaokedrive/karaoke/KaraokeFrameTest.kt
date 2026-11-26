package com.karaokedrive.karaoke

import com.karaokedrive.lyrics.LyricLine
import com.karaokedrive.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeFrameTest {

    private val line0 = LyricLine(
        startMs = 1_000, endMs = 3_000,
        words = listOf(LyricWord("un", 1_000, 2_000), LyricWord("deux", 2_000, 3_000)),
    )
    private val line1 = LyricLine(
        startMs = 5_000, endMs = 6_000,
        words = listOf(LyricWord("trois", 5_000, 6_000)),
    )
    private val lines = listOf(line0, line1)

    @Test
    fun `avant la première ligne on attend la ligne 0`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 0)

        assertEquals(0, frame.lineIndex)
        assertTrue(frame.isWaiting)
        assertEquals(listOf(0f, 0f), frame.wordFills)
    }

    @Test
    fun `au milieu d'un mot le remplissage est partiel et la boule vole vers le suivant`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 1_500)

        assertEquals(0, frame.lineIndex)
        assertFalse(frame.isWaiting)
        assertEquals(0.5f, frame.wordFills[0], 0.001f)
        assertEquals(0f, frame.wordFills[1], 0.001f)
        assertEquals(0, frame.ball.fromWord)
        assertEquals(1, frame.ball.toWord)
        assertEquals(0.5f, frame.ball.t, 0.001f)
        assertEquals(1f, frame.ball.height, 0.001f) // sommet de l'arc
    }

    @Test
    fun `la boule touche le mot pile à son début`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 2_000)

        assertEquals(1, frame.ball.fromWord)
        assertEquals(0f, frame.ball.height, 0.001f)
    }

    @Test
    fun `entre deux lignes la boule attend en bout de ligne suivante`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 3_500)

        assertEquals(1, frame.lineIndex)
        assertTrue(frame.isWaiting)
        assertEquals(line1.words.size, frame.ball.fromWord) // posée après le dernier mot
        assertEquals(0, frame.ball.toWord)
        assertEquals(0f, frame.ball.t, 0.001f)
        assertEquals(0f, frame.ball.height, 0.001f)
    }

    @Test
    fun `juste avant la ligne la boule saute vers le premier mot`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 5_000 - KaraokeFrame.LEAD_IN_MS / 2)

        assertEquals(line1.words.size, frame.ball.fromWord)
        assertEquals(0, frame.ball.toWord)
        assertEquals(0.5f, frame.ball.t, 0.01f)
        assertEquals(1f, frame.ball.height, 0.01f) // sommet du saut
    }

    @Test
    fun `au début du morceau la boule arrive par la gauche`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 1_000 - KaraokeFrame.LEAD_IN_MS / 2)

        assertEquals(-1, frame.ball.fromWord)
        assertEquals(0, frame.ball.toWord)
    }

    @Test
    fun `sur le dernier mot la boule s'y pose et y reste`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 2_700)

        assertEquals(1, frame.ball.fromWord)
        assertEquals(1, frame.ball.toWord)
        assertEquals(0f, frame.ball.height, 0.001f)
    }

    @Test
    fun `après la dernière ligne tout est rempli`() {
        val frame = KaraokeFrame.compute(lines, positionMs = 9_000)

        assertEquals(1, frame.lineIndex)
        assertEquals(listOf(1f), frame.wordFills)
        assertTrue(frame.isFinished)
        assertEquals(0, frame.ball.fromWord) // posée sur le dernier mot
        assertEquals(0, frame.ball.toWord)
    }

    @Test
    fun `paroles vides donne une frame vide`() {
        val frame = KaraokeFrame.compute(emptyList(), positionMs = 1_000)

        assertEquals(-1, frame.lineIndex)
    }
}
