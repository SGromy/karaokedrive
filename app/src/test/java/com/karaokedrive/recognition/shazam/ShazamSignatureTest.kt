package com.karaokedrive.recognition.shazam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class ShazamSignatureTest {

    private val signature = ShazamSignature(
        sampleRateHz = 16_000,
        numberSamples = 128_000,
        bands = mapOf(
            2 to listOf(FrequencyPeak(10, 30_000, 12_000), FrequencyPeak(400, 31_000, 12_500)),
            0 to listOf(FrequencyPeak(3, 20_000, 3_000)),
        ),
    )

    private fun decode(bytes: ByteArray): Map<Int, List<FrequencyPeak>> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(ShazamSignature.HEADER_SIZE + 8)
        val bands = linkedMapOf<Int, List<FrequencyPeak>>()
        while (buf.remaining() >= 8) {
            val band = buf.int - ShazamSignature.BAND_TAG
            val size = buf.int
            val end = buf.position() + size
            val peaks = mutableListOf<FrequencyPeak>()
            var pass = 0
            while (buf.position() < end) {
                val delta = buf.get().toInt() and 0xFF
                if (delta == 0xFF) { pass = buf.int; continue }
                pass += delta
                peaks += FrequencyPeak(pass, buf.short.toInt() and 0xFFFF, buf.short.toInt() and 0xFFFF)
            }
            buf.position(end + (4 - size % 4) % 4)
            bands[band] = peaks
        }
        return bands
    }

    @Test
    fun `l'en-tête respecte le format Shazam, CRC compris`() {
        val bytes = signature.encode()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(ShazamSignature.MAGIC_1, header.getInt(0))
        assertEquals(bytes.size - ShazamSignature.HEADER_SIZE, header.getInt(8))
        assertEquals(ShazamSignature.MAGIC_2, header.getInt(12))
        assertEquals(3 shl 27, header.getInt(28))
        assertEquals(128_000 + 3_840, header.getInt(40))
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
        assertEquals(crc, header.getInt(4))
    }

    @Test
    fun `les pics se relisent à l'identique, bandes triées et grands sauts encodés`() {
        val decoded = decode(signature.encode())

        assertEquals(listOf(0, 2), decoded.keys.toList())
        assertEquals(signature.bands[0], decoded[0])
        assertEquals(signature.bands[2], decoded[2])
    }

    @Test
    fun `l'URI de données porte le bon préfixe et la bonne durée`() {
        assertTrue(signature.toDataUri().startsWith(ShazamSignature.DATA_URI_PREFIX))
        assertEquals(8_000L, signature.durationMs)
    }
}
