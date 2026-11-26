package com.karaokedrive.recognition.shazam

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

/**
 * Calcul de l'empreinte Shazam d'un extrait PCM 16 bits mono à 16 kHz.
 *
 * Portage de l'algorithme de SongRec, via shazamio (licence MIT, © 2021 dotX12) :
 * une FFT de 2048 points toutes les 128 échantillons, un « étalement » des pics en
 * temps et en fréquence, puis la sélection des maxima locaux répartis en 4 bandes.
 */
class SignatureGenerator {

    private val fft = Fft(FFT_SIZE)
    private val windowed = DoubleArray(FFT_SIZE)

    private val samplesRing = DoubleArray(FFT_SIZE)
    private var samplesPosition = 0

    private val fftOutputs = Array(RING_SIZE) { DoubleArray(BINS) }
    private var fftPosition = 0

    private val spreadOutputs = Array(RING_SIZE) { DoubleArray(BINS) }
    private var spreadPosition = 0
    private var spreadWritten = 0

    private val peaks = sortedMapOf<Int, MutableList<FrequencyPeak>>()
    private var numberSamples = 0

    /** Traite l'extrait (jusqu'à [MAX_SECONDS] s, plus s'il manque de pics) et renvoie l'empreinte. */
    fun generate(samples: ShortArray): ShazamSignature {
        var offset = 0
        while (offset + CHUNK <= samples.size && (numberSamples < MAX_SECONDS * SAMPLE_RATE || peakCount() < MAX_PEAKS)) {
            processChunk(samples, offset)
            offset += CHUNK
        }
        return ShazamSignature(SAMPLE_RATE, numberSamples, peaks.mapValues { it.value.toList() })
    }

    private fun peakCount(): Int = peaks.values.sumOf { it.size }

    private fun processChunk(samples: ShortArray, offset: Int) {
        numberSamples += CHUNK
        doFft(samples, offset)
        doPeakSpreading()
        if (spreadWritten >= 46) doPeakRecognition()
    }

    private fun doFft(samples: ShortArray, offset: Int) {
        for (i in 0 until CHUNK) samplesRing[samplesPosition + i] = samples[offset + i].toDouble()
        samplesPosition = (samplesPosition + CHUNK) % FFT_SIZE
        // Fenêtre de Hann appliquée aux 2048 derniers échantillons, du plus ancien au plus récent
        for (i in 0 until FFT_SIZE) windowed[i] = HANNING[i] * samplesRing[(samplesPosition + i) % FFT_SIZE]
        val out = fftOutputs[fftPosition]
        fft.power(windowed, out)
        for (k in 0 until BINS) out[k] = max(out[k] / (1 shl 17), 1e-10)
        fftPosition = (fftPosition + 1) % RING_SIZE
    }

    private fun doPeakSpreading() {
        val last = fftOutputs[wrap(fftPosition - 1)]
        val spread = spreadOutputs[spreadPosition]
        // Étalement en fréquence : chaque case prend le max d'elle-même et des deux suivantes
        for (k in 0 until BINS) {
            spread[k] = if (k < BINS - 3) max(last[k], max(last[k + 1], last[k + 2])) else last[k]
        }
        // Étalement en temps : chaque sortie précédente (−1, −3, −6) prend le max d'elle-même
        // et de la nouvelle, indépendamment (comme SongRec ; la version Python de shazamio
        // cumule ces maxima, ce qui écarte environ 10 % des pics).
        for (back in TIME_SPREAD) {
            val previous = spreadOutputs[wrap(spreadPosition - back)]
            for (k in 0 until BINS) previous[k] = max(previous[k], spread[k])
        }
        spreadPosition = (spreadPosition + 1) % RING_SIZE
        spreadWritten++
    }

    private fun doPeakRecognition() {
        val fft46 = fftOutputs[wrap(fftPosition - 46)]
        val spread49 = spreadOutputs[wrap(spreadPosition - 49)]

        for (bin in 10 until 1015) {
            val value = fft46[bin]
            if (value < 1.0 / 64 || value < spread49[bin - 1]) continue

            // Maximum local en fréquence
            var neighborMax = 0.0
            for (offset in FREQUENCY_NEIGHBORS) neighborMax = max(spread49[bin + offset], neighborMax)
            if (value <= neighborMax) continue

            // Maximum local en temps
            var otherMax = neighborMax
            for (offset in TIME_NEIGHBORS) otherMax = max(spreadOutputs[wrap(spreadPosition + offset)][bin - 1], otherMax)
            if (value <= otherMax) continue

            val magnitude = logMagnitude(value)
            val before = logMagnitude(fft46[bin - 1])
            val after = logMagnitude(fft46[bin + 1])
            val variation1 = magnitude * 2 - before - after
            if (variation1 <= 0) continue
            val variation2 = (after - before) * 32 / variation1
            val correctedBin = bin * 64 + variation2
            val band = bandOf(correctedBin * (SAMPLE_RATE / 2.0 / 1024 / 64)) ?: continue

            peaks.getOrPut(band) { mutableListOf() }
                .add(FrequencyPeak(spreadWritten - 46, magnitude.toInt(), correctedBin.toInt()))
        }
    }

    private fun wrap(index: Int): Int = ((index % RING_SIZE) + RING_SIZE) % RING_SIZE

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FFT_SIZE = 2048
        const val BINS = FFT_SIZE / 2 + 1
        const val CHUNK = 128
        const val RING_SIZE = 256
        const val MAX_SECONDS = 12
        const val MAX_PEAKS = 255

        /** np.hanning(2050)[1:-1] : fenêtre de Hann sans les zéros aux extrémités. */
        val HANNING = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2 * PI * (it + 1) / (FFT_SIZE + 1)) }
        val TIME_SPREAD = intArrayOf(1, 3, 6)
        val FREQUENCY_NEIGHBORS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
        val TIME_NEIGHBORS = intArrayOf(-53, -45) + (165..200 step 7).toList() + (214..249 step 7).toList()

        fun logMagnitude(value: Double): Double = ln(max(1.0 / 64, value)) * 1477.3 + 6144

        fun bandOf(hz: Double): Int? = when {
            hz > 250 && hz < 520 -> 0
            hz > 520 && hz < 1450 -> 1
            hz > 1450 && hz < 3500 -> 2
            hz > 3500 && hz <= 5500 -> 3
            else -> null
        }
    }
}
