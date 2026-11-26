package com.karaokedrive.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavoritesRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Morceau inventé
    private val favorite = Favorite("phares_route", "Route de nuit", "Les Phares", 180_000, "[00:01.00]La route chante", 1_000)

    @Test
    fun `enregistre un favori sans laisser de fichier temporaire`() {
        val dir = File(folder.root, "favorites")
        val repo = FavoritesRepository(dir)

        assertTrue(repo.save(favorite, cover = null))

        assertEquals(listOf(favorite), repo.list())
        assertEquals(listOf("phares_route.json"), dir.list()?.toList())
    }

    @Test
    fun `signale un échec d'écriture au lieu de le considérer comme enregistré`() {
        val notADirectory = folder.newFile("favorites")
        val repo = FavoritesRepository(notADirectory)

        assertFalse(repo.save(favorite, cover = null))
        assertFalse(repo.contains(favorite.id))
    }

    @Test
    fun `ignore les restes d'une écriture interrompue`() {
        val dir = folder.newFolder("favorites")
        File(dir, "phares_route.json.tmp").writeText("{\"id\":")
        val repo = FavoritesRepository(dir)

        assertEquals(emptyList<Favorite>(), repo.list())
        assertFalse(repo.contains(favorite.id))
    }

    @Test
    fun `remplace un favori existant et le supprime`() {
        val repo = FavoritesRepository(folder.newFolder("favorites"))
        repo.save(favorite, cover = null)

        assertTrue(repo.save(favorite.copy(durationMs = 200_000), cover = null))
        assertEquals(200_000L, repo.find(favorite.id)?.durationMs)

        assertTrue(repo.delete(favorite.id))
        assertFalse(repo.contains(favorite.id))
    }
}
