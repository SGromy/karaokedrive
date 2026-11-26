package com.karaokedrive.translate

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Requête et réponse de traduction par IA (format « chat completions » d'OpenAI, compris
 * par Gemini, DeepSeek, Mistral, Groq…). Toute la chanson part d'un coup pour que le modèle
 * comprenne le sens et les tournures ; il renvoie une traduction par ligne. Calcul pur, testable.
 */
object LlmTranslation {

    private val SYSTEM_PROMPT = """
        Tu traduis des paroles de chansons en français pour un karaoké.
        Traduis le sens de chaque ligne, avec un français naturel : respecte les expressions
        idiomatiques, l'argot et le ton de la chanson, sans mot-à-mot.
        Garde exactement le même nombre de lignes, dans le même ordre (une ligne peut se
        comprendre grâce aux lignes voisines).
        Mets null pour une ligne déjà en français ou sans rien à traduire (onomatopées
        comme « ooh », « yeah », « la la la »).
        Réponds uniquement avec un objet JSON : {"translations": [chaîne ou null, …]}.
    """.trimIndent()

    fun requestBody(model: String, lines: List<String>, language: String): String {
        val userContent = JSONObject()
            .put("language", language)
            .put("lines", JSONArray(lines))
            .toString()
        return JSONObject()
            .put("model", model)
            .put("temperature", TEMPERATURE)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", userContent)),
            )
            .toString()
    }

    /**
     * Traductions lues dans la réponse du service ; null si elle est inutilisable (pas de JSON,
     * nombre de lignes différent), pour basculer sur la traduction de secours.
     * @param original lignes d'origine : une « traduction » identique n'est pas affichée
     */
    fun parseResponse(body: String, expectedCount: Int, original: List<String>? = null): List<String?>? = try {
        val content = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val json = JSONObject(stripCodeFence(content))
        val list = json.getJSONArray("translations")
        if (list.length() != expectedCount) {
            null
        } else {
            List(expectedCount) { i ->
                val text = if (list.isNull(i)) null else list.optString(i).trim()
                text?.takeIf { it.isNotEmpty() && !it.equals(original?.getOrNull(i)?.trim(), ignoreCase = true) }
            }
        }
    } catch (e: JSONException) {
        null
    }

    /** Certains modèles entourent le JSON d'un bloc ```json … ``` malgré la consigne. */
    private fun stripCodeFence(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    /** Peu de fantaisie : on veut une traduction fidèle, pas une réécriture. */
    private const val TEMPERATURE = 0.3
}
