package com.karaokedrive.lyrics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Paroles inventées
    private val online = SyncedLyrics("[00:01.00]La route chante", 180_000)

    /** Sources en ligne simulées : renvoient les réponses prévues, dans l'ordre. */
    private class ScriptedSources(vararg answers: LyricsLookup) {
        private val queue = ArrayDeque(answers.toList())
        var calls = 0
        suspend fun find(title: String, artist: String, durationMs: Long, onLineLevel: (SyncedLyrics) -> Unit): LyricsLookup {
            calls++
            return queue.removeFirst()
        }
    }

    private fun repository(sources: ScriptedSources) = LyricsRepository(
        find = sources::find,
        cache = LyricsCache(folder.newFolder("cache")),
    )

    @Test
    fun `trouve les paroles en ligne puis les garde en cache`() = runTest {
        val sources = ScriptedSources(LyricsLookup.Found(online))
        val repo = repository(sources)

        val first = repo.load("Route de nuit", "Les Phares", 0)
        val second = repo.load("Route de nuit", "Les Phares", 0)

        assertEquals(LyricsResult.Found(online, LyricsOrigin.ONLINE), first)
        assertEquals(LyricsResult.Found(online, LyricsOrigin.CACHE), second)
        assertEquals(1, sources.calls)
    }

    @Test
    fun `se souvient qu'un morceau n'a pas de paroles`() = runTest {
        val sources = ScriptedSources(LyricsLookup.NotFound)
        val repo = repository(sources)

        assertEquals(LyricsResult.None, repo.load("Route de nuit", "Les Phares", 0))
        assertEquals(LyricsResult.None, repo.load("Route de nuit", "Les Phares", 0))
        assertEquals(1, sources.calls)
    }

    @Test
    fun `réessaie quand le service est en panne`() = runTest {
        val sources = ScriptedSources(LyricsLookup.Unavailable, LyricsLookup.Found(online))
        val repo = repository(sources)

        val result = repo.load("Route de nuit", "Les Phares", 0)

        assertEquals(LyricsResult.Found(online, LyricsOrigin.ONLINE), result)
        assertEquals(2_000L, currentTime) // première attente avant le nouvel essai
    }

    @Test
    fun `abandonne après plusieurs pannes sans rien mettre en cache`() = runTest {
        val sources = ScriptedSources(
            LyricsLookup.Unavailable, LyricsLookup.Unavailable, LyricsLookup.Unavailable, LyricsLookup.Unavailable,
            LyricsLookup.Found(online),
        )
        val repo = repository(sources)

        assertEquals(LyricsResult.Unreachable, repo.load("Route de nuit", "Les Phares", 0))
        assertEquals(4, sources.calls)
        assertEquals(14_000L, currentTime) // 2 + 4 + 8 s
        // Pas de « sans paroles » mémorisé : on redemande au prochain passage
        assertEquals(LyricsResult.Found(online, LyricsOrigin.ONLINE), repo.load("Route de nuit", "Les Phares", 0))
    }

    @Test
    fun `transmet l'aperçu ligne par ligne venu des sources en ligne`() = runTest {
        val word = SyncedLyrics("[00:01.00]<00:01.00>La <00:01.50>route", 180_000)
        val previews = mutableListOf<SyncedLyrics>()
        val repo = LyricsRepository(
            find = { _, _, _, onLineLevel -> onLineLevel(online); LyricsLookup.Found(word) },
            cache = LyricsCache(folder.newFolder("cache")),
        )

        val result = repo.load("Route de nuit", "Les Phares", 0) { previews += it }

        assertEquals(listOf(online), previews)
        assertEquals(LyricsResult.Found(word, LyricsOrigin.ONLINE), result)
        // Le cache garde la version finale (mot par mot), pas l'aperçu
        assertEquals(LyricsResult.Found(word, LyricsOrigin.CACHE), repo.load("Route de nuit", "Les Phares", 0))
    }
}
