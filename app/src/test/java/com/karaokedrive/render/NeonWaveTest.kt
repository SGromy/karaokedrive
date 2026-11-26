package com.karaokedrive.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NeonWaveTest {

    private fun peak(timeMs: Long, energy: Float): Float =
        (0..600).maxOf { abs(NeonWave.sample(it / 600f, timeMs, energy)) }

    @Test
    fun `l'onde reste dans l'amplitude prévue`() {
        for (t in 0L..20_000L step 700L) {
            for (i in 0..300) {
                val y = NeonWave.sample(i / 300f, t, energy = 1f)
                assertTrue("y=$y", y in -1f..1f)
            }
        }
    }

    @Test
    fun `l'onde contient de vrais pics`() {
        assertTrue(peak(timeMs = 3_000, energy = 1f) > 0.4f)
    }

    @Test
    fun `plus d'énergie donne des pics plus hauts`() {
        assertTrue(peak(5_000, energy = 1f) > peak(5_000, energy = 0f) * 1.5f)
    }

    @Test
    fun `l'onde est continue aux bords (défilement sans raccord visible)`() {
        assertEquals(NeonWave.sample(0f, 1_234, 1f), NeonWave.sample(1f, 1_234, 1f), 0.05f)
    }

    @Test
    fun `l'onde bouge dans le temps`() {
        val a = (0..100).map { NeonWave.sample(it / 100f, 0, 1f) }
        val b = (0..100).map { NeonWave.sample(it / 100f, 800, 1f) }

        assertTrue(a.zip(b).any { (x, y) -> abs(x - y) > 0.05f })
    }
}
