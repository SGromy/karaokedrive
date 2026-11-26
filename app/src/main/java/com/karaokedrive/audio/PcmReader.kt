package com.karaokedrive.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/** Boucle de lecture du micro, indépendante d'Android (testable avec un faux micro). */
internal object PcmReader {

    /** Au-delà, un micro qui ne renvoie plus que des lectures vides est considéré comme perdu. */
    const val MAX_STALL_MS = 1_000L
    /** Pause entre deux lectures vides, pour ne pas tourner à vide. */
    const val STALL_PAUSE_MS = 20L

    /**
     * Lit [targetBytes] octets via [read] (même contrat que `AudioRecord.read`). Échoue sur un
     * code d'erreur, ou si aucune donnée n'arrive pendant [maxStallMs] (horloge [clockMs]).
     */
    suspend fun read(
        targetBytes: Int,
        chunkSize: Int,
        read: (buffer: ByteArray, offset: Int, size: Int) -> Int,
        clockMs: () -> Long,
        maxStallMs: Long = MAX_STALL_MS,
    ): ByteArray {
        val pcm = ByteArrayOutputStream(targetBytes)
        val chunk = ByteArray(chunkSize)
        var lastDataAtMs = clockMs()
        while (pcm.size() < targetBytes) {
            coroutineContext.ensureActive()
            val count = read(chunk, 0, minOf(chunk.size, targetBytes - pcm.size()))
            check(count >= 0) { "Erreur de lecture micro ($count)" }
            if (count > 0) {
                pcm.write(chunk, 0, count)
                lastDataAtMs = clockMs()
                continue
            }
            check(clockMs() - lastDataAtMs < maxStallMs) { "Le micro ne fournit plus de son" }
            delay(STALL_PAUSE_MS)
        }
        return pcm.toByteArray()
    }
}
