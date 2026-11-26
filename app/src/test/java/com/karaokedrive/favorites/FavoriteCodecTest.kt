package com.karaokedrive.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteCodecTest {

    private val favorite = Favorite(
        id = "artiste_titre", title = "Titre", artist = "Artiste", durationMs = 180_000,
        lrc = "[00:01.00]une ligne inventée\n[00:04.00]une autre ligne", addedAtMs = 1_700_000_000_000,
    )

    @Test
    fun `un favori se relit à l'identique`() {
        assertEquals(favorite, FavoriteCodec.decode(FavoriteCodec.encode(favorite)))
    }

    @Test
    fun `un fichier illisible ou sans paroles est ignoré`() {
        assertNull(FavoriteCodec.decode("pas du json"))
        assertNull(FavoriteCodec.decode(FavoriteCodec.encode(favorite.copy(lrc = " "))))
    }
}
