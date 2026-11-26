package com.karaokedrive.lyrics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsFinderTest {

    // Paroles inventées, même version du morceau dans les deux sources
    private val lines = SyncedLyrics("[00:12.00]La route chante\n[00:16.00]Sous les phares", 200_000)
    private val words = SyncedLyrics(
        "[00:12.100]<00:12.100>La <00:12.500>route <00:13.000>chante <00:14.200>\n" +
            "[00:16.000]<00:16.000>Sous <00:16.300>les <00:18.000>phares <00:19.800>",
        201_000,
    )

    /** Sources simulées qui répondent après [lineAfterMs] / [wordAfterMs] (temps virtuel). */
    private fun finder(
        lineAfterMs: Long,
        wordAfterMs: Long,
        lineResult: LyricsLookup = LyricsLookup.Found(lines),
        wordResult: SyncedLyrics? = words,
    ) = LyricsFinder(
        lineLevel = { _, _, _ -> delay(lineAfterMs); lineResult },
        wordLevel = { _, _, _ -> delay(wordAfterMs); wordResult },
        budgetMs = BUDGET_MS,
        io = EmptyCoroutineContext,
    )

    @Test
    fun `affiche les lignes d'abord puis garde le mot par mot arrivé ensuite`() = runTest {
        val previews = mutableListOf<Pair<SyncedLyrics, Long>>()

        val result = finder(lineAfterMs = 500, wordAfterMs = 3_000).find("Route de nuit", "Les Phares", 0) {
            previews += it to currentTime
        }

        assertEquals(listOf(lines to 500L), previews)
        assertEquals(LyricsLookup.Found(words), result)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `pas d'aperçu quand le mot par mot est déjà là`() = runTest {
        val previews = mutableListOf<SyncedLyrics>()

        val result = finder(lineAfterMs = 2_000, wordAfterMs = 500).find("Route de nuit", "Les Phares", 0) { previews += it }

        assertEquals(emptyList<SyncedLyrics>(), previews)
        assertEquals(LyricsLookup.Found(words), result)
    }

    @Test
    fun `n'attend pas une source trop lente au-delà du délai global`() = runTest {
        val result = finder(lineAfterMs = 500, wordAfterMs = 60_000).find("Route de nuit", "Les Phares", 0)

        assertEquals(LyricsLookup.Found(lines), result)
        assertEquals(BUDGET_MS, currentTime)
    }

    @Test
    fun `rien d'arrivé à temps compte comme une panne, pas comme une absence de paroles`() = runTest {
        val result = finder(lineAfterMs = 60_000, wordAfterMs = 60_000).find("Route de nuit", "Les Phares", 0)

        assertEquals(LyricsLookup.Unavailable, result)
    }

    @Test
    fun `garde le mot par mot arrivé à temps même si LRCLIB traîne`() = runTest {
        val result = finder(lineAfterMs = 60_000, wordAfterMs = 1_000).find("Route de nuit", "Les Phares", 0)

        assertEquals(LyricsLookup.Found(words), result)
    }

    private companion object {
        const val BUDGET_MS = 8_000L
    }
}
