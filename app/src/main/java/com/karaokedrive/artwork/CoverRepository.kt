package com.karaokedrive.artwork

import android.graphics.Bitmap
import com.karaokedrive.karaoke.TrackKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pochette d'un morceau : cache du téléphone, sinon téléchargement (puis mise en cache). */
class CoverRepository(private val client: CoverArtClient, private val cache: CoverCache?) {

    /** D'où vient la pochette (diagnostic). */
    enum class Origin { CACHE, RECOGNITION, DEEZER }

    data class Cover(val bitmap: Bitmap?, val origin: Origin)

    suspend fun load(title: String, artist: String, knownUrl: String?): Cover {
        val key = TrackKey.fileName(title, artist).removeSuffix(".lrc")
        withContext(Dispatchers.IO) { cache?.get(key) }?.let { return Cover(it, Origin.CACHE) }
        val downloaded = client.find(title, artist, knownUrl)
        if (downloaded != null) withContext(Dispatchers.IO) { cache?.put(key, downloaded) }
        return Cover(downloaded, if (knownUrl != null) Origin.RECOGNITION else Origin.DEEZER)
    }
}
