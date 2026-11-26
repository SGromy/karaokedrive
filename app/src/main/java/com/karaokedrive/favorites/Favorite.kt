package com.karaokedrive.favorites

import org.json.JSONObject

/** Morceau enregistré pour réviser ses paroles, même sans musique. */
data class Favorite(
    /** Identifiant stable ([com.karaokedrive.karaoke.TrackKey.fileName] sans extension). */
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    /** Paroles synchronisées au format LRC. */
    val lrc: String,
    val addedAtMs: Long,
)

/** Lecture / écriture JSON d'un favori (testable sans Android). */
object FavoriteCodec {

    private const val VERSION = 1

    fun encode(favorite: Favorite): String = JSONObject()
        .put("version", VERSION)
        .put("id", favorite.id)
        .put("title", favorite.title)
        .put("artist", favorite.artist)
        .put("durationMs", favorite.durationMs)
        .put("lrc", favorite.lrc)
        .put("addedAtMs", favorite.addedAtMs)
        .toString()

    /** null si le fichier est illisible ou incomplet (on l'ignore plutôt que de planter). */
    fun decode(json: String): Favorite? = runCatching {
        val o = JSONObject(json)
        Favorite(
            id = o.getString("id"),
            title = o.getString("title"),
            artist = o.getString("artist"),
            durationMs = o.optLong("durationMs", 0),
            lrc = o.getString("lrc").takeIf { it.isNotBlank() } ?: return null,
            addedAtMs = o.optLong("addedAtMs", 0),
        )
    }.getOrNull()
}
