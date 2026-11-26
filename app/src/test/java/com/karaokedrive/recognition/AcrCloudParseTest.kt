package com.karaokedrive.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcrCloudParseTest {

    @Test
    fun `extrait titre, artiste, durée et position`() {
        val json = """
            {"status":{"code":0,"msg":"Success"},
             "metadata":{"music":[{"title":"Titre","artists":[{"name":"Artiste"}],
               "duration_ms":200000,"play_offset_ms":61500}]}}
        """

        val result = AcrCloudRecognizer.parse(json)

        assertEquals(RecognitionResult.Match("Titre", "Artiste", 200_000, 61_500), result)
    }

    @Test
    fun `code 1001 signifie aucun résultat`() {
        val result = AcrCloudRecognizer.parse("""{"status":{"code":1001,"msg":"No result"}}""")

        assertEquals(RecognitionResult.NoMatch, result)
    }

    @Test
    fun `un extrait sans empreinte exploitable est traité comme non reconnu`() {
        val result = AcrCloudRecognizer.parse("""{"status":{"code":2004,"msg":"Can't generate fingerprint"}}""")

        assertEquals(RecognitionResult.NoMatch, result)
    }

    @Test
    fun `une erreur d'API devient un échec lisible`() {
        val result = AcrCloudRecognizer.parse("""{"status":{"code":3001,"msg":"Missing/Invalid Access Key"}}""")

        assertTrue(result is RecognitionResult.Failure)
    }

    @Test
    fun `un JSON invalide ne plante pas`() {
        assertTrue(AcrCloudRecognizer.parse("pas du json") is RecognitionResult.Failure)
    }
}
