package com.karaokedrive.karaoke

import com.karaokedrive.media.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPolicyTest {

    private fun radio(title: String, artist: String) =
        NowPlaying("radio", title, artist, durationMs = -1, positionMs = 0, positionUpdatedAtMs = 0, speed = 1f, appLabel = "Radio")

    private fun streaming(title: String, artist: String) =
        NowPlaying("stream", title, artist, durationMs = 200_000, positionMs = 42_000, positionUpdatedAtMs = 0, speed = 1f, appLabel = "Stream")

    @Test
    fun `karaoké arrêté, on ignore tout`() {
        val decision = MediaPolicy.decide(radio("T", "A"), null, null, isKaraokeActive = false)

        assertEquals(MediaDecision.Ignore, decision)
    }

    @Test
    fun `radio qui change de morceau, rescan immédiat`() {
        val decision = MediaPolicy.decide(radio("Nouveau", "B"), TrackKey.of("Ancien", "A"), TrackKey.of("Ancien", "A"), true)

        assertEquals(MediaDecision.Rescan, decision)
    }

    @Test
    fun `radio qui republie le même morceau, pas de rescan`() {
        val key = TrackKey.of("Titre", "A")

        assertEquals(MediaDecision.Ignore, MediaPolicy.decide(radio("Titre", "A"), key, null, true))
    }

    @Test
    fun `morceau déjà reconnu par le micro, pas de rescan`() {
        val decision = MediaPolicy.decide(radio("Titre", "A"), null, TrackKey.of("Titre", "A"), true)

        assertEquals(MediaDecision.Ignore, decision)
    }

    @Test
    fun `application avec position exacte, on la suit sans micro`() {
        val song = streaming("Titre", "A")

        val decision = MediaPolicy.decide(song, null, null, true)

        assertTrue(decision is MediaDecision.FollowApp)
        assertEquals(song, (decision as MediaDecision.FollowApp).nowPlaying)
    }
}
