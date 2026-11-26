package com.karaokedrive.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * Exécute la requête sans bloquer de fil : annuler la coroutine (morceau suivant, délai
 * dépassé) coupe aussi la requête, au lieu de la laisser aller jusqu'à son propre délai.
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            // Réponse arrivée juste après l'annulation : on la ferme, personne ne la lira
            continuation.resume(response) { _, unread, _ -> unread.close() }
        }
    })
}
