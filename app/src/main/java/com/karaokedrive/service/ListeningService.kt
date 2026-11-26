package com.karaokedrive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karaokedrive.BuildConfig
import com.karaokedrive.R
import com.karaokedrive.karaoke.KaraokeController
import com.karaokedrive.ui.MainActivity

/**
 * Garde le micro actif quand l'app passe en arrière-plan (par ex. quand on
 * regarde l'écran Android Auto) : Android l'exige pour tout enregistrement audio.
 */
class ListeningService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) Log.d(TAG, "onStartCommand action=${intent?.action} flags=$flags startId=$startId")
        when (intent?.action) {
            ACTION_START -> {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
                KaraokeController.init(this)
                // Écoute refusée : ne pas garder une notification micro qui n'enregistre rien
                if (!KaraokeController.startListening()) stopSelf()
            }
            // ACTION_STOP, mais aussi une relance par le système (intent null) ou une commande
            // inconnue : on n'ouvre jamais le micro sans demande explicite de l'utilisateur.
            else -> {
                if (intent?.action == ACTION_STOP) KaraokeController.stop()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Écoute de la radio", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListeningService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("KaraokeDrive écoute la radio")
            .setContentText("Les paroles se synchronisent automatiquement")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "KaraokeDrive"
        private const val ACTION_START = "com.karaokedrive.START"
        private const val ACTION_STOP = "com.karaokedrive.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ListeningService::class.java).setAction(ACTION_START),
            )
        }

        /**
         * Libère le micro sans toucher à l'affichage : l'appelant gère l'état du contrôleur.
         * (Un ACTION_STOP asynchrone effacerait un entraînement lancé juste après.)
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
