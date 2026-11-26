package com.karaokedrive.karaoke

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenScheduleTest {

    @Test
    fun `après la première détection on confirme une fois`() {
        assertEquals(
            ListenSchedule.CONFIRM_INTERVAL_MS,
            ListenSchedule.afterMatch(measurements = 1, confirmed = false, remainingMs = 180_000),
        )
    }

    @Test
    fun `confirmation concordante, on ne revérifie qu'une fois par minute`() {
        assertEquals(
            ListenSchedule.WATCH_INTERVAL_MS,
            ListenSchedule.afterMatch(measurements = 2, confirmed = true, remainingMs = 170_000),
        )
    }

    @Test
    fun `fin du morceau proche, on réécoute juste après sa fin`() {
        assertEquals(
            40_000 + ListenSchedule.END_MARGIN_MS,
            ListenSchedule.afterMatch(measurements = 2, confirmed = true, remainingMs = 40_000),
        )
    }

    @Test
    fun `fin des paroles proche, on réécoute juste après la dernière ligne`() {
        assertEquals(
            20_000 + ListenSchedule.LYRICS_END_MARGIN_MS,
            ListenSchedule.afterMatch(measurements = 2, confirmed = true, remainingMs = 90_000, lyricsEndInMs = 20_000),
        )
    }

    @Test
    fun `paroles déjà finies, on attend la fin du morceau`() {
        assertEquals(
            30_000 + ListenSchedule.END_MARGIN_MS,
            ListenSchedule.afterMatch(measurements = 3, confirmed = true, remainingMs = 30_000, lyricsEndInMs = -5_000),
        )
    }

    @Test
    fun `mesures divergentes, on départage`() {
        assertEquals(
            ListenSchedule.CONFIRM_INTERVAL_MS,
            ListenSchedule.afterMatch(measurements = 2, confirmed = false, remainingMs = 170_000),
        )
    }

    @Test
    fun `après trop de départages, on laisse le vote trancher et on attend la fin`() {
        assertEquals(
            30_000 + ListenSchedule.END_MARGIN_MS,
            ListenSchedule.afterMatch(ListenSchedule.MAX_TRACK_MEASUREMENTS, confirmed = false, remainingMs = 30_000),
        )
    }

    @Test
    fun `morceau presque fini, on réécoute juste après sa fin même avant confirmation`() {
        assertEquals(
            4_000 + ListenSchedule.END_MARGIN_MS,
            ListenSchedule.afterMatch(measurements = 1, confirmed = false, remainingMs = 4_000),
        )
    }

    @Test
    fun `durée inconnue, vérification une fois par minute`() {
        assertEquals(
            ListenSchedule.WATCH_INTERVAL_MS,
            ListenSchedule.afterMatch(measurements = 2, confirmed = true, remainingMs = null),
        )
    }

    @Test
    fun `jamais moins que le délai minimal`() {
        assertEquals(ListenSchedule.MIN_DELAY_MS, ListenSchedule.afterMatch(3, confirmed = true, remainingMs = -10_000))
    }

    @Test
    fun `les échecs espacent les tentatives jusqu'à une minute`() {
        assertEquals(3_000L, ListenSchedule.afterMiss(1))
        assertEquals(6_000L, ListenSchedule.afterMiss(2))
        assertEquals(12_000L, ListenSchedule.afterMiss(3))
        assertEquals(ListenSchedule.MISS_MAX_MS, ListenSchedule.afterMiss(50))
    }
}
