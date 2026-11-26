package com.karaokedrive.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpectrumBarsTest {

    @Test
    fun `donne une hauteur par barre, entre 0 et 1`() {
        for (t in 0L..30_000L step 900L) {
            val heights = SpectrumBars.heights(count = 31, timeMs = t, energy = 1f)
            assertEquals(31, heights.size)
            heights.forEach { assertTrue("h=$it", it in 0f..1f) }
        }
    }

    @Test
    fun `les barres sont symétriques autour du centre`() {
        val heights = SpectrumBars.heights(count = 24, timeMs = 4_321, energy = 0.7f)

        assertArrayEquals(heights, heights.reversedArray(), 0.0001f)
    }

    @Test
    fun `les basses au centre sont plus hautes que les aigus aux bords en moyenne`() {
        val samples = (0L until 20_000L step 250L).map { SpectrumBars.heights(count = 32, timeMs = it, energy = 0.6f) }
        val center = samples.map { it[16] }.average()
        val edge = samples.map { it[0] }.average()

        assertTrue("centre=$center bord=$edge", center > edge * 1.3)
    }

    @Test
    fun `plus d'énergie fait monter les barres`() {
        val calm = (0L until 10_000L step 200L).sumOf { SpectrumBars.heights(20, it, 0f).sum().toDouble() }
        val loud = (0L until 10_000L step 200L).sumOf { SpectrumBars.heights(20, it, 1f).sum().toDouble() }

        assertTrue(loud > calm * 1.4)
    }

    @Test
    fun `les barres bougent dans le temps`() {
        val a = SpectrumBars.heights(16, 1_000, 0.5f)
        val b = SpectrumBars.heights(16, 1_300, 0.5f)

        assertTrue(a.indices.any { abs(a[it] - b[it]) > 0.05f })
    }
}
