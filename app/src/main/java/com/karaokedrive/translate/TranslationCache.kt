package com.karaokedrive.translate

import com.karaokedrive.cache.LruFiles
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Traductions par IA déjà obtenues : une seule requête par chanson, puis disponibles sans
 * réseau. Seules les traductions par IA sont gardées (celle du téléphone est refaite à la
 * demande, pour que l'IA soit retentée dès que le réseau revient).
 */
class TranslationCache(dir: File) {

    private val files = LruFiles(dir, "json", MAX_ENTRIES)

    /**
     * Traductions de la chanson [key], si elles ont été faites pour exactement ces [lines]
     * (même texte, même langue cible) : une autre version des paroles est retraduite.
     */
    fun get(key: String, lines: List<String>): List<String?>? {
        val file = files.fileFor(key)?.takeIf(File::isFile) ?: return null
        val cached = try {
            val json = JSONObject(file.readText())
            val list = json.getJSONArray("translations")
            json.optString("fingerprint") to List(list.length()) { i -> if (list.isNull(i)) null else list.getString(i) }
        } catch (e: JSONException) {
            null
        } catch (e: IOException) {
            null
        }
        if (cached == null) file.delete() else files.touch(file)
        val (fingerprint, translations) = cached ?: return null
        return translations.takeIf { fingerprint == fingerprintOf(lines) && it.size == lines.size }
    }

    fun put(key: String, lines: List<String>, translations: List<String?>) {
        val file = files.fileFor(key) ?: return
        try {
            files.ensureDir()
            val list = JSONArray().apply { translations.forEach { put(it ?: JSONObject.NULL) } }
            val json = JSONObject()
                .put("version", VERSION)
                .put("fingerprint", fingerprintOf(lines))
                .put("translations", list)
            file.writeText(json.toString())
            files.written(file)
        } catch (e: IOException) {
            // Cache seulement : la chanson sera retraduite la prochaine fois
        }
    }

    private companion object {
        /** 2 : ajout de l'empreinte ; les entrées de la version 1 (sans empreinte) sont ignorées. */
        const val VERSION = 2
        const val MAX_ENTRIES = 1_000
        const val TARGET_LANGUAGE = "fr"

        /** Empreinte du texte source, de la langue cible et du format : change si l'un d'eux change. */
        fun fingerprintOf(lines: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("v$VERSION|$TARGET_LANGUAGE|".toByteArray())
            lines.forEach { digest.update(it.toByteArray()); digest.update(LINE_SEPARATOR) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val LINE_SEPARATOR = byteArrayOf(0)
    }
}
