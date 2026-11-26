package com.karaokedrive.recognition.shazam

import com.karaokedrive.artwork.CoverArtClient
import com.karaokedrive.audio.AudioSample
import com.karaokedrive.recognition.RecognitionResult
import com.karaokedrive.recognition.SongRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.TimeZone
import java.util.UUID

/**
 * Reconnaissance via les serveurs de Shazam, comme SongRec / shazamio.
 *
 * ⚠️ API non officielle : contraire aux conditions d'utilisation de Shazam et susceptible
 * de cesser de fonctionner sans préavis. Acceptable pour un prototype personnel avec
 * ACRCloud en secours, pas pour une publication.
 */
class ShazamRecognizer(private val http: OkHttpClient) : SongRecognizer {

    override val name = "Shazam"
    override val isConfigured = true

    override suspend fun identify(sample: AudioSample): RecognitionResult = withContext(Dispatchers.Default) {
        val signature = SignatureGenerator().generate(sample.pcm)
        if (signature.peakCount == 0) return@withContext RecognitionResult.NoMatch
        try {
            withContext(Dispatchers.IO) {
                http.newCall(buildRequest(signature)).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) RecognitionResult.Failure("Shazam HTTP ${response.code}")
                    else parse(body, signature.durationMs)
                }
            }
        } catch (e: IOException) {
            RecognitionResult.Failure("Réseau indisponible : ${e.message}")
        }
    }

    private fun buildRequest(signature: ShazamSignature): Request {
        val payload = JSONObject()
            .put("timezone", TimeZone.getDefault().id)
            .put("signature", JSONObject().put("uri", signature.toDataUri()).put("samplems", signature.durationMs))
            .put("timestamp", System.currentTimeMillis())
            .put("context", JSONObject())
            .put("geolocation", JSONObject())
        val url = "https://amp.shazam.com/discovery/v5/fr/FR/android/-/tag/" +
            "${UUID.randomUUID().toString().uppercase()}/${UUID.randomUUID().toString().uppercase()}" +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true" +
            "&hubv5minorversion=v5.1&hidelb=true&video=v3"
        return Request.Builder()
            .url(url)
            .header("X-Shazam-Platform", "IPHONE")
            .header("X-Shazam-AppVersion", "14.1.0")
            .header("Accept", "*/*")
            .header("Accept-Language", "fr")
            .header("User-Agent", USER_AGENT)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    companion object {
        private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8a Build/AP2A.240805.005)"

        /**
         * Lit la réponse de Shazam. Shazam donne la position du **début** de l'extrait ;
         * l'app raisonne sur la **fin** (instant de fin de capture), d'où l'ajout de sa durée.
         * La durée du morceau n'est pas fournie (0) : elle viendra de LRCLIB.
         */
        fun parse(json: String, sampleDurationMs: Long): RecognitionResult {
            val root = runCatching { JSONObject(json) }.getOrNull()
                ?: return RecognitionResult.Failure("Réponse Shazam illisible")
            val match = root.optJSONArray("matches")?.optJSONObject(0) ?: return RecognitionResult.NoMatch
            val track = root.optJSONObject("track") ?: return RecognitionResult.NoMatch
            val title = track.optString("title")
            val artist = track.optString("subtitle")
            if (title.isBlank()) return RecognitionResult.NoMatch
            val startMs = (match.optDouble("offset", 0.0) * 1000).toLong()
            return RecognitionResult.Match(
                title, artist, durationMs = 0, playOffsetMs = startMs + sampleDurationMs,
                coverUrl = CoverArtClient.parseShazamTrack(track),
            )
        }
    }
}
