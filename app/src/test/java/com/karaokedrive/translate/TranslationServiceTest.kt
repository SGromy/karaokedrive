package com.karaokedrive.translate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class TranslationServiceTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Paroles inventées
    private val lines = listOf("The road is singing", "Under the headlights")
    private val byAi = listOf("La route chante", "Sous les phares")
    private val offline = listOf("La route est en train de chanter", "Sous les phares")

    private class Calls {
        var ai = 0
        var offline = 0
    }

    private fun service(
        language: String = "en",
        ai: (suspend (List<String>, String) -> List<String?>?)? = { _, _ -> byAi },
        calls: Calls = Calls(),
        cache: TranslationCache? = TranslationCache(folder.root),
    ) = TranslationService(
        detectLanguage = { language },
        ai = ai?.let { translate ->
            val counted: suspend (List<String>, String) -> List<String?>? = { l, lang -> calls.ai++; translate(l, lang) }
            counted
        },
        offline = { calls.offline++; offline },
        cache = cache,
    )

    @Test
    fun `traduit par l'IA et garde le résultat en cache`() = runTest {
        val calls = Calls()
        val cache = TranslationCache(folder.root)

        val first = service(calls = calls, cache = cache).translate("phares_route", lines)
        val second = service(calls = calls, cache = cache).translate("phares_route", lines)

        assertEquals(Translated(byAi, TranslationOrigin.AI), first)
        assertEquals(Translated(byAi, TranslationOrigin.CACHE), second)
        assertEquals(1, calls.ai)
    }

    @Test
    fun `une chanson en français n'est pas traduite`() = runTest {
        val calls = Calls()

        assertNull(service(language = "fr", calls = calls).translate("x", lines))
        assertEquals(0, calls.ai + calls.offline)
    }

    @Test
    fun `sans réseau, l'anglais est traduit sur le téléphone`() = runTest {
        val result = service(ai = { _, _ -> throw IOException("hors ligne") }).translate("x", lines)

        assertEquals(Translated(offline, TranslationOrigin.OFFLINE), result)
    }

    @Test
    fun `la traduction de secours n'est pas mise en cache, l'IA sera retentée`() = runTest {
        val calls = Calls()
        val cache = TranslationCache(folder.root)
        service(ai = { _, _ -> throw IOException("hors ligne") }, calls = calls, cache = cache).translate("x", lines)

        val retry = service(calls = calls, cache = cache).translate("x", lines)

        assertEquals(Translated(byAi, TranslationOrigin.AI), retry)
    }

    @Test
    fun `réponse inutilisable de l'IA, on passe au secours`() = runTest {
        val result = service(ai = { _, _ -> null }).translate("x", lines)

        assertEquals(Translated(offline, TranslationOrigin.OFFLINE), result)
    }

    @Test
    fun `l'espagnol est traduit par l'IA mais pas sans réseau`() = runTest {
        assertEquals(Translated(byAi, TranslationOrigin.AI), service(language = "es").translate("x", lines))
        assertNull(service(language = "es", ai = { _, _ -> throw IOException("hors ligne") }).translate("y", lines))
    }

    @Test
    fun `sans clé d'IA, on utilise la traduction du téléphone`() = runTest {
        assertEquals(Translated(offline, TranslationOrigin.OFFLINE), service(ai = null).translate("x", lines))
    }

    @Test
    fun `ignore un cache d'une autre version des paroles`() = runTest {
        val cache = TranslationCache(folder.root)
        cache.put("x", listOf("One single line"), listOf("Une seule ligne"))
        val calls = Calls()

        val result = service(calls = calls, cache = cache).translate("x", lines)

        assertEquals(Translated(byAi, TranslationOrigin.AI), result)
        assertEquals(1, calls.ai)
    }

    @Test
    fun `ignore un cache de paroles différentes ayant le même nombre de lignes`() = runTest {
        val cache = TranslationCache(folder.root)
        cache.put("x", listOf("The road was singing", "Under the streetlights"), listOf("Ancienne", "Traduction"))
        val calls = Calls()

        val result = service(calls = calls, cache = cache).translate("x", lines)

        assertEquals(Translated(byAi, TranslationOrigin.AI), result)
        assertEquals(1, calls.ai)
    }
}
