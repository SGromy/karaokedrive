package com.karaokedrive.translate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTranslationTest {

    // Paroles inventées
    private val lines = listOf("The road is singing", "Ooh, ooh", "Under the headlights")

    private fun reply(content: String) = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))))
        .toString()

    @Test
    fun `la requête envoie toutes les lignes d'un coup au modèle choisi`() {
        val body = JSONObject(LlmTranslation.requestBody(model = "modele-test", lines = lines, language = "en"))

        assertEquals("modele-test", body.getString("model"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val user = messages.getJSONObject(1).getString("content")
        assertEquals(lines, JSONObject(user).getJSONArray("lines").let { a -> List(a.length()) { a.getString(it) } })
        assertTrue(user.contains("\"en\""))
    }

    @Test
    fun `lit une traduction par ligne, null pour les lignes à ne pas traduire`() {
        val content = """{"translations": ["La route chante", null, "Sous les phares"]}"""

        assertEquals(listOf("La route chante", null, "Sous les phares"), LlmTranslation.parseResponse(reply(content), expectedCount = 3))
    }

    @Test
    fun `accepte une réponse entourée d'un bloc de code`() {
        val content = "```json\n{\"translations\": [\"La route chante\", null, \"Sous les phares\"]}\n```"

        assertEquals(listOf("La route chante", null, "Sous les phares"), LlmTranslation.parseResponse(reply(content), expectedCount = 3))
    }

    @Test
    fun `une traduction identique à l'original n'est pas affichée`() {
        val content = """{"translations": ["La route chante", "Ooh, ooh", "  "]}"""

        val parsed = LlmTranslation.parseResponse(reply(content), expectedCount = 3, original = lines)

        assertEquals(listOf("La route chante", null, null), parsed)
    }

    @Test
    fun `refuse une réponse avec un nombre de lignes différent`() {
        val content = """{"translations": ["La route chante", "Sous les phares"]}"""

        assertNull(LlmTranslation.parseResponse(reply(content), expectedCount = 3))
    }

    @Test
    fun `refuse une réponse illisible`() {
        assertNull(LlmTranslation.parseResponse(reply("Désolé, je ne peux pas."), expectedCount = 3))
        assertNull(LlmTranslation.parseResponse("pas du json", expectedCount = 3))
    }
}
