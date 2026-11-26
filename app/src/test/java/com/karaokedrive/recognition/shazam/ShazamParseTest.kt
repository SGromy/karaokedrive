package com.karaokedrive.recognition.shazam

import com.karaokedrive.recognition.RecognitionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShazamParseTest {

    @Test
    fun `morceau reconnu, position ramenée à la fin de l'extrait`() {
        val json = """{"matches":[{"id":"1","offset":147.1,"timeskew":0.0}],
                       "track":{"title":"Titre","subtitle":"Artiste"}}"""

        val result = ShazamRecognizer.parse(json, sampleDurationMs = 8_000)

        assertEquals(RecognitionResult.Match("Titre", "Artiste", durationMs = 0, playOffsetMs = 155_100), result)
    }

    @Test
    fun `aucune correspondance, non reconnu`() {
        assertEquals(RecognitionResult.NoMatch, ShazamRecognizer.parse("""{"matches":[]}""", 8_000))
    }

    @Test
    fun `réponse illisible, échec technique (déclenche le secours)`() {
        assertTrue(ShazamRecognizer.parse("<html>", 8_000) is RecognitionResult.Failure)
    }
}
