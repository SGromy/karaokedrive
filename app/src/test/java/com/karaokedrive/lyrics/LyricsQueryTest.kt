package com.karaokedrive.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsQueryTest {

    @Test
    fun `les mentions entre parenthèses sont retirées du titre`() {
        assertEquals("Mon Titre", LyricsQuery.cleanTitle("Mon Titre (Single Edit)"))
        assertEquals("Mon Titre", LyricsQuery.cleanTitle("Mon Titre [Radio Edit]"))
    }

    @Test
    fun `les suffixes de version après un tiret sont retirés`() {
        assertEquals("Mon Titre", LyricsQuery.cleanTitle("Mon Titre - Remastered 2011"))
        assertEquals("Mon Titre", LyricsQuery.cleanTitle("Mon Titre – Radio Version"))
    }

    @Test
    fun `un tiret qui fait partie du titre est conservé`() {
        assertEquals("Rendez-vous - La Suite", LyricsQuery.cleanTitle("Rendez-vous - La Suite"))
    }

    @Test
    fun `le feat est retiré du titre`() {
        assertEquals("Mon Titre", LyricsQuery.cleanTitle("Mon Titre (feat. Quelqu'un)"))
    }

    @Test
    fun `variantes du plus précis au plus large, sans doublon`() {
        val variants = LyricsQuery.variants("Mon Titre (Single Edit)", "Artiste A & Artiste B")

        assertEquals(
            listOf(
                "Mon Titre (Single Edit)" to "Artiste A & Artiste B",
                "Mon Titre" to "Artiste A & Artiste B",
                "Mon Titre" to "Artiste A",
            ),
            variants,
        )
    }

    @Test
    fun `un titre déjà simple ne produit qu'une variante`() {
        assertEquals(listOf("Titre" to "Artiste"), LyricsQuery.variants("Titre", "Artiste"))
    }
}
