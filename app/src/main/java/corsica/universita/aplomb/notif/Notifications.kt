package corsica.universita.aplomb.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import corsica.universita.aplomb.MainActivity
import corsica.universita.aplomb.R

/**
 * Tout ce qui touche aux notifications. Un canal par TYPE de message, que l'utilisateur règle lui-même
 * (Réglages › Applications › Aplomb › Notifications). L'app propose une importance ; l'utilisateur a le dernier mot.
 */
object Notifications {
    const val CANAL_SYNC = "sync"                 // « Relevés synchronisés » : basse, silencieuse
    const val CANAL_MESSAGES = "messages"         // push FCM du serveur : normale
    const val CANAL_SURVEILLANCE = "surveillance" // notification permanente du foreground service : basse
    const val CANAL_ALERTE = "alerte"             // l'aplomb a bougé : haute, son + bannière

    const val ID_SYNC = 1
    const val ID_SURVEILLANCE = 2
    const val ID_ALERTE = 3

    /** Extras lus par MainActivity.handleIntent : quel onglet, ou quel relevé, ouvrir au clic. */
    const val EXTRA_ONGLET = "corsica.universita.aplomb.ONGLET"
    const val EXTRA_RELEVE_ID = "corsica.universita.aplomb.RELEVE_ID"
    /** D'où vient l'ouverture (analytics) : notification:<type>, tuile, raccourci. */
    const val EXTRA_SOURCE = "corsica.universita.aplomb.SOURCE"

    private const val TAG = "Aplomb"

    /** Appelé dans Application.onCreate : le canal doit exister avant la première notification, même d'un push app fermée. */
    fun creerCanaux(ctx: Context) {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CANAL_SYNC, ctx.getString(R.string.canal_sync), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = ctx.getString(R.string.canal_sync_desc) },
                NotificationChannel(CANAL_MESSAGES, ctx.getString(R.string.canal_messages), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = ctx.getString(R.string.canal_messages_desc) },
                NotificationChannel(CANAL_SURVEILLANCE, ctx.getString(R.string.canal_surveillance), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = ctx.getString(R.string.canal_surveillance_desc) },
                NotificationChannel(CANAL_ALERTE, ctx.getString(R.string.canal_alerte), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = ctx.getString(R.string.canal_alerte_desc)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 400, 200, 400)
                    },
            ),
        )
    }

    /**
     * Le clic : un PendingIntent, c'est-à-dire un Intent confié à l'OS, qu'il lancera à la place de l'app
     * même si elle a été tuée entre-temps. requestCode distinct par destination : sinon l'OS réutiliserait le même.
     */
    fun ouvrir(ctx: Context, onglet: String? = null, releveId: String? = null, type: String = "autre"): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            onglet?.let { putExtra(EXTRA_ONGLET, it) }
            releveId?.let { putExtra(EXTRA_RELEVE_ID, it) }
            putExtra(EXTRA_SOURCE, "notification:$type")
        }
        return PendingIntent.getActivity(
            ctx, (releveId ?: onglet ?: "").hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun permises(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** notify(), si c'est permis. Sans permission (Android 13+) ou coupée dans les réglages : rien, et on le dit dans Logcat. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() vérifié juste avant
    fun poster(ctx: Context, id: Int, notification: Notification) {
        if (!permises(ctx)) {
            Log.w(TAG, "Notification $id non affichée : notifications désactivées pour Aplomb")
            return
        }
        NotificationManagerCompat.from(ctx).notify(id, notification)
    }

    fun base(ctx: Context, canal: String, titre: String, texte: String) = NotificationCompat.Builder(ctx, canal)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ctx.getColor(R.color.accent))
        .setContentTitle(titre)
        .setContentText(texte)
        .setStyle(NotificationCompat.BigTextStyle().bigText(texte))

    /** L'équivalent du push « Relevés synchronisés » de la PWA, posté ici par le SyncWorker lui-même. */
    fun relevesSynchronises(ctx: Context, nombre: Int) {
        val texte = ctx.resources.getQuantityString(R.plurals.notif_sync_texte, nombre, nombre)
        poster(
            ctx, ID_SYNC,
            base(ctx, CANAL_SYNC, ctx.getString(R.string.notif_sync_titre), texte)
                .setContentIntent(ouvrir(ctx, onglet = "releves", type = "sync"))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Un push reçu (message data) : l'app construit la notification, avec la destination du clic. */
    fun message(ctx: Context, titre: String, texte: String, onglet: String?, releveId: String?) {
        poster(
            ctx, (releveId ?: texte).hashCode(),
            base(ctx, CANAL_MESSAGES, titre, texte)
                .setContentIntent(ouvrir(ctx, onglet = onglet ?: "releves", releveId = releveId, type = "message"))
                .setAutoCancel(true)
                .build(),
        )
    }
}
