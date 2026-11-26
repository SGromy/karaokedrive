package com.karaokedrive.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class KrcConverterTest {

    // Paroles inventées pour les tests
    private val krc = """
        [ti:Route de nuit]
        [ar:Les Phares]
        [language:eyJjb250ZW50IjogW119]
        [0,1000]<0,500,0>Route de nuit - Les Phares
        [1000,900]<0,400,0>Lyrics by：Quelqu'un
        [1900,900]<0,400,0>Composed by：Quelqu'un
        [12000,3000]<0,400,0>La <400,600,0>route <1000,500,0>chan<1500,700,0>te
        [16000,4000]<0,300,0>Sous <300,500,0>les <2000,1800,0>phares
    """.trimIndent()

    @Test
    fun `convertit le format Kugou en LRC enrichi mot par mot`() {
        val lrc = KrcConverter.toEnhancedLrc(krc, title = "Route de nuit")

        val lines = LrcParser.parse(requireNotNull(lrc), trackDurationMs = 30_000)

        assertEquals(2, lines.size)
        assertEquals(listOf("La", "route", "chante"), lines[0].words.map { it.text })
        assertEquals(listOf(12_000L, 12_400L, 13_000L), lines[0].words.map { it.startMs })
        assertEquals(listOf("Sous", "les", "phares"), lines[1].words.map { it.text })
    }

    @Test
    fun `retire le titre et les crédits du début`() {
        val lrc = requireNotNull(KrcConverter.toEnhancedLrc(krc, title = "Route de nuit"))

        assertFalse(lrc.contains("Lyrics by"))
        assertFalse(lrc.contains("Composed by"))
        assertFalse(lrc.contains("Les Phares"))
    }

    @Test
    fun `un mot tenu garde toute sa durée, une pause rend la boule immobile`() {
        val lrc = requireNotNull(KrcConverter.toEnhancedLrc(krc, title = "Route de nuit"))

        val words = LrcParser.parse(lrc, trackDurationMs = 30_000)[1].words

        // « les » finit à 16 800 ms (pause avant « phares »), « phares » est tenu 1,8 s
        assertEquals(16_800L, words[1].endMs)
        assertEquals(18_000L, words[2].startMs)
        assertEquals(19_800L, words[2].endMs)
    }

    @Test
    fun `renvoie null sans aucune ligne chantée`() {
        assertNull(KrcConverter.toEnhancedLrc("[ti:Rien]\n[ar:Personne]", title = "Rien"))
    }

    @Test
    fun `déchiffre un fichier KRC`() {
        val encrypted = encrypt(krc)

        assertEquals(krc, KrcConverter.decrypt(encrypted))
    }

    @Test
    fun `refuse un fichier qui n'est pas au format KRC`() {
        assertNull(KrcConverter.decrypt("pas un krc".toByteArray()))
    }

    @Test
    fun `formate les temps au millième`() {
        val lrc = requireNotNull(KrcConverter.toEnhancedLrc("[61234,1000]<0,500,0>Salut", title = "x"))

        assertTrue(lrc, lrc.startsWith("[01:01.234]<01:01.234>Salut"))
    }

    private fun encrypt(text: String): ByteArray {
        val compressed = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(text.toByteArray()) }
        }.toByteArray()
        val key = KrcConverter.KEY
        return "krc1".toByteArray() + ByteArray(compressed.size) { i -> (compressed[i].toInt() xor key[i % key.size]).toByte() }
    }
}
