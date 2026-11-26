package com.karaokedrive.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcmReaderTest {

    /** Faux micro : renvoie les tailles de lecture prévues, dans l'ordre, puis toujours la dernière. */
    private class ScriptedMic(vararg counts: Int) {
        private val queue = ArrayDeque(counts.toList())
        var reads = 0
        fun read(buffer: ByteArray, offset: Int, size: Int): Int {
            reads++
            val count = (if (queue.size > 1) queue.removeFirst() else queue.first()).coerceAtMost(size)
            for (i in 0 until count) buffer[offset + i] = (reads % 100).toByte()
            return count
        }
    }

    @Test
    fun `assemble les lectures jusqu'à la taille voulue`() = runTest {
        val mic = ScriptedMic(4, 4, 4)

        val pcm = PcmReader.read(targetBytes = 10, chunkSize = 4, read = mic::read, clockMs = { currentTime })

        assertArrayEquals(byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3), pcm)
    }

    @Test
    fun `une lecture vide passagère n'interrompt pas la capture`() = runTest {
        val mic = ScriptedMic(4, 0, 0, 4)

        val pcm = PcmReader.read(targetBytes = 8, chunkSize = 4, read = mic::read, clockMs = { currentTime })

        assertEquals(8, pcm.size)
        assertEquals(2 * PcmReader.STALL_PAUSE_MS, currentTime) // une courte pause par lecture vide
    }

    @Test
    fun `un micro qui ne renvoie plus rien finit en erreur au lieu de boucler`() = runTest {
        val mic = ScriptedMic(4, 0)

        val error = runCatching {
            PcmReader.read(targetBytes = 8, chunkSize = 4, read = mic::read, clockMs = { currentTime })
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Le micro ne fournit plus de son", error?.message)
        assertEquals(PcmReader.MAX_STALL_MS, currentTime) // abandon après le délai, pas avant
    }

    @Test
    fun `un code d'erreur du micro est remonté`() = runTest {
        val mic = ScriptedMic(-3)

        val error = runCatching {
            PcmReader.read(targetBytes = 8, chunkSize = 4, read = mic::read, clockMs = { currentTime })
        }.exceptionOrNull()

        assertEquals("Erreur de lecture micro (-3)", error?.message)
    }
}
