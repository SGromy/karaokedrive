package com.karaokedrive.karaoke

import com.karaokedrive.audio.AudioSample
import com.karaokedrive.recognition.RecognitionResult
import com.karaokedrive.recognition.SongRecognizer
import com.karaokedrive.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Ce que la boucle d'écoute demande au reste de l'application. */
interface ListeningHost {
    /**
     * Nouveau morceau reconnu : affiche ses paroles et sa pochette.
     * @return la durée du morceau retenue (0 si inconnue), ou null s'il n'y a pas de paroles
     */
    suspend fun onNewTrack(match: RecognitionResult.Match): Long?
    fun hasLyrics(): Boolean
    /** Fin de la dernière ligne des paroles affichées (position dans le morceau), null sans paroles. */
    fun lyricsEndMs(): Long?
    /** Rien de reconnu (pub, animateur, silence) pendant le morceau, ou avant le premier. */
    fun onNoMatch()
    /** Le morceau est terminé (paroles finies ou durée écoulée) et plus rien n'est reconnu. */
    fun onSongOver()
    fun onFailure(message: String)
}

/**
 * Boucle d'écoute au micro : extrait → reconnaissance → nouveau morceau ou recalage de la
 * synchro → attente avant la prochaine écoute ([ListenSchedule]). Indépendante d'Android :
 * micro et horloge sont fournis, ce qui permet de la tester sur une horloge virtuelle.
 */
class ListeningLoop(
    private val record: suspend () -> AudioSample,
    private val recognizer: SongRecognizer,
    private val sync: SyncEngine,
    private val track: CurrentTrack,
    private val host: ListeningHost,
    private val clockMs: () -> Long,
    private val log: (String) -> Unit = {},
) {
    /** Mesures réussies sur le morceau en cours, et échecs d'affilée : pilotent [ListenSchedule]. */
    private var measurementsForTrack = 0
    private var consecutiveMisses = 0
    /** Requêtes envoyées depuis le lancement (diagnostic). */
    private var requestCount = 0
    /** Instant (horloge) de la fin prévue du morceau en cours, null si inconnue. */
    private var expectedEndAtMs: Long? = null

    /** Écoute jusqu'à l'annulation (Stop, Rescanner…) ; une erreur ne fait que retarder l'essai suivant. */
    suspend fun run(): Nothing {
        while (true) {
            val pause = try {
                listenOnce()
            } catch (e: CancellationException) {
                throw e // arrêt normal : surtout pas un « Oups »
            } catch (e: Exception) {
                host.onFailure(e.message ?: "Erreur inconnue")
                ListenSchedule.afterMiss(++consecutiveMisses)
            }
            delay(pause)
        }
    }

    /** Un cycle d'écoute ; renvoie le délai avant le prochain. */
    suspend fun listenOnce(): Long {
        val sample = record()
        val result = recognizer.identify(sample)
        requestCount++
        log("extrait rms=${sample.rms} → ${result::class.simpleName} (requête n°$requestCount)")
        return when (result) {
            is RecognitionResult.Match -> {
                consecutiveMisses = 0
                onMatch(result, sample.capturedAtMs)
            }
            RecognitionResult.NoMatch -> {
                if (isSongOver()) {
                    log("morceau terminé, plus rien de reconnu : retour à l'écoute")
                    forgetTrack()
                    host.onSongOver()
                } else {
                    host.onNoMatch()
                }
                ListenSchedule.afterMiss(++consecutiveMisses)
            }
            is RecognitionResult.Failure -> {
                host.onFailure(result.message)
                ListenSchedule.afterMiss(++consecutiveMisses)
            }
        }
    }

    private suspend fun onMatch(match: RecognitionResult.Match, capturedAtMs: Long): Long {
        val key = TrackKey.of(match.title, match.artist)
        if (key != track.key) {
            track.key = key
            measurementsForTrack = 0
            sync.reset()
            log("reconnu « ${match.title} — ${match.artist} » offset=${match.playOffsetMs}")
            val duration = host.onNewTrack(match)
            track.durationMs = duration ?: match.durationMs
            if (duration == null) {
                // Pas de paroles : inutile de mesurer, on surveille simplement le passage au morceau suivant
                val remaining = (track.durationMs - match.playOffsetMs - (clockMs() - capturedAtMs)).takeIf { track.durationMs > 0 }
                expectedEndAtMs = remaining?.let { clockMs() + it }
                return ListenSchedule.afterMatch(ListenSchedule.MAX_TRACK_MEASUREMENTS, true, remaining)
            }
        }
        var confirmed = false
        if (host.hasLyrics()) {
            val drift = sync.anchor(match.playOffsetMs, capturedAtMs)
            confirmed = drift != null && abs(drift) <= SyncEngine.JUMP_THRESHOLD_MS
            if (drift != null) log("recalage : écart mesuré $drift ms")
        }
        measurementsForTrack++
        val remaining = rawPositionMs()?.let { track.durationMs - it }?.takeIf { track.durationMs > 0 }
        expectedEndAtMs = remaining?.let { clockMs() + it }
        val lyricsEndIn = host.lyricsEndMs()?.let { end -> sync.positionMs()?.let { end - it } }
        return ListenSchedule.afterMatch(measurementsForTrack, confirmed, remaining, lyricsEndIn)
    }

    /**
     * Le morceau affiché est fini : sa dernière ligne de paroles est passée (avec un peu de
     * marge), ou sa durée est écoulée. Un silence en plein milieu ne compte pas.
     */
    private fun isSongOver(): Boolean {
        if (track.key == null) return false
        val lyricsOver = host.lyricsEndMs()?.let { end -> sync.positionMs()?.let { it > end + SONG_OVER_GRACE_MS } } == true
        val durationOver = expectedEndAtMs?.let { clockMs() > it } == true
        return lyricsOver || durationOver
    }

    private fun forgetTrack() {
        track.clear()
        sync.reset()
        measurementsForTrack = 0
        expectedEndAtMs = null
    }

    /** Remet les compteurs à zéro (nouvelle écoute). */
    fun reset() {
        measurementsForTrack = 0
        consecutiveMisses = 0
        expectedEndAtMs = null
    }

    /** Position dans le repère de la reconnaissance (sans le réglage de décalage). */
    private fun rawPositionMs(): Long? = sync.positionMs()?.minus(sync.calibrationMs)

    private companion object {
        /** Marge après la dernière ligne avant de considérer les paroles finies. */
        const val SONG_OVER_GRACE_MS = 3_000L
    }
}
