package com.karaokedrive.artwork

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverArtClientTest {

    @Test
    fun `pochette haute définition de Shazam en priorité`() {
        val track = JSONObject("""{"images":{"coverart":"https://img/400.jpg","coverarthq":"https://img/hq.jpg"}}""")

        assertEquals("https://img/hq.jpg", CoverArtClient.parseShazamTrack(track))
    }

    @Test
    fun `Shazam sans image, pas de pochette`() {
        assertNull(CoverArtClient.parseShazamTrack(JSONObject("""{"title":"T"}""")))
        assertNull(CoverArtClient.parseShazamTrack(null))
    }

    @Test
    fun `recherche Deezer, pochette de l'album du premier résultat`() {
        val json = """{"data":[{"album":{"cover_big":"https://deezer/big.jpg","cover_xl":"https://deezer/xl.jpg"}}]}"""

        assertEquals("https://deezer/big.jpg", CoverArtClient.parseDeezer(json))
    }

    @Test
    fun `recherche Deezer sans résultat ou illisible`() {
        assertNull(CoverArtClient.parseDeezer("""{"data":[]}"""))
        assertNull(CoverArtClient.parseDeezer("pas du json"))
    }

    @Test
    fun `les grandes images sont réduites au décodage`() {
        assertEquals(1, CoverArtClient.sampleSizeFor(600))
        assertEquals(2, CoverArtClient.sampleSizeFor(1400))
        assertEquals(4, CoverArtClient.sampleSizeFor(3000))
    }
}
