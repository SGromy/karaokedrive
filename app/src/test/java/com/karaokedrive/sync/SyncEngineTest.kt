package com.karaokedrive.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    private var now = 0L
    private val engine = SyncEngine(clockMs = { now })

    @Test
    fun `sans ancrage la position est inconnue`() {
        assertFalse(engine.isAnchored)
        assertNull(engine.positionMs())
    }

    @Test
    fun `la position avance avec l'horloge depuis la fin de capture`() {
        now = 10_000
        engine.anchor(trackPositionMs = 42_000, capturedAtMs = 9_000)

        now = 12_500

        assertEquals(45_500L, engine.positionMs())
    }

    @Test
    fun `la calibration décale la position affichée`() {
        engine.anchor(trackPositionMs = 5_000, capturedAtMs = 0)
        engine.calibrationMs = -300

        assertEquals(4_700L, engine.positionMs())
    }

    @Test
    fun `un petit écart est lissé plutôt qu'appliqué d'un coup`() {
        engine.anchor(trackPositionMs = 10_000, capturedAtMs = 0)

        engine.anchor(trackPositionMs = 10_200, capturedAtMs = 0)

        val position = engine.positionMs()!!
        assertTrue(position in 10_001..10_199)
    }

    @Test
    fun `un grand écart isolé (mesure aberrante) est ignoré`() {
        engine.anchor(trackPositionMs = 10_000, capturedAtMs = 0)

        engine.anchor(trackPositionMs = 60_000, capturedAtMs = 0)

        assertEquals(10_000L, engine.positionMs())
    }

    @Test
    fun `deux erreurs d'affilée sur un refrain répété ne font pas sauter l'affichage`() {
        // Cas réel : bonne mesure, puis deux mesures décalées de +104 s (boucle répétée), puis bonne
        engine.anchor(trackPositionMs = 10_000, capturedAtMs = 0)
        now = 20_000
        engine.anchor(trackPositionMs = 30_000 + 104_000, capturedAtMs = 20_000)
        now = 40_000
        engine.anchor(trackPositionMs = 50_000 + 104_000, capturedAtMs = 40_000)

        assertEquals(50_000L, engine.positionMs())

        now = 60_000
        engine.anchor(trackPositionMs = 70_000, capturedAtMs = 60_000)
        assertEquals(70_000L, engine.positionMs())
    }

    @Test
    fun `une première mesure fausse est corrigée quand les bonnes mesures l'emportent nettement`() {
        engine.anchor(trackPositionMs = 50_000, capturedAtMs = 0) // fausse : la vraie position est 10 s
        now = 20_000
        engine.anchor(trackPositionMs = 30_000, capturedAtMs = 20_000)
        now = 40_000
        engine.anchor(trackPositionMs = 50_000, capturedAtMs = 40_000)

        assertEquals(90_000L, engine.positionMs()) // 2 voix contre 1 : pas encore assez net

        now = 60_000
        engine.anchor(trackPositionMs = 70_000, capturedAtMs = 60_000)
        assertEquals(70_000L, engine.positionMs()) // 3 contre 1 : on se recale
    }

    @Test
    fun `anchor renvoie l'écart mesuré pour le diagnostic`() {
        engine.anchor(trackPositionMs = 10_000, capturedAtMs = 0)

        val drift = engine.anchor(trackPositionMs = 10_300, capturedAtMs = 0)

        assertEquals(300L, drift)
    }

    @Test
    fun `reset oublie l'ancrage`() {
        engine.anchor(trackPositionMs = 1_000, capturedAtMs = 0)

        engine.reset()

        assertNull(engine.positionMs())
    }
}
