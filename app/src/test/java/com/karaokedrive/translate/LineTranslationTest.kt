package com.karaokedrive.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineTranslationTest {

    private val dictionary = mapOf("good morning" to "bonjour", "thank you" to "merci")

    @Test
    fun `chaque ligne reçoit sa traduction, dans l'ordre`() = runBlocking {
        val result = LineTranslation.translateDistinct(listOf("good morning", "thank you")) { dictionary.getValue(it) }

        assertEquals(listOf("bonjour", "merci"), result)
    }

    @Test
    fun `une ligne répétée n'est traduite qu'une fois`() = runBlocking {
        var calls = 0
        val result = LineTranslation.translateDistinct(listOf("thank you", "good morning", "thank you")) {
            calls++
            dictionary.getValue(it)
        }

        assertEquals(2, calls)
        assertEquals(listOf("merci", "bonjour", "merci"), result)
    }

    @Test
    fun `une ligne en échec ou identique reste sans traduction`() = runBlocking {
        val result = LineTranslation.translateDistinct(listOf("good morning", "OK", "oops")) {
            when (it) {
                "oops" -> error("modèle indisponible")
                "OK" -> "ok"
                else -> dictionary.getValue(it)
            }
        }

        assertEquals("bonjour", result[0])
        assertNull(result[1])
        assertNull(result[2])
    }

    @Test
    fun `l'extrait de détection ignore les lignes vides et reste court`() {
        val sample = LineTranslation.detectionSample(listOf("a", "", "b") + List(200) { "long line of text" })

        assertEquals("a b ", sample.take(4))
        assertEquals(600, sample.length)
    }
}
