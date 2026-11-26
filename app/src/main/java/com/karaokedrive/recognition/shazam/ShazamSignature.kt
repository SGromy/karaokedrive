package com.karaokedrive.recognition.shazam

import java.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Pic de fréquence d'une empreinte Shazam.
 * @param fftPassNumber numéro de la FFT (une toutes les 128 échantillons à 16 kHz)
 * @param magnitude amplitude en échelle logarithmique
 * @param correctedBin fréquence en 1/64 de case FFT
 */
data class FrequencyPeak(val fftPassNumber: Int, val magnitude: Int, val correctedBin: Int)

/**
 * Empreinte au format binaire de Shazam (portage de shazamio, licence MIT,
 * © 2021 dotX12, lui-même dérivé de SongRec). Bandes : 0 = 250-520 Hz,
 * 1 = 520-1450 Hz, 2 = 1450-3500 Hz, 3 = 3500-5500 Hz.
 */
class ShazamSignature(
    val sampleRateHz: Int,
    val numberSamples: Int,
    val bands: Map<Int, List<FrequencyPeak>>,
) {
    val peakCount: Int get() = bands.values.sumOf { it.size }
    val durationMs: Long get() = numberSamples * 1000L / sampleRateHz

    fun encode(): ByteArray {
        val contents = ByteArrayOutputStream()
        bands.toSortedMap().forEach { (band, peaks) ->
            val encodedPeaks = encodePeaks(peaks)
            contents.write(int32(BAND_TAG + band))
            contents.write(int32(encodedPeaks.size))
            contents.write(encodedPeaks)
            contents.write(ByteArray(padding(encodedPeaks.size)))
        }
        val body = contents.toByteArray()
        val message = ByteBuffer.allocate(HEADER_SIZE + 8 + body.size).order(ByteOrder.LITTLE_ENDIAN)
        message.putInt(MAGIC_1)
        message.putInt(0) // CRC-32, calculé plus bas
        message.putInt(body.size + 8)
        message.putInt(MAGIC_2)
        repeat(3) { message.putInt(0) }
        message.putInt(SAMPLE_RATE_ID_16K shl 27)
        repeat(2) { message.putInt(0) }
        message.putInt(numberSamples + (sampleRateHz * 0.24).toInt())
        message.putInt((15 shl 19) + 0x40000)
        message.putInt(0x40000000)
        message.putInt(body.size + 8)
        message.put(body)
        val bytes = message.array()
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value
        ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc.toInt())
        return bytes
    }

    fun toDataUri(): String = DATA_URI_PREFIX + Base64.getEncoder().encodeToString(encode())

    private fun encodePeaks(peaks: List<FrequencyPeak>): ByteArray {
        val out = ByteArrayOutputStream()
        var lastPass = 0
        for (peak in peaks) {
            if (peak.fftPassNumber - lastPass >= 255) {
                out.write(0xFF)
                out.write(int32(peak.fftPassNumber))
                lastPass = peak.fftPassNumber
            }
            out.write(peak.fftPassNumber - lastPass)
            out.write(int16(peak.magnitude))
            out.write(int16(peak.correctedBin))
            lastPass = peak.fftPassNumber
        }
        return out.toByteArray()
    }

    companion object {
        const val DATA_URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"
        const val HEADER_SIZE = 48
        const val MAGIC_1 = 0xCAFE2580.toInt()
        const val MAGIC_2 = 0x94119C00.toInt()
        const val BAND_TAG = 0x60030040
        private const val SAMPLE_RATE_ID_16K = 3

        private fun padding(size: Int): Int = (4 - size % 4) % 4
        private fun int32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        private fun int16(v: Int) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
    }
}
