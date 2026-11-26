package com.karaokedrive.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extrait audio prêt à envoyer, l'instant (horloge monotone) où la capture s'est terminée,
 * et son niveau RMS (0‥32767) pour diagnostiquer un micro trop faible.
 */
class AudioSample(
    val wav: ByteArray,
    val capturedAtMs: Long,
    val rms: Int,
    /** Mêmes échantillons que [wav] (normalisés), pour les empreintes calculées sur l'appareil. */
    val pcm: ShortArray = ShortArray(0),
)

/** Enregistre de courts extraits via le micro du téléphone (mono, 16 kHz, PCM 16 bits). */
class AudioSampler(private val sampleRate: Int = 16_000) {

    suspend fun record(durationMs: Long): AudioSample {
        val (raw, capturedAtMs) = recordPcm(durationMs)
        val normalized = normalize(raw)
        val pcm = ShortArray(normalized.size / 2).also {
            ByteBuffer.wrap(normalized).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it)
        }
        return AudioSample(wav = toWav(normalized), capturedAtMs = capturedAtMs, rms = rms(raw), pcm = pcm)
    }

    /** PCM brut (16 bits LE) et instant de fin de capture. */
    @SuppressLint("MissingPermission") // vérifiée par l'UI avant de démarrer l'écoute
    private suspend fun recordPcm(durationMs: Long): Pair<ByteArray, Long> = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        check(minBuffer > 0) { "Micro indisponible (code $minBuffer)" }

        // MIC plutôt que UNPROCESSED : ce dernier renvoie un signal quasi muet sur certains
        // téléphones (RMS ≈ 17 mesuré), trop faible pour l'empreinte en voiture.
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, CHANNEL, ENCODING, minBuffer * 4)
        val targetBytes = (sampleRate * BYTES_PER_SAMPLE * durationMs / 1000).toInt()
        val pcm = try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Impossible d'ouvrir le micro" }
            recorder.startRecording()
            PcmReader.read(targetBytes, minBuffer, recorder::read, SystemClock::elapsedRealtime)
        } finally {
            try {
                recorder.stop()
            } catch (e: IllegalStateException) {
                // Micro jamais démarré : rien à arrêter, mais il faut quand même le libérer
            } finally {
                recorder.release()
            }
        }
        pcm to SystemClock.elapsedRealtime()
    }

    /** Amplifie l'extrait pour que son pic atteigne [TARGET_PEAK], sans jamais saturer. */
    private fun normalize(pcm: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val peak = (0 until input.limit()).maxOfOrNull { kotlin.math.abs(input.get(it).toInt()) } ?: 0
        if (peak == 0) return pcm
        val gain = (TARGET_PEAK.toDouble() / peak).coerceAtMost(MAX_GAIN)
        if (gain <= 1.0) return pcm
        val out = ByteBuffer.allocate(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until input.limit()) out.putShort((input.get(i) * gain).toInt().toShort())
        return out.array()
    }

    private fun rms(pcm: ByteArray): Int {
        val samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (!samples.hasRemaining()) return 0
        var sum = 0.0
        val count = samples.remaining()
        while (samples.hasRemaining()) {
            val v = samples.get().toDouble()
            sum += v * v
        }
        return kotlin.math.sqrt(sum / count).toInt()
    }

    private fun toWav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * BYTES_PER_SAMPLE)
            putShort(BYTES_PER_SAMPLE.toShort()); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return header.array() + pcm
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val WAV_HEADER_SIZE = 44
        const val TARGET_PEAK = 28_000
        /** Au-delà, on amplifierait surtout le bruit de fond. */
        const val MAX_GAIN = 30.0
    }
}
