package com.karaokedrive.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LruFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000L

    @Test
    fun `range chaque clé dans le dossier du cache`() {
        val files = LruFiles(folder.root, "webp", maxEntries = 10, now = { clock })

        assertEquals(folder.root.resolve("artiste_titre.webp"), files.fileFor("artiste_titre"))
    }

    @Test
    fun `refuse les clés qui sortiraient du dossier`() {
        val files = LruFiles(folder.root, "webp", maxEntries = 10, now = { clock })

        assertNull(files.fileFor("../evasion"))
        assertNull(files.fileFor("a/b"))
        assertNull(files.fileFor(""))
    }

    @Test
    fun `supprime les moins récemment utilisés au-delà de la limite`() {
        val files = LruFiles(folder.root, "webp", maxEntries = 2, now = { clock })
        fun write(key: String) = requireNotNull(files.fileFor(key)).also { it.writeText(key); files.written(it) }

        val a = write("a"); clock += 10
        write("b"); clock += 10
        files.touch(a); clock += 10 // « a » vient d'être utilisé : « b » est le plus ancien
        write("c")

        assertEquals(setOf("a.webp", "c.webp"), folder.root.list()?.toSet())
    }

    @Test
    fun `ne touche pas aux fichiers d'une autre extension`() {
        folder.root.resolve("garde.json").writeText("{}")
        val files = LruFiles(folder.root, "webp", maxEntries = 0, now = { clock })

        requireNotNull(files.fileFor("x")).also { it.writeText("x"); files.written(it) }

        assertEquals(setOf("garde.json"), folder.root.list()?.toSet())
    }
}
