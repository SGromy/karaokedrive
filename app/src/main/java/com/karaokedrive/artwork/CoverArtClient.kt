package com.karaokedrive.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Pochette du morceau : l'adresse donnée par la reconnaissance (Shazam) si elle existe,
 * sinon une recherche sur l'API publique de Deezer (gratuite, sans clé).
 */
class CoverArtClient(private val http: OkHttpClient) {

    suspend fun find(title: String, artist: String, knownUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = knownUrl?.takeIf { it.isNotBlank() } ?: searchDeezer(title, artist) ?: return@withContext null
            download(url)
        } catch (e: IOException) {
            null
        }
    }

    private fun searchDeezer(title: String, artist: String): String? {
        val url = "https://api.deezer.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "artist:\"$artist\" track:\"$title\"")
            .addQueryParameter("limit", "1")
            .build()
        return http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) parseDeezer(response.body.string()) else null
        }
    }

    private fun download(url: String): Bitmap? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val bytes = response.body.bytes().takeIf { response.isSuccessful } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

    companion object {
        /** Taille visée : largement suffisante pour une vignette et un fond flouté. */
        private const val TARGET_PX = 600

        fun sampleSizeFor(width: Int): Int {
            var size = 1
            while (width / (size * 2) >= TARGET_PX) size *= 2
            return size
        }

        /** Adresse de la pochette dans une réponse de recherche Deezer. */
        fun parseDeezer(json: String): String? =
            runCatching {
                JSONObject(json).optJSONArray("data")?.optJSONObject(0)?.optJSONObject("album")
                    ?.let { album -> album.optString("cover_big").ifBlank { album.optString("cover_xl") } }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()

        /** Adresse de la pochette dans une réponse de reconnaissance Shazam. */
        fun parseShazamTrack(track: JSONObject?): String? =
            track?.optJSONObject("images")
                ?.let { images -> images.optString("coverarthq").ifBlank { images.optString("coverart") } }
                ?.takeIf { it.isNotBlank() }
    }
}
