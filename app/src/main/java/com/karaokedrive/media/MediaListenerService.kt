package com.karaokedrive.media

import android.service.notification.NotificationListenerService

/**
 * Accès aux notifications : c'est la clé qu'Android exige pour lire les sessions
 * média des autres applications (titre, artiste, position de lecture).
 * On n'utilise pas les notifications elles-mêmes.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        MediaWatcher.start(this)
    }

    override fun onListenerDisconnected() {
        MediaWatcher.stop()
    }
}
