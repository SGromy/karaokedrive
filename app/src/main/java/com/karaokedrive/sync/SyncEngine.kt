package com.karaokedrive.sync

import android.os.SystemClock
import kotlin.math.abs

/**
 * Horloge de lecture : convertit « le morceau était à X ms quand on a fini
 * d'enregistrer » en position courante, et absorbe les recalages successifs.
 */
class SyncEngine(
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Position du morceau [trackPositionMs] observée à l'instant [clockAtMs]. */
    private data class Anchor(val trackPositionMs: Long, val clockAtMs: Long)

    @Volatile private var anchor: Anchor? = null

    /** Réglage manuel (négatif = paroles plus tôt), pour compenser la latence de l'autoradio. */
    @Volatile var calibrationMs: Long = 0

    val isAnchored: Boolean get() = anchor != null

    fun positionMs(): Long? = anchor?.let { rawPosition(it) + calibrationMs }

    /**
     * Dernières mesures, exprimées en « instant de début du morceau » (horloge monotone) :
     * les bonnes mesures désignent toutes le même instant, les erreurs de refrain répété un autre.
     */
    private val recentStarts = ArrayDeque<Long>()

    /**
     * Intègre une nouvelle mesure. Un petit écart est lissé. Un grand écart n'est appliqué
     * que si la nouvelle position l'emporte nettement ([JUMP_MARGIN] voix d'avance) parmi
     * les mesures récentes : sur une boucle répétée, la reconnaissance peut renvoyer
     * plusieurs fois de suite le mauvais passage du morceau.
     *
     * @return l'écart mesuré (estimation − position affichée), `null` au premier ancrage.
     */
    @Synchronized
    fun anchor(trackPositionMs: Long, capturedAtMs: Long): Long? {
        val now = clockMs()
        val start = capturedAtMs - trackPositionMs
        remember(start)
        val current = anchor ?: run {
            anchor = Anchor(now - start, now)
            return null
        }
        val currentPosition = rawPosition(current)
        val drift = (now - start) - currentPosition
        if (abs(drift) <= JUMP_THRESHOLD_MS) {
            anchor = Anchor(currentPosition + (drift * SMOOTHING).toLong(), now)
            return drift
        }
        val currentStart = now - currentPosition
        if (votesFor(start) >= votesFor(currentStart) + JUMP_MARGIN) anchor = Anchor(now - start, now)
        return drift
    }

    @Synchronized
    fun reset() {
        anchor = null
        recentStarts.clear()
    }

    private fun remember(start: Long) {
        recentStarts.addLast(start)
        while (recentStarts.size > HISTORY_SIZE) recentStarts.removeFirst()
    }

    private fun votesFor(start: Long): Int = recentStarts.count { abs(it - start) <= JUMP_THRESHOLD_MS }

    private fun rawPosition(a: Anchor): Long = a.trackPositionMs + (clockMs() - a.clockAtMs)

    companion object {
        /** Au-delà, on considère que l'écart est réel (radio coupée, passage sauté). */
        const val JUMP_THRESHOLD_MS = 700L
        /** Part de l'écart corrigée à chaque recalage, pour éviter les sauts visibles. */
        const val SMOOTHING = 0.5
        /** Voix d'avance exigées pour sauter : deux erreurs d'affilée ne suffisent pas. */
        const val JUMP_MARGIN = 2
        /** Nombre de mesures récentes prises en compte (≈ 2 minutes de recalages). */
        const val HISTORY_SIZE = 6
    }
}
