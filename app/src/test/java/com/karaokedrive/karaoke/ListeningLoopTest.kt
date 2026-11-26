package com.karaokedrive.karaoke

import com.karaokedrive.audio.AudioSample
import com.karaokedrive.recognition.RecognitionResult
import com.karaokedrive.recognition.SongRecognizer
import com.karaokedrive.sync.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningLoopTest {

    /** Reconnaissance simulée : renvoie les réponses prévues, dans l'ordre. */
    private class ScriptedRecognizer(vararg answers: () -> RecognitionResult) : SongRecognizer {
        private val queue = ArrayDeque(answers.toList())
        var calls = 0
        override val name = "simulé"
        override val isConfigured = true
        override suspend fun identify(sample: AudioSample): RecognitionResult {
            calls++
            return queue.removeFirst().invoke()
        }
    }

    /** Ce que la boucle demande au reste de l'app, enregistré pour les vérifications. */
    private class RecordingHost(
        private val lyricsDurationMs: Long? = TRACK_MS,
        /** Fin de la dernière ligne des paroles (position dans le morceau). */
        private val lyricsEnd: Long? = null,
    ) : ListeningHost {
        val newTracks = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var noMatches = 0
        var songsOver = 0
        var lyricsShown = false
        override suspend fun onNewTrack(match: RecognitionResult.Match): Long? {
            newTracks += match.title
            lyricsShown = lyricsDurationMs != null
            return lyricsDurationMs
        }
        override fun hasLyrics() = lyricsShown
        override fun lyricsEndMs(): Long? = lyricsEnd.takeIf { lyricsShown }
        override fun onNoMatch() { noMatches++ }
        override fun onSongOver() { songsOver++; lyricsShown = false }
        override fun onFailure(message: String) { failures += message }
    }

    private fun match(title: String, offsetMs: Long, durationMs: Long = TRACK_MS) =
        RecognitionResult.Match(title, "Les Phares", durationMs, offsetMs)

    private fun TestScope.loop(recognizer: SongRecognizer, host: ListeningHost, track: CurrentTrack = CurrentTrack()): ListeningLoop {
        val clock = { testScheduler.currentTime }
        return ListeningLoop(
            record = { AudioSample(ByteArray(0), capturedAtMs = clock(), rms = 0) },
            recognizer = recognizer,
            sync = SyncEngine(clockMs = clock),
            track = track,
            host = host,
            clockMs = clock,
        )
    }

    @Test
    fun `reconnaît un morceau, charge ses paroles une fois puis confirme 10 s plus tard`() = runTest {
        val host = RecordingHost()
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }), host)

        val pause = loop.listenOnce()

        assertEquals(listOf("Route de nuit"), host.newTracks)
        assertEquals(ListenSchedule.CONFIRM_INTERVAL_MS, pause)
    }

    @Test
    fun `une fois la position confirmée, revérifie une minute plus tard`() = runTest {
        val host = RecordingHost()
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }, { match("Route de nuit", 40_000) }), host)

        loop.listenOnce()
        advanceTimeBy(10_000)
        val pause = loop.listenOnce()

        assertEquals(listOf("Route de nuit"), host.newTracks) // pas de rechargement des paroles
        assertEquals(ListenSchedule.WATCH_INTERVAL_MS, pause)
    }

    @Test
    fun `réécoute juste après la dernière ligne des paroles`() = runTest {
        val host = RecordingHost(lyricsEnd = 70_000)
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }, { match("Route de nuit", 40_000) }), host)

        loop.listenOnce()
        advanceTimeBy(10_000)
        val pause = loop.listenOnce()

        assertEquals(70_000 - 40_000 + ListenSchedule.LYRICS_END_MARGIN_MS, pause)
    }

    @Test
    fun `paroles finies et plus rien de reconnu, on repasse en écoute`() = runTest {
        val host = RecordingHost(lyricsEnd = 35_000)
        val track = CurrentTrack()
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }, { RecognitionResult.NoMatch }), host, track)

        loop.listenOnce()
        advanceTimeBy(10_000) // position 40 s, paroles finies à 35 s
        loop.listenOnce()

        assertEquals(1, host.songsOver)
        assertEquals(0, host.noMatches)
        assertEquals(null, track.key)
    }

    @Test
    fun `un silence en plein milieu des paroles ne coupe pas le karaoké`() = runTest {
        val host = RecordingHost(lyricsEnd = 150_000)
        val track = CurrentTrack()
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }, { RecognitionResult.NoMatch }), host, track)

        loop.listenOnce()
        advanceTimeBy(10_000)
        loop.listenOnce()

        assertEquals(0, host.songsOver)
        assertEquals(1, host.noMatches)
        assertEquals(TrackKey.of("Route de nuit", "Les Phares"), track.key)
    }

    @Test
    fun `morceau terminé sans paroles et plus rien de reconnu, on repasse en écoute`() = runTest {
        val host = RecordingHost(lyricsDurationMs = null)
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 195_000) }, { RecognitionResult.NoMatch }), host)

        loop.listenOnce()
        advanceTimeBy(10_000) // position 205 s pour un morceau de 200 s
        loop.listenOnce()

        assertEquals(1, host.songsOver)
    }

    @Test
    fun `un nouveau morceau recharge les paroles`() = runTest {
        val host = RecordingHost()
        val track = CurrentTrack()
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }, { match("Aube claire", 5_000) }), host, track)

        loop.listenOnce()
        val pause = loop.listenOnce()

        assertEquals(listOf("Route de nuit", "Aube claire"), host.newTracks)
        assertEquals(TrackKey.of("Aube claire", "Les Phares"), track.key)
        assertEquals(ListenSchedule.CONFIRM_INTERVAL_MS, pause)
    }

    @Test
    fun `sans paroles, surveille une fois par minute sans remesurer`() = runTest {
        val host = RecordingHost(lyricsDurationMs = null)
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }), host)

        val pause = loop.listenOnce()

        assertEquals(ListenSchedule.WATCH_INTERVAL_MS, pause)
    }

    @Test
    fun `pubs et silences espacent les essais de plus en plus`() = runTest {
        val host = RecordingHost()
        val loop = loop(
            ScriptedRecognizer({ RecognitionResult.NoMatch }, { RecognitionResult.NoMatch }, { RecognitionResult.NoMatch }),
            host,
        )

        val pauses = List(3) { loop.listenOnce() }

        assertEquals(listOf(3_000L, 6_000L, 12_000L), pauses)
        assertEquals(3, host.noMatches)
    }

    @Test
    fun `un morceau reconnu remet à zéro l'attente après les échecs`() = runTest {
        val host = RecordingHost()
        val loop = loop(
            ScriptedRecognizer(
                { RecognitionResult.NoMatch }, { RecognitionResult.NoMatch },
                { match("Route de nuit", 30_000) }, { RecognitionResult.NoMatch },
            ),
            host,
        )

        repeat(3) { loop.listenOnce() }
        val pause = loop.listenOnce()

        assertEquals(ListenSchedule.MISS_BASE_MS, pause)
    }

    @Test
    fun `une panne du service est signalée puis réessayée plus tard`() = runTest {
        val host = RecordingHost()
        val loop = loop(ScriptedRecognizer({ RecognitionResult.Failure("réseau coupé") }), host)

        val pause = loop.listenOnce()

        assertEquals(listOf("réseau coupé"), host.failures)
        assertEquals(ListenSchedule.MISS_BASE_MS, pause)
    }

    @Test
    fun `une erreur imprévue n'arrête pas l'écoute, et Stop l'arrête proprement`() = runTest {
        val host = RecordingHost()
        val recognizer = ScriptedRecognizer({ throw IOException("micro occupé") }, { match("Route de nuit", 30_000) })
        val loop = loop(recognizer, host)

        val job = launch { loop.run() }
        runCurrent()
        advanceTimeBy(ListenSchedule.MISS_BASE_MS + 1)
        job.cancel() // Stop

        assertEquals(listOf("micro occupé"), host.failures)
        assertEquals(listOf("Route de nuit"), host.newTracks)
        assertTrue(job.isCancelled)
        assertEquals(2, recognizer.calls)
    }

    @Test
    fun `oublier le morceau fait recharger les paroles au prochain passage`() = runTest {
        val host = RecordingHost()
        val track = CurrentTrack()
        val loop = loop(ScriptedRecognizer({ match("Route de nuit", 30_000) }, { match("Route de nuit", 60_000) }), host, track)

        loop.listenOnce()
        track.clear()
        loop.listenOnce()

        assertEquals(listOf("Route de nuit", "Route de nuit"), host.newTracks)
    }

    private companion object {
        const val TRACK_MS = 200_000L
    }
}
