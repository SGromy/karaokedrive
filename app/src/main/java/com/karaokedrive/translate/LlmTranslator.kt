package com.karaokedrive.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Traduction par IA via le format « chat completions » d'OpenAI (Gemini par défaut).
 * Service, modèle et clé viennent de local.properties (llm.baseUrl, llm.model, llm.apiKey).
 */
class LlmTranslator(
    http: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    /** Une chanson entière peut prendre une dizaine de secondes à traduire. */
    private val http = http.newBuilder().readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).build()

    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    /** Une traduction par ligne ; null si la réponse est inutilisable ; IOException si le service échoue. */
    suspend fun translate(lines: List<String>, language: String): List<String?>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(LlmTranslation.requestBody(model, lines, language).toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            // Le message d'erreur du service n'est pas repris : il pourrait contenir des détails du compte
            if (!response.isSuccessful) throw IOException("service de traduction : HTTP ${response.code}")
            val body = response.body.string()
            LlmTranslation.parseResponse(body, expectedCount = lines.size, original = lines)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val READ_TIMEOUT_S = 45L
    }
}
