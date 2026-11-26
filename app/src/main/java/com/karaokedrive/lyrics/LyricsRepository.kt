package com.karaokedrive.lyrics

import com.karaokedrive.karaoke.TrackKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** D'où viennent les paroles affichées (diagnostic). */
enum class LyricsOrigin { CACHE, ONLINE }

sealed interface LyricsResult {
    data class Found(val lyrics: SyncedLyrics, val origin: LyricsOrigin) : LyricsResult
    /** Aucune source n'a de paroles synchronisées pour ce morceau. */
    data object None : LyricsResult
    /** Services injoignables malgré plusieurs essais : on redemandera au prochain passage. */
    data object Unreachable : LyricsResult
}

/**
 * Paroles d'un morceau : cache du téléphone, sinon sources en ligne ([LyricsFinder]) avec
 * de nouveaux essais en cas de panne (plutôt que de conclure à tort qu'il n'y a pas de paroles).
 */
class LyricsRepository(
    private val find: suspend (title: String, artist: String, durationMs: Long, onLineLevel: (SyncedLyrics) -> Unit) -> LyricsLookup,
    private val cache: LyricsCache?,
    private val retryDelaysMs: List<Long> = RETRY_DELAYS_MS,
) {

    /**
     * @param onPreview paroles ligne par ligne arrivées avant le mot par mot (recherche en
     *   ligne seulement) : affichables tout de suite, le résultat final peut les remplacer.
     */
    suspend fun load(title: String, artist: String, durationMs: Long, onPreview: (SyncedLyrics) -> Unit = {}): LyricsResult {
        val key = TrackKey.fileName(title, artist).removeSuffix(".lrc")
        when (val cached = withContext(Dispatchers.IO) { cache?.get(key) }) {
            is CachedLyrics.Found -> return LyricsResult.Found(cached.lyrics, LyricsOrigin.CACHE)
            CachedLyrics.Missing -> return LyricsResult.None
            null -> Unit
        }
        for (wait in listOf(0L) + retryDelaysMs) {
            delay(wait)
            when (val result = find(title, artist, durationMs, onPreview)) {
                is LyricsLookup.Found -> {
                    withContext(Dispatchers.IO) { cache?.putFound(key, result.lyrics) }
                    return LyricsResult.Found(result.lyrics, LyricsOrigin.ONLINE)
                }
                LyricsLookup.NotFound -> {
                    withContext(Dispatchers.IO) { cache?.putMissing(key) }
                    return LyricsResult.None
                }
                LyricsLookup.Unavailable -> Unit // nouvel essai après la prochaine attente
            }
        }
        return LyricsResult.Unreachable
    }

    private companion object {
        /** Attentes avant de redemander quand les services sont momentanément injoignables. */
        val RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L)
    }
}
