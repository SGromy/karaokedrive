package com.karaokedrive.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class CoverPaletteTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun hueOf(color: Int): Float = CoverPalette.toHsv(color)[0]

    private fun hueDistance(a: Float, b: Float): Float = abs(a - b).let { min(it, 360f - it) }

    @Test
    fun `convertit aller-retour entre RVB et TSV`() {
        val color = rgb(200, 60, 120)

        val (h, s, v) = CoverPalette.toHsv(color).toList()

        assertEquals(color, CoverPalette.fromHsv(h, s, v))
    }

    @Test
    fun `une pochette grise ne donne pas de couleurs`() {
        val pixels = IntArray(64) { i -> rgb(i * 3, i * 3, i * 3) }

        assertNull(CoverPalette.accents(pixels))
    }

    @Test
    fun `garde les deux teintes vives distinctes de la pochette`() {
        val pixels = IntArray(30) { rgb(230, 30, 40) } + IntArray(20) { rgb(30, 60, 220) } + IntArray(50) { rgb(40, 40, 40) }

        val accents = assertNotNullAndGet(CoverPalette.accents(pixels))

        assertTrue(hueDistance(hueOf(accents.first), 357f) < 15f)
        assertTrue(hueDistance(hueOf(accents.second), 229f) < 15f)
    }

    @Test
    fun `une seule teinte vive donne une seconde couleur voisine mais distincte`() {
        val pixels = IntArray(40) { rgb(250, 140, 20) } + IntArray(40) { rgb(20, 20, 20) }

        val accents = assertNotNullAndGet(CoverPalette.accents(pixels))

        val distance = hueDistance(hueOf(accents.first), hueOf(accents.second))
        assertTrue("écart de teinte $distance", distance in 25f..90f)
    }

    @Test
    fun `les couleurs sont ravivées pour l'effet néon`() {
        val pixels = IntArray(40) { rgb(120, 70, 60) } + IntArray(40) { rgb(60, 80, 120) }

        val accents = assertNotNullAndGet(CoverPalette.accents(pixels))

        val (_, s, v) = CoverPalette.toHsv(accents.first).toList()
        assertTrue(s >= 0.55f)
        assertEquals(1f, v, 0.01f)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return requireNotNull(value)
    }
}
