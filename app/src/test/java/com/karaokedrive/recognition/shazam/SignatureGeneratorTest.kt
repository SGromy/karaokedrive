package com.karaokedrive.recognition.shazam

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Compare le portage Kotlin à la référence produite par le code de SongRec
 * (tools/shazam/make_golden.py) sur un signal synthétique de 8 s.
 */
class SignatureGeneratorTest {

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("shazam/$name")) { "ressource $name absente" }.readBytes()

    private fun synthetic(): ShortArray {
        val pcm = ByteBuffer.wrap(resource("synthetic.wav"), 44, resource("synthetic.wav").size - 44)
            .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(pcm.remaining()).also { pcm.get(it) }
    }

    private val golden = JSONObject(String(resource("golden_peaks.json")))
    private val signature = SignatureGenerator().generate(synthetic())

    @Test
    fun `même nombre d'échantillons traités que SongRec`() {
        assertEquals(golden.getInt("numberSamples"), signature.numberSamples)
    }

    @Test
    fun `les 4 bandes ont presque le même nombre de pics que SongRec`() {
        val bands = golden.getJSONObject("bands")
        for (band in 0..3) {
            val expected = bands.getJSONArray(band.toString()).length()
            val actual = signature.bands[band].orEmpty().size
            assertTrue("bande $band : $actual pics contre $expected", abs(actual - expected) <= expected * 0.05 + 1)
        }
    }

    @Test
    fun `au moins 95 pour cent des pics de SongRec sont retrouvés à l'identique ou presque`() {
        val bands = golden.getJSONObject("bands")
        var total = 0
        var found = 0
        for (band in 0..3) {
            val mine = signature.bands[band].orEmpty()
            val theirs = bands.getJSONArray(band.toString())
            for (i in 0 until theirs.length()) {
                val (pass, magnitude, bin) = theirs.getJSONArray(i).let { Triple(it.getInt(0), it.getInt(1), it.getInt(2)) }
                total++
                if (mine.any { it.fftPassNumber == pass && abs(it.magnitude - magnitude) <= 2 && abs(it.correctedBin - bin) <= 2 }) found++
            }
        }
        assertTrue("pics retrouvés : $found / $total", found >= total * 0.95)
    }
}
