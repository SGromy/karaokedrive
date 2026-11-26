package com.karaokedrive.karaoke

/**
 * Horloge du mode entraînement (paroles sans musique) : lecture, pause, déplacement et
 * vitesse. Immuable : chaque action renvoie une nouvelle horloge.
 *
 * @param anchorPositionMs position du morceau à l'instant [anchorClockMs]
 * @param anchorClockMs horloge monotone au dernier changement (lecture, pause, déplacement…)
 */
data class PracticeClock(
    val durationMs: Long,
    val anchorPositionMs: Long = 0,
    val anchorClockMs: Long = 0,
    val speed: Float = 1f,
    val isPlaying: Boolean = false,
) {
    fun positionAt(nowMs: Long): Long {
        val elapsed = if (isPlaying) ((nowMs - anchorClockMs) * speed).toLong() else 0
        return (anchorPositionMs + elapsed).coerceIn(0, durationMs)
    }

    fun isFinishedAt(nowMs: Long): Boolean = positionAt(nowMs) >= durationMs

    fun play(nowMs: Long): PracticeClock {
        // Relancer à la fin repart du début
        val from = if (isFinishedAt(nowMs)) 0 else positionAt(nowMs)
        return copy(anchorPositionMs = from, anchorClockMs = nowMs, isPlaying = true)
    }

    fun pause(nowMs: Long): PracticeClock = copy(anchorPositionMs = positionAt(nowMs), anchorClockMs = nowMs, isPlaying = false)

    fun seekBy(deltaMs: Long, nowMs: Long): PracticeClock =
        copy(anchorPositionMs = (positionAt(nowMs) + deltaMs).coerceIn(0, durationMs), anchorClockMs = nowMs)

    fun restart(nowMs: Long): PracticeClock = copy(anchorPositionMs = 0, anchorClockMs = nowMs)

    /** Vitesse suivante : 1× → 0,75× → 0,5× → 1×. */
    fun nextSpeed(nowMs: Long): PracticeClock {
        val next = SPEEDS[(SPEEDS.indexOf(speed).coerceAtLeast(0) + 1) % SPEEDS.size]
        return copy(anchorPositionMs = positionAt(nowMs), anchorClockMs = nowMs, speed = next)
    }

    companion object {
        val SPEEDS = listOf(1f, 0.75f, 0.5f)
    }
}
