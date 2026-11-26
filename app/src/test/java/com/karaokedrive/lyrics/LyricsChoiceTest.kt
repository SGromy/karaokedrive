package com.karaokedrive.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsChoiceTest {

    // Paroles inventées
    private val lines = SyncedLyrics("[00:12.00]La route chante\n[00:16.00]Sous les phares", 200_000)
    private val words = SyncedLyrics(
        "[00:12.100]<00:12.100>La <00:12.500>route <00:13.000>chante <00:14.200>\n" +
            "[00:16.000]<00:16.000>Sous <00:16.300>les <00:18.000>phares <00:19.800>",
        201_000,
    )

    @Test
    fun `préfère le timing mot par mot quand il correspond à la même version`() {
        assertEquals(LyricsLookup.Found(words), LyricsChoice.pick(LyricsLookup.Found(lines), words))
    }

    @Test
    fun `garde les lignes si la version mot par mot a une autre durée`() {
        val otherEdit = words.copy(durationMs = 260_000)

        assertEquals(LyricsLookup.Found(lines), LyricsChoice.pick(LyricsLookup.Found(lines), otherEdit))
    }

    @Test
    fun `garde les lignes si les paroles mot par mot sont décalées`() {
        val shifted = words.copy(lrc = words.lrc.replace("[00:12.100]<00:12.100>", "[00:20.100]<00:20.100>"))

        assertEquals(LyricsLookup.Found(lines), LyricsChoice.pick(LyricsLookup.Found(lines), shifted))
    }

    @Test
    fun `reprend la durée des lignes si la version mot par mot n'en a pas`() {
        val noDuration = words.copy(durationMs = 0)

        assertEquals(LyricsLookup.Found(noDuration.copy(durationMs = 200_000)), LyricsChoice.pick(LyricsLookup.Found(lines), noDuration))
    }

    @Test
    fun `utilise le mot par mot quand LRCLIB n'a rien ou est en panne`() {
        assertEquals(LyricsLookup.Found(words), LyricsChoice.pick(LyricsLookup.NotFound, words))
        assertEquals(LyricsLookup.Found(words), LyricsChoice.pick(LyricsLookup.Unavailable, words))
    }

    @Test
    fun `sans mot par mot, garde la réponse de LRCLIB`() {
        assertEquals(LyricsLookup.NotFound, LyricsChoice.pick(LyricsLookup.NotFound, null))
        assertEquals(LyricsLookup.Unavailable, LyricsChoice.pick(LyricsLookup.Unavailable, null))
    }

    @Test
    fun `choisit le bon candidat Kugou`() {
        val candidates = listOf(
            KugouCandidate("1", "k1", "Autre chanson", "Les Phares", 200_000),
            KugouCandidate("2", "k2", "Route de nuit (Remastered)", "Les Phares", 260_000),
            KugouCandidate("3", "k3", "Route de nuit", "Les Phares & Ami", 201_000),
        )

        assertEquals("3", KugouMatch.best(candidates, "Route de nuit", "Les Phares", durationMs = 200_000)?.id)
        assertEquals("2", KugouMatch.best(candidates, "Route de nuit", "Les Phares", durationMs = 0)?.id)
        assertNull(KugouMatch.best(candidates, "Inconnue", "Personne", durationMs = 0))
    }
}
