package com.karaokedrive.recognition

import android.util.Base64
import android.util.Log
import com.karaokedrive.BuildConfig
import com.karaokedrive.audio.AudioSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed interface RecognitionResult {
    /** [playOffsetMs] = position dans le morceau à la fin de l'extrait envoyé. */
    data class Match(
        val title: String,
        val artist: String,
        val durationMs: Long,
        val playOffsetMs: Long,
        /** Pochette fournie par la reconnaissance (Shazam), sinon cherchée ailleurs. */
        val coverUrl: String? = null,
    ) : RecognitionResult

    data object NoMatch : RecognitionResult
    data class Failure(val message: String) : RecognitionResult
}

/** Client minimal de l'API « identify » d'ACRCloud (empreinte audio + position dans le morceau). */
class AcrCloudRecognizer(
    private val host: String,
    private val accessKey: String,
    private val accessSecret: String,
    private val http: OkHttpClient,
) : SongRecognizer {
    override val name = "ACRCloud"

    override val isConfigured: Boolean
        get() = host.isNotBlank() && accessKey.isNotBlank() && accessSecret.isNotBlank()

    override suspend fun identify(sample: AudioSample): RecognitionResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RecognitionResult.Failure("Clés ACRCloud manquantes")
        try {
            http.newCall(buildRequest(sample)).execute().use { response ->
                val body = response.body.string()
                if (BuildConfig.DEBUG) Log.d(TAG, "HTTP ${response.code} status=${statusOf(body)}")
                if (!response.isSuccessful) RecognitionResult.Failure("ACRCloud HTTP ${response.code}")
                else parse(body)
            }
        } catch (e: IOException) {
            RecognitionResult.Failure("Réseau indisponible : ${e.message}")
        }
    }

    private fun buildRequest(sample: AudioSample): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val toSign = listOf("POST", ENDPOINT, accessKey, DATA_TYPE, SIGNATURE_VERSION, timestamp)
            .joinToString("\n")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sample", "sample.wav", sample.wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("sample_bytes", sample.wav.size.toString())
            .addFormDataPart("access_key", accessKey)
            .addFormDataPart("data_type", DATA_TYPE)
            .addFormDataPart("signature_version", SIGNATURE_VERSION)
            .addFormDataPart("signature", sign(toSign))
            .addFormDataPart("timestamp", timestamp)
            .build()
        return Request.Builder().url("https://$host$ENDPOINT").post(body).build()
    }

    private fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(accessSecret.toByteArray(), "HmacSHA1"))
        }
        return Base64.encodeToString(mac.doFinal(data.toByteArray()), Base64.NO_WRAP)
    }

    /** Uniquement le bloc « status » : jamais les métadonnées complètes dans les logs. */
    private fun statusOf(body: String): String =
        runCatching { JSONObject(body).optJSONObject("status")?.toString() }.getOrNull() ?: "illisible"

    companion object {
        private const val TAG = "KaraokeDrive"
        private const val ENDPOINT = "/v1/identify"
        private const val DATA_TYPE = "audio"
        private const val SIGNATURE_VERSION = "1"
        private const val CODE_OK = 0
        private const val CODE_NO_RESULT = 1001
        /** Extrait trop pauvre (silence, bruit, parole) : on réessaie simplement. */
        private const val CODE_NO_FINGERPRINT = 2004

        /** Visible pour les tests : transforme la réponse JSON d'ACRCloud en résultat. */
        fun parse(json: String): RecognitionResult {
            val root = runCatching { JSONObject(json) }.getOrNull()
                ?: return RecognitionResult.Failure("Réponse ACRCloud illisible")
            val status = root.optJSONObject("status")
            return when (status?.optInt("code", -1)) {
                CODE_OK -> parseMatch(root)
                CODE_NO_RESULT, CODE_NO_FINGERPRINT -> RecognitionResult.NoMatch
                else -> RecognitionResult.Failure("ACRCloud : ${status?.optString("msg") ?: "statut absent"}")
            }
        }

        private fun parseMatch(root: JSONObject): RecognitionResult {
            val music = root.optJSONObject("metadata")?.optJSONArray("music")?.optJSONObject(0)
                ?: return RecognitionResult.NoMatch
            return RecognitionResult.Match(
                title = music.optString("title"),
                artist = music.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty(),
                durationMs = music.optLong("duration_ms"),
                playOffsetMs = music.optLong("play_offset_ms"),
            )
        }
    }
}
