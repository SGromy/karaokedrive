package com.karaokedrive.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitTest {

    private val measure: (String) -> Float = { it.length.toFloat() }

    @Test
    fun `garde tout sur une ligne si ça tient`() {
        val rows = TextFit.wrap(listOf("ab", "cd"), maxWidth = 10f, spaceWidth = 1f, measure = measure)

        assertEquals(listOf(listOf(0, 1)), rows)
    }

    @Test
    fun `passe à la ligne quand le mot suivant déborde`() {
        val rows = TextFit.wrap(listOf("abcd", "efgh", "ij"), maxWidth = 9f, spaceWidth = 1f, measure = measure)

        assertEquals(listOf(listOf(0, 1), listOf(2)), rows)
    }

    @Test
    fun `un mot trop long occupe seul sa ligne`() {
        val rows = TextFit.wrap(listOf("a", "bbbbbbbbbbbb", "c"), maxWidth = 5f, spaceWidth = 1f, measure = measure)

        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), rows)
    }

    @Test
    fun `tronque avec des points de suspension`() {
        val text = TextFit.ellipsize("bonjour la route", maxWidth = 8f, measure = measure)

        assertTrue(text.endsWith("…"))
        assertTrue(text.length <= 8)
    }

    @Test
    fun `wrapText répartit un titre sur plusieurs lignes`() {
        val rows = TextFit.wrapText("les roues du car", maxWidth = 9f, maxRows = 2, measure = measure)

        assertEquals(listOf("les roues", "du car"), rows)
    }

    @Test
    fun `wrapText termine la dernière ligne par des points de suspension si tout ne tient pas`() {
        val rows = TextFit.wrapText("un deux trois quatre cinq", maxWidth = 9f, maxRows = 2, measure = measure)

        assertEquals(2, rows.size)
        assertEquals("un deux", rows[0])
        assertTrue(rows[1].endsWith("…"))
        assertTrue(rows[1].length <= 9)
    }

    @Test
    fun `wrapText tronque un mot seul trop long`() {
        val rows = TextFit.wrapText("abcdefghijklmnop", maxWidth = 6f, maxRows = 2, measure = measure)

        assertEquals(1, rows.size)
        assertTrue(rows[0].endsWith("…"))
    }
}
