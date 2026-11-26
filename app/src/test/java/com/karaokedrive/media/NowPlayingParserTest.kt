package com.karaokedrive.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingParserTest {

    private fun parse(
        title: String?, artist: String?, subtitle: String? = null,
        durationMs: Long = 0, positionMs: Long = 0,
    ) = NowPlayingParser.parse("app", title, artist, subtitle, durationMs, positionMs, 1_000, 1f, "Appli")

    @Test
    fun `application de musique classique, titre et artiste séparés`() {
        val song = parse(title = "Mon titre", artist = "Mon artiste", durationMs = 200_000, positionMs = 30_000)!!

        assertEquals("Mon titre", song.title)
        assertEquals("Mon artiste", song.artist)
        assertTrue(song.hasPosition)
    }

    @Test
    fun `radio, station en titre, « Artiste — Titre » en sous-titre`() {
        val song = parse(title = "NOSTALGIE", artist = null, subtitle = "Un Groupe — Une Chanson")!!

        assertEquals("Une Chanson", song.title)
        assertEquals("Un Groupe", song.artist)
        assertFalse(song.hasPosition)
    }

    @Test
    fun `radio, « Artiste - Titre » dans le champ artiste, tiret simple`() {
        val song = parse(title = "Station", artist = "Quelqu'un - Un titre")!!

        assertEquals("Un titre", song.title)
        assertEquals("Quelqu'un", song.artist)
    }

    @Test
    fun `un tiret dans un mot composé ne coupe pas le titre`() {
        val song = parse(title = "Rendez-vous", artist = "Jean-Pierre")!!

        assertEquals("Rendez-vous", song.title)
        assertEquals("Jean-Pierre", song.artist)
    }

    @Test
    fun `les infos de station entre deux morceaux ne sont pas prises pour un morceau`() {
        assertNull(parse(title = "NOSTALGIE", artist = null, subtitle = "Fleurance - 91.0 FM"))
        assertNull(parse(title = "Radio", artist = "Paris 105,5 FM"))
    }

    @Test
    fun `sans artiste ni morceau identifiable, rien n'est proposé`() {
        assertNull(parse(title = "NOSTALGIE", artist = null))
    }

    @Test
    fun `la position est extrapolée selon le temps écoulé et la vitesse`() {
        val song = parse(title = "T", artist = "A", durationMs = 200_000, positionMs = 10_000)!!

        assertEquals(12_000L, song.positionAt(nowMs = 3_000))
    }
}
