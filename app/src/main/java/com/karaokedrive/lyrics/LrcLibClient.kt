package com.karaokedrive.lyrics

import com.karaokedrive.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

/** Paroles synchronisées et durée du morceau selon LRCLIB (0 si inconnue). */
data class SyncedLyrics(val lrc: String, val durationMs: Long)

/** Résultat d'une recherche : il faut distinguer « introuvable » de « service en panne ». */
sealed interface LyricsLookup {
    data class Found(val lyrics: SyncedLyrics) : LyricsLookup
    data object NotFound : LyricsLookup
    /** LRCLIB injoignable ou surchargé (réseau, 5xx, 429) : réessayer plus tard. */
    data object Unavailable : LyricsLookup
}

/** Réponse d'erreur du service (5xx, 429), à distinguer d'une absence de résultat (404). */
private class ServiceUnavailable(code: Int) : IOException("LRCLIB HTTP $code")

/**
 * Récupère des paroles synchronisées (LRC) sur lrclib.net.
 * ⚠️ Source communautaire : OK pour un prototype perso, à remplacer par une
 * source sous licence (ex. Musixmatch) avant toute publication.
 */
class LrcLibClient(private val http: OkHttpClient) {

    /** Cherche les paroles synchronisées. [durationMs] ≤ 0 = durée inconnue. */
    suspend fun findSynced(title: String, artist: String, durationMs: Long): LyricsLookup =
        withContext(Dispatchers.IO) {
            try {
                // Du plus précis au plus large : titre exact, titre nettoyé, artiste principal,
                // puis recherche libre (« artiste titre ») en dernier recours
                val found = LyricsQuery.variants(title, artist).firstNotNullOfOrNull { (t, a) ->
                    exactMatch(t, a, durationMs) ?: searchMatch(t, a, durationMs)
                } ?: freeSearch(LyricsQuery.variants(title, artist).last(), durationMs)
                found?.let(LyricsLookup::Found) ?: LyricsLookup.NotFound
            } catch (e: IOException) {
                LyricsLookup.Unavailable
            }
        }

    private suspend fun exactMatch(title: String, artist: String, durationMs: Long): SyncedLyrics? {
        val url = BASE.newBuilder().addPathSegments("api/get")
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (durationMs > 0) addQueryParameter("duration", (durationMs / 1000).toString()) }
            .build()
        return get(url)?.let { JSONObject(it).toSyncedLyrics() }
    }

    private suspend fun searchMatch(title: String, artist: String, durationMs: Long): SyncedLyrics? {
        val url = BASE.newBuilder().addPathSegments("api/search")
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        return pickBest(get(url)?.let(::JSONArray) ?: return null, durationMs)
    }

    private fun pickBest(results: JSONArray, durationMs: Long): SyncedLyrics? {
        val candidates = (0 until results.length())
            .map(results::getJSONObject)
            .filter { it.syncedOrNull() != null }
        // Durée inconnue (Shazam ne la donne pas) : on garde le résultat le plus pertinent
        val best = if (durationMs <= 0) candidates.firstOrNull()
        else candidates.minByOrNull { abs(it.optDouble("duration", 0.0) * 1000 - durationMs) }
        return best?.toSyncedLyrics()
    }

    private suspend fun freeSearch(query: Pair<String, String>, durationMs: Long): SyncedLyrics? {
        val url = BASE.newBuilder().addPathSegments("api/search")
            .addQueryParameter("q", "${query.second} ${query.first}")
            .build()
        return pickBest(get(url)?.let(::JSONArray) ?: return null, durationMs)
    }

    private suspend fun get(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return http.newCall(request).await().use { r ->
            when {
                r.isSuccessful -> r.body?.string()
                r.code == 429 || r.code >= 500 -> throw ServiceUnavailable(r.code)
                else -> null // 404 : pas de paroles sous ce nom
            }
        }
    }

    private fun JSONObject.toSyncedLyrics(): SyncedLyrics? =
        syncedOrNull()?.let { SyncedLyrics(it, (optDouble("duration", 0.0) * 1000).toLong()) }

    private fun JSONObject.syncedOrNull(): String? =
        optString("syncedLyrics").takeIf { has("syncedLyrics") && !isNull("syncedLyrics") && it.isNotBlank() }

    private companion object {
        val BASE = "https://lrclib.net/".toHttpUrl()
        const val USER_AGENT = "KaraokeDrive/0.1 (prototype personnel)"
    }
}
