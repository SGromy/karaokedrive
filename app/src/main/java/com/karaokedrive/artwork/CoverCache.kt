package com.karaokedrive.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.karaokedrive.cache.LruFiles
import java.io.File
import java.io.IOException

/**
 * Pochettes déjà téléchargées, gardées sur le téléphone : affichage immédiat quand un
 * morceau repasse, et sans réseau. Les moins récemment utilisées sont supprimées au-delà
 * de [MAX_ENTRIES] (~50 à 100 Ko chacune).
 */
class CoverCache(dir: File) {

    private val files = LruFiles(dir, "webp", MAX_ENTRIES)

    fun get(key: String): Bitmap? {
        val file = files.fileFor(key)?.takeIf(File::isFile) ?: return null
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) file.delete() else files.touch(file)
        return bitmap
    }

    fun put(key: String, cover: Bitmap) {
        val file = files.fileFor(key) ?: return
        try {
            files.ensureDir()
            file.outputStream().use { cover.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, it) }
            files.written(file)
        } catch (e: IOException) {
            Log.w("KaraokeDrive", "pochette non mise en cache : ${e.message}")
        }
    }

    private companion object {
        const val MAX_ENTRIES = 150
        const val QUALITY = 85
    }
}
