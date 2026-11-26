package com.karaokedrive.lyrics

import com.karaokedrive.cache.LruFiles
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Ce que le cache sait d'un morceau. */
sealed interface CachedLyrics {
    data class Found(val lyrics: SyncedLyrics) : CachedLyrics
    /** LRCLIB n'avait pas de paroles synchronisées (mémorisé quelques jours seulement). */
    data object Missing : CachedLyrics
}

/**
 * Paroles déjà trouvées, gardées sur le téléphone : les tubes repassent souvent à la radio,
 * l'affichage devient instantané et marche sans réseau (tunnels, zones blanches) ou quand
 * LRCLIB est en panne. Un fichier JSON par morceau ; les moins récemment utilisés sont
 * supprimés au-delà de [maxEntries].
 *
 * @param key identifiant de fichier du morceau ([com.karaokedrive.karaoke.TrackKey.fileName] sans extension)
 */
class LyricsCache(
    dir: File,
    private val now: () -> Long = System::currentTimeMillis,
    maxEntries: Int = MAX_ENTRIES,
) {

    private val files = LruFiles(dir, "json", maxEntries, now)

    fun get(key: String): CachedLyrics? {
        val file = files.fileFor(key)?.takeIf(File::isFile) ?: return null
        val entry = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val cached = when (entry.optString("status")) {
            STATUS_FOUND -> entry.optString("lrc").takeIf { it.isNotBlank() }?.let { lrc ->
                CachedLyrics.Found(SyncedLyrics(lrc, entry.optLong("durationMs", 0)))
            }
            STATUS_MISSING -> CachedLyrics.Missing.takeIf { now() - entry.optLong("savedAtMs", 0) <= MISSING_TTL_MS }
            else -> null
        }
        if (cached == null) file.delete() else files.touch(file)
        return cached
    }

    fun putFound(key: String, lyrics: SyncedLyrics) =
        write(key, JSONObject().put("status", STATUS_FOUND).put("lrc", lyrics.lrc).put("durationMs", lyrics.durationMs))

    fun putMissing(key: String) = write(key, JSONObject().put("status", STATUS_MISSING))

    private fun write(key: String, entry: JSONObject) {
        val file = files.fileFor(key) ?: return
        try {
            files.ensureDir()
            file.writeText(entry.put("version", VERSION).put("savedAtMs", now()).toString())
            files.written(file)
        } catch (e: IOException) {
            // Cache seulement : en cas d'échec, les paroles seront redemandées la prochaine fois
        }
    }

    companion object {
        /** Un morceau sans paroles est redemandé au bout de 3 jours (LRCLIB s'enrichit sans cesse). */
        const val MISSING_TTL_MS = 3L * 24 * 3600 * 1000
        /** ~2 à 5 Ko par morceau : 1 000 morceaux tiennent dans quelques Mo. */
        const val MAX_ENTRIES = 1_000
        private const val VERSION = 1
        private const val STATUS_FOUND = "found"
        private const val STATUS_MISSING = "missing"
    }
}
