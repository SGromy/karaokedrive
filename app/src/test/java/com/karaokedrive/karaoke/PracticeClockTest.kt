package com.karaokedrive.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeClockTest {

    private val stopped = PracticeClock(durationMs = 60_000)

    @Test
    fun `à l'arrêt la position ne bouge pas`() {
        assertEquals(0L, stopped.positionAt(nowMs = 10_000))
    }

    @Test
    fun `en lecture la position avance avec l'horloge`() {
        val playing = stopped.play(nowMs = 1_000)

        assertEquals(4_000L, playing.positionAt(nowMs = 5_000))
    }

    @Test
    fun `la pause fige la position, la reprise repart de là`() {
        val paused = stopped.play(0).pause(nowMs = 3_000)

        assertEquals(3_000L, paused.positionAt(nowMs = 9_000))
        assertEquals(5_000L, paused.play(nowMs = 9_000).positionAt(nowMs = 11_000))
    }

    @Test
    fun `avancer et reculer restent dans les bornes du morceau`() {
        val playing = stopped.play(0)

        assertEquals(12_000L, playing.seekBy(10_000, nowMs = 2_000).positionAt(2_000))
        assertEquals(0L, playing.seekBy(-10_000, nowMs = 2_000).positionAt(2_000))
        assertEquals(60_000L, playing.seekBy(100_000, nowMs = 2_000).positionAt(2_000))
    }

    @Test
    fun `la vitesse réduite ralentit le défilement sans saut`() {
        val slow = stopped.play(0).nextSpeed(nowMs = 4_000) // 0,75× à partir de 4 s

        assertEquals(0.75f, slow.speed)
        assertEquals(4_000L, slow.positionAt(4_000))
        assertEquals(7_000L, slow.positionAt(8_000))
    }

    @Test
    fun `les vitesses tournent en boucle`() {
        val speeds = generateSequence(stopped) { it.nextSpeed(0) }.take(4).map { it.speed }.toList()

        assertEquals(listOf(1f, 0.75f, 0.5f, 1f), speeds)
    }

    @Test
    fun `fin du morceau détectée, relancer repart du début`() {
        val playing = stopped.play(0)

        assertTrue(playing.isFinishedAt(70_000))
        assertFalse(playing.isFinishedAt(30_000))
        assertEquals(0L, playing.play(nowMs = 70_000).positionAt(70_000))
    }

    @Test
    fun `recommencer revient au début en gardant lecture et vitesse`() {
        val restarted = stopped.play(0).nextSpeed(0).restart(nowMs = 20_000)

        assertEquals(0L, restarted.positionAt(20_000))
        assertTrue(restarted.isPlaying)
        assertEquals(0.75f, restarted.speed)
    }
}
