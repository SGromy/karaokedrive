package com.karaokedrive.favorites

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Favoris enregistrés sur le téléphone (dossier privé de l'app) : un fichier JSON par
 * morceau, plus sa jaquette. Disponibles hors connexion, pour réviser sans musique.
 *
 * Accès disque uniquement : à appeler hors du fil de l'interface.
 */
class FavoritesRepository(private val dir: File) {

    fun list(): List<Favorite> =
        dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { file -> runCatching { FavoriteCodec.decode(file.readText()) }.getOrNull() }
            .sortedByDescending { it.addedAtMs }

    fun find(id: String): Favorite? = jsonFile(id).takeIf(File::isFile)?.let { FavoriteCodec.decode(it.readText()) }

    fun contains(id: String): Boolean = jsonFile(id).isFile

    /**
     * Enregistre le favori et sa jaquette ; renvoie false en cas d'erreur d'écriture.
     * Chaque fichier est écrit à côté puis renommé : jamais de fichier à moitié écrit. Le JSON
     * vient en dernier, car c'est lui qui fait exister le favori : pas de favori sans sa jaquette.
     */
    fun save(favorite: Favorite, cover: Bitmap?): Boolean = try {
        dir.mkdirs()
        if (cover != null) {
            writeAtomically(coverFile(favorite.id)) { out ->
                if (!cover.compress(Bitmap.CompressFormat.WEBP_LOSSY, COVER_QUALITY, out)) throw IOException("jaquette non compressée")
            }
        } else {
            coverFile(favorite.id).delete() // pas d'ancienne jaquette d'un autre enregistrement
        }
        writeAtomically(jsonFile(favorite.id)) { it.write(FavoriteCodec.encode(favorite).toByteArray()) }
        true
    } catch (e: IOException) {
        Log.w("KaraokeDrive", "favori non enregistré : ${e.message}")
        if (!contains(favorite.id)) coverFile(favorite.id).delete() // pas de jaquette orpheline
        false
    }

    /** Supprime le favori ; renvoie false s'il n'a pas pu être effacé. */
    fun delete(id: String): Boolean {
        val json = jsonFile(id)
        val removed = !json.exists() || json.delete()
        if (removed) coverFile(id).delete()
        return removed
    }

    fun cover(id: String): Bitmap? = coverFile(id).takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path) }

    private fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
        val temp = File(dir, "${target.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                write(out)
                out.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temp.delete() // reste d'une écriture interrompue ; sans effet après le renommage
        }
    }

    private fun jsonFile(id: String) = File(dir, "$id.json")
    private fun coverFile(id: String) = File(dir, "$id.webp")

    private companion object {
        const val COVER_QUALITY = 85
    }
}
