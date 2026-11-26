package com.karaokedrive.render

import com.karaokedrive.karaoke.KaraokeUiState
import com.karaokedrive.karaoke.Status
import com.karaokedrive.lyrics.LyricLine
import com.karaokedrive.lyrics.LyricWord
import com.karaokedrive.lyrics.Lyrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderCadenceTest {

    // Paroles inventées
    private val lyrics = Lyrics("Route de nuit", "Les Phares", listOf(LyricLine(1_000, 4_000, listOf(LyricWord("La", 1_000, 2_000), LyricWord("route", 2_000, 4_000)))))

    @Test
    fun `redessine à chaque frame pendant que les paroles défilent`() {
        assertTrue(RenderCadence.isAnimated(KaraokeUiState(status = Status.SINGING, lyrics = lyrics)))
    }

    @Test
    fun `image fixe sur les écrans d'attente et sans paroles`() {
        for (status in listOf(Status.IDLE, Status.LISTENING, Status.NO_MATCH, Status.NO_LYRICS, Status.ERROR)) {
            assertFalse(status.name, RenderCadence.isAnimated(KaraokeUiState(status = status)))
        }
    }

    @Test
    fun `image fixe pendant la recherche des paroles d'un nouveau morceau`() {
        assertFalse(RenderCadence.isAnimated(KaraokeUiState(status = Status.SINGING, lyrics = null, trackLabel = "Route de nuit — Les Phares")))
    }
}
