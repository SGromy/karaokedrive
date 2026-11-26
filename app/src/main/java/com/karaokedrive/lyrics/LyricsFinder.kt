package com.karaokedrive.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

/**
 * Cherche les paroles dans toutes les sources en même temps (pas d'attente en plus) :
 * LRCLIB pour les lignes, Kugou pour le timing mot par mot, puis garde la meilleure
 * réponse ([LyricsChoice]). Kugou comble aussi les morceaux absents de LRCLIB.
 *
 * @param budgetMs délai global par morceau : au-delà, on garde ce qui est arrivé à temps
 *   plutôt que d'attendre la source la plus lente.
 */
class LyricsFinder(
    private val lineLevel: suspend (title: String, artist: String, durationMs: Long) -> LyricsLookup,
    private val wordLevel: suspend (title: String, artist: String, durationMs: Long) -> SyncedLyrics?,
    private val budgetMs: Long = BUDGET_MS,
    private val io: CoroutineContext = Dispatchers.IO,
    private val log: (String) -> Unit = {},
) {

    /**
     * @param onLineLevel appelé avec les paroles ligne par ligne dès qu'elles arrivent, si le
     *   mot par mot se fait encore attendre : de quoi les afficher sans attendre Kugou.
     */
    suspend fun find(
        title: String,
        artist: String,
        durationMs: Long,
        onLineLevel: (SyncedLyrics) -> Unit = {},
    ): LyricsLookup = coroutineScope {
        val start = TimeSource.Monotonic.markNow()
        val lines = async(io) {
            lineLevel(title, artist, durationMs).also { log("LRCLIB : ${it::class.simpleName} en ${start.elapsedNow().inWholeMilliseconds} ms") }
        }
        val words = async(io) {
            wordLevel(title, artist, durationMs).also { log("Kugou : ${if (it != null) "trouvé" else "rien"} en ${start.elapsedNow().inWholeMilliseconds} ms") }
        }
        withTimeoutOrNull(budgetMs) {
            val found = lines.await()
            if (found is LyricsLookup.Found && !words.isCompleted) onLineLevel(found.lyrics)
            LyricsChoice.pick(found, words.await())
        } ?: run {
            log("délai de $budgetMs ms dépassé : on garde ce qui est arrivé à temps")
            // Ligne par ligne non arrivée = service trop lent : « indisponible », pas « introuvable »
            val found = if (lines.isCompleted) lines.await() else LyricsLookup.Unavailable
            val wordsFound = if (words.isCompleted) words.await() else null
            lines.cancel()
            words.cancel()
            LyricsChoice.pick(found, wordsFound)
        }
    }

    private companion object {
        /**
         * Valeur de départ prudente (plusieurs requêtes LRCLIB s'enchaînent dans le pire cas),
         * à ajuster d'après les durées mesurées dans les journaux de débogage.
         */
        const val BUDGET_MS = 10_000L
    }
}
