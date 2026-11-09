package corsica.universita.aplomb.notif

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import corsica.universita.aplomb.R
import corsica.universita.aplomb.app
import corsica.universita.aplomb.work.SyncWorker
import kotlinx.coroutines.launch

/**
 * Réception FCM. Jamais lancé par l'app : Play Services garde UNE connexion ouverte avec les serveurs Google
 * pour tout le téléphone, et réveille Aplomb (process relancé si besoin) quand un message arrive.
 * Dans la PWA, c'était le navigateur qui recevait (Web Push + VAPID) ; ici c'est l'OS, même app tuée.
 *
 * Message « data » attendu (voir outils/envoyer-push.mjs) :
 *   { "title": "…", "body": "…", "onglet": "releves" | "niveau" | …, "releveId": "<uuid>", "sync": "1" }
 */
class AplombMessagingService : FirebaseMessagingService() {

    /** Le jeton : l'adresse de CETTE installation d'Aplomb. Le serveur en a besoin pour lui envoyer un push. */
    override fun onRegistered(token: String) {
        Log.d(TAG, "Jeton FCM : $token")
        app.scope.launch { app.reglages.sauverFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        Log.d(TAG, "Push FCM reçu : data=$d, notification=${message.notification?.title}")
        // Travail silencieux : le serveur demande une synchronisation (aucune notification affichée).
        if (d["sync"] == "1") SyncWorker.planifier(this, immediat = true)

        val titre = d["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val texte = d["body"] ?: message.notification?.body ?: return
        Notifications.message(this, titre, texte, onglet = d["onglet"], releveId = d["releveId"])
    }

    private companion object {
        const val TAG = "Aplomb"
    }
}
