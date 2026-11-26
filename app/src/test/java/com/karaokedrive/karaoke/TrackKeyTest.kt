package com.karaokedrive.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrackKeyTest {

    @Test
    fun `les variantes d'apostrophe désignent le même morceau`() {
        assertEquals(TrackKey.of("Je m’en vais", "Vianney"), TrackKey.of("Je m'en vais", "Vianney"))
    }

    @Test
    fun `la casse, les accents et la ponctuation sont ignorés`() {
        assertEquals(TrackKey.of("Soirée mondaine!", "ORIA"), TrackKey.of("soiree mondaine", "Oria"))
    }

    @Test
    fun `les mentions feat et les suffixes entre parenthèses sont ignorés`() {
        assertEquals(
            TrackKey.of("Titre (Radio Edit)", "Artiste feat. Invité"),
            TrackKey.of("Titre", "Artiste"),
        )
    }

    @Test
    fun `deux morceaux différents restent différents`() {
        assertNotEquals(TrackKey.of("Loser", "Tame Impala"), TrackKey.of("Nue", "Clara Luciani"))
    }
}
