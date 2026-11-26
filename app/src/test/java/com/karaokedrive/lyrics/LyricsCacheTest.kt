package com.karaokedrive.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private fun cache(maxEntries: Int = 100) = LyricsCache(folder.root, now = { clock }, maxEntries = maxEntries)

    private val lyrics = SyncedLyrics("[00:01.00]La route chante\n[00:04.00]Sous les phares", 181_000)

    @Test
    fun `retrouve des paroles enregistrées`() {
        cache().putFound("artiste_titre", lyrics)

        assertEquals(CachedLyrics.Found(lyrics), cache().get("artiste_titre"))
    }

    @Test
    fun `un morceau inconnu n'est pas en cache`() {
        assertNull(cache().get("inconnu"))
    }

    @Test
    fun `se souvient qu'un morceau n'a pas de paroles`() {
        cache().putMissing("sans_paroles")

        assertEquals(CachedLyrics.Missing, cache().get("sans_paroles"))
    }

    @Test
    fun `oublie au bout de 3 jours qu'un morceau n'avait pas de paroles`() {
        cache().putMissing("sans_paroles")
        clock += LyricsCache.MISSING_TTL_MS + 1

        assertNull(cache().get("sans_paroles"))
    }

    @Test
    fun `garde les paroles trouvées sans limite de temps`() {
        cache().putFound("artiste_titre", lyrics)
        clock += 365L * 24 * 3600 * 1000

        assertEquals(CachedLyrics.Found(lyrics), cache().get("artiste_titre"))
    }

    @Test
    fun `retire les morceaux les moins récemment utilisés au-delà de la limite`() {
        val small = cache(maxEntries = 2)
        small.putFound("a", lyrics); clock += 10
        small.putFound("b", lyrics); clock += 10
        small.get("a"); clock += 10 // « a » vient d'être utilisé : c'est « b » le plus ancien
        small.putFound("c", lyrics)

        assertEquals(CachedLyrics.Found(lyrics), small.get("a"))
        assertNull(small.get("b"))
        assertEquals(CachedLyrics.Found(lyrics), small.get("c"))
    }

    @Test
    fun `refuse une clé qui sortirait du dossier du cache`() {
        cache().putFound("../evasion", lyrics)

        assertNull(cache().get("../evasion"))
        assertEquals(0, folder.root.parentFile?.listFiles { f -> f.name.startsWith("evasion") }?.size ?: 0)
    }

    @Test
    fun `ignore un fichier abîmé`() {
        folder.root.resolve("abime.json").writeText("{pas du json")

        assertNull(cache().get("abime"))
    }
}
