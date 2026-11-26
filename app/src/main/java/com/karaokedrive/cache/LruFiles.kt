package com.karaokedrive.cache

import java.io.File

/**
 * Fichiers de cache nommés par clé dans un dossier : au-delà de [maxEntries], les moins
 * récemment utilisés sont supprimés. Partagé par le cache des paroles et celui des pochettes.
 */
class LruFiles(
    private val dir: File,
    private val extension: String,
    private val maxEntries: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Fichier de la clé ; null si la clé n'est pas un simple nom (sécurité : on reste dans [dir]). */
    fun fileFor(key: String): File? = key.takeIf(SAFE_KEY::matches)?.let { File(dir, "$it.$extension") }

    /** Marque le fichier comme récemment utilisé. */
    fun touch(file: File) {
        file.setLastModified(now())
    }

    /** À appeler après l'écriture d'un fichier : le date et fait de la place si besoin. */
    fun written(file: File) {
        touch(file)
        val files = dir.listFiles { f -> f.extension == extension }.orEmpty()
        if (files.size <= maxEntries) return
        files.sortedBy(File::lastModified).take(files.size - maxEntries).forEach(File::delete)
    }

    /** Crée le dossier du cache s'il n'existe pas encore. */
    fun ensureDir() {
        dir.mkdirs()
    }

    private companion object {
        val SAFE_KEY = Regex("[a-z0-9_]{1,200}")
    }
}
