package com.karaokedrive.recognition

import com.karaokedrive.audio.AudioSample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackRecognizerTest {

    private class Fake(override val name: String, private val result: RecognitionResult, override val isConfigured: Boolean = true) :
        SongRecognizer {
        var calls = 0
        override suspend fun identify(sample: AudioSample): RecognitionResult = result.also { calls++ }
    }

    private val sample = AudioSample(ByteArray(0), capturedAtMs = 0, rms = 0)
    private val match = RecognitionResult.Match("T", "A", 0, 1_000)

    @Test
    fun `Shazam répond, ACRCloud n'est pas sollicité`() = runBlocking {
        val shazam = Fake("Shazam", match)
        val acr = Fake("ACRCloud", match)

        assertEquals(match, FallbackRecognizer(shazam, acr).identify(sample))
        assertEquals(0, acr.calls)
    }

    @Test
    fun `Shazam ne reconnaît pas, on ne paie pas une requête ACRCloud`() = runBlocking {
        val acr = Fake("ACRCloud", match)

        assertEquals(RecognitionResult.NoMatch, FallbackRecognizer(Fake("Shazam", RecognitionResult.NoMatch), acr).identify(sample))
        assertEquals(0, acr.calls)
    }

    @Test
    fun `Shazam en panne, ACRCloud prend le relais`() = runBlocking {
        val acr = Fake("ACRCloud", match)

        assertEquals(match, FallbackRecognizer(Fake("Shazam", RecognitionResult.Failure("HTTP 429")), acr).identify(sample))
        assertEquals(1, acr.calls)
    }

    @Test
    fun `Shazam en panne et ACRCloud non configuré, l'échec remonte`() = runBlocking {
        val failure = RecognitionResult.Failure("HTTP 429")
        val acr = Fake("ACRCloud", match, isConfigured = false)

        assertEquals(failure, FallbackRecognizer(Fake("Shazam", failure), acr).identify(sample))
    }
}
