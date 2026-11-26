package com.karaokedrive.render

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMixTest {

    private val red = 0xFFC81E1E.toInt()

    @Test
    fun `éclaircir de 0 garde la couleur, de 1 donne du blanc`() {
        assertEquals(red, ColorMix.withWhite(red, 0f))
        assertEquals(0xFFFFFFFF.toInt(), ColorMix.withWhite(red, 1f))
    }

    @Test
    fun `éclaircir à moitié rapproche chaque composante du blanc`() {
        // 200 → 228, 30 → 143
        assertEquals(0xFFE48F8F.toInt(), ColorMix.withWhite(red, 0.5f))
    }

    @Test
    fun `change seulement l'opacité`() {
        assertEquals(0x66C81E1E, ColorMix.withAlpha(red, 0x66))
    }
}
