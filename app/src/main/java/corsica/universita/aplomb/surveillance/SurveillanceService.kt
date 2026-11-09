package corsica.universita.aplomb.surveillance

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import corsica.universita.aplomb.R
import corsica.universita.aplomb.app
import corsica.universita.aplomb.capteurs.Vec3
import corsica.universita.aplomb.capteurs.arrondi1
import corsica.universita.aplomb.capteurs.ecartAngulaire
import corsica.universita.aplomb.capteurs.fmt
import corsica.universita.aplomb.capteurs.lisser
import corsica.universita.aplomb.data.Etat
import corsica.universita.aplomb.data.ModeNiveau
import corsica.universita.aplomb.data.Outil
import corsica.universita.aplomb.data.Releve
import corsica.universita.aplomb.notif.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** L'état de la surveillance, lu par l'écran Niveau et par la tuile de réglages rapides. */
sealed interface EtatSurveillance {
    data object Inactive : EtatSurveillance
    data class Active(val depuis: Long, val seuil: Float, val ecart: Double?, val alertes: Int) : EtatSurveillance
}

/**
 * LA fonctionnalité impossible en PWA : surveiller un aplomb écran éteint.
 *
 * On pose le téléphone sur ce qu'on veut surveiller (un cadre de porte qui sèche, une étagère qu'on charge,
 * un meuble qu'on cale), on éteint l'écran, et Aplomb prévient dès que l'inclinaison dépasse le seuil.
 * Une page web perd ses capteurs dès qu'elle est cachée ; ici, un foreground service :
 * - l'OS le traite comme une app visible (pas de Doze, tué en dernier) ;
 * - en échange, une notification permanente que l'utilisateur ne peut pas masquer : le prix à payer ;
 * - un type déclaré (specialUse, avec sa justification dans le manifest), vérifié par Google Play ;
 * - un wake lock partiel borné à 2 h : le processeur reste éveillé pour lire l'accéléromètre, pas l'écran.
 */
class SurveillanceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mesure: Job? = null
    private var verrou: PowerManager.WakeLock? = null
    private var finParDelai = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ARRETER) {
            arreter()
            return START_NOT_STICKY
        }
        val seuil = intent?.getFloatExtra(EXTRA_SEUIL, 1f) ?: 1f
        val depuis = System.currentTimeMillis()
        _etat.value = EtatSurveillance.Active(depuis, seuil, null, 0)
        // Analytics — la fonctionnalité native : sert-elle ? avec quel seuil ?
        app.mesure.evenement("surveillance_started", "/niveau", "seuil" to seuil)

        // startForeground dans les 5 s qui suivent startForegroundService, sinon l'OS tue le service.
        ServiceCompat.startForeground(
            this, Notifications.ID_SURVEILLANCE, notificationEnCours(seuil, null, depuis),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        verrou = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aplomb:surveillance")
            .apply { acquire(DUREE_MAX_MS) }
        rafraichirTuile()

        mesure?.cancel()
        mesure = scope.launch { surveiller(seuil, depuis) }
        return START_NOT_STICKY // tué par l'OS : on ne relance pas une surveillance sans référence
    }

    private suspend fun surveiller(seuil: Float, depuis: Long) {
        // Quelques secondes pour reposer le téléphone, puis la position de référence = la moyenne de 10 mesures.
        delay(DELAI_POSE_MS)
        var reference: Vec3? = null
        var echantillons = 0
        var lisse: Vec3? = null
        var horsSeuilDepuis = 0L
        var enAlerte = false
        var alertes = 0
        var derniereMaj = 0L

        val minuterie = scope.launch {
            delay(DUREE_MAX_MS)
            Log.i(TAG, "Surveillance arrêtée après 2 h (économie de batterie)")
            finParDelai = true
            arreter()
        }

        app.capteurs.haut(SensorManager.SENSOR_DELAY_NORMAL).collect { haut ->
            val courant = lisser(lisse, haut).also { lisse = it }
            if (reference == null) {
                if (++echantillons >= 10) reference = courant
                return@collect
            }
            val ecart = ecartAngulaire(reference, courant)
            val maintenant = System.currentTimeMillis()

            if (ecart > seuil) {
                if (horsSeuilDepuis == 0L) horsSeuilDepuis = maintenant
                // Au-delà du seuil pendant plus d'une seconde (un choc passager ne déclenche rien) : alerte, une fois.
                if (!enAlerte && maintenant - horsSeuilDepuis > 1_000) {
                    enAlerte = true
                    alertes++
                    alerter(ecart, seuil, reference)
                    // … et réussit-elle : une alerte déclenchée (l'écart arrondi au degré, rien de plus).
                    app.mesure.evenement("surveillance_alerted", "/niveau", "seuil" to seuil, "ecart" to Math.round(ecart))
                }
            } else {
                horsSeuilDepuis = 0L
                if (ecart < seuil * 0.8) enAlerte = false // revenu en place : prêt pour une nouvelle alerte
            }

            _etat.value = EtatSurveillance.Active(depuis, seuil, ecart, alertes)
            if (maintenant - derniereMaj > 5_000) { // la notification permanente, sans la reposter 5 fois par seconde
                derniereMaj = maintenant
                Notifications.poster(this, Notifications.ID_SURVEILLANCE, notificationEnCours(seuil, ecart, depuis))
            }
        }
        minuterie.cancel()
    }

    /** L'alerte : une notification haute priorité, et un relevé écrit dans Room → l'outbox → le serveur. */
    private fun alerter(ecart: Double, seuil: Float, reference: Vec3) {
        Log.w(TAG, "Aplomb : écart de ${fmt(ecart)}° (seuil ${fmt(seuil.toDouble())}°)")
        val texte = getString(R.string.alerte_texte, fmt(ecart), fmt(seuil.toDouble()))
        Notifications.poster(
            this, Notifications.ID_ALERTE,
            Notifications.base(this, Notifications.CANAL_ALERTE, getString(R.string.alerte_titre), texte)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(Notifications.ouvrir(this, onglet = "releves", type = "alerte"))
                .setAutoCancel(true)
                .build(),
        )
        // Le service n'a pas d'écran : il passe par le repository, comme le worker et le service FCM.
        scope.launch {
            app.repository.enregistrer(
                Releve(
                    id = "", outil = Outil.NIVEAU, valeur = arrondi1(ecart),
                    mode = if (abs(reference.z) > 0.7) ModeNiveau.PLAT else ModeNiveau.CHANT,
                    x = null, y = null, note = getString(R.string.alerte_note, fmt(seuil.toDouble())),
                    createdAt = 0, etat = Etat.ATTENTE,
                ),
            )
        }
    }

    private fun notificationEnCours(seuil: Float, ecart: Double?, depuis: Long) =
        Notifications.base(
            this, Notifications.CANAL_SURVEILLANCE, getString(R.string.surveillance_titre),
            if (ecart == null) getString(R.string.surveillance_reference, fmt(seuil.toDouble()))
            else getString(R.string.surveillance_texte, fmt(ecart), fmt(seuil.toDouble())),
        )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(depuis)
            .setUsesChronometer(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Notifications.ouvrir(this, onglet = "niveau", type = "surveillance"))
            .addAction(0, getString(R.string.surveillance_arreter), arretPendingIntent())
            .build()

    private fun arretPendingIntent() = android.app.PendingIntent.getService(
        this, 0, Intent(this, SurveillanceService::class.java).setAction(ACTION_ARRETER),
        android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    private fun arreter() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Analytics : combien de temps a-t-elle tourné, avec combien d'alertes, et qui l'a arrêtée.
        (etat.value as? EtatSurveillance.Active)?.let { e ->
            app.mesure.evenement(
                "surveillance_stopped", "/niveau",
                "duree_min" to (System.currentTimeMillis() - e.depuis) / 60_000,
                "alertes" to e.alertes,
                "fin" to if (finParDelai) "delai_2h" else "utilisateur",
            )
        }
        scope.cancel() // le collect du capteur s'arrête → unregisterListener
        verrou?.takeIf { it.isHeld }?.release()
        _etat.value = EtatSurveillance.Inactive
        rafraichirTuile()
        super.onDestroy()
    }

    private fun rafraichirTuile() =
        TileService.requestListeningState(this, ComponentName(this, SurveillanceTileService::class.java))

    companion object {
        private const val TAG = "Aplomb"
        private const val ACTION_ARRETER = "corsica.universita.aplomb.ARRETER_SURVEILLANCE"
        private const val EXTRA_SEUIL = "seuil"
        private const val DELAI_POSE_MS = 3_000L
        private const val DUREE_MAX_MS = 2 * 60 * 60 * 1000L

        private val _etat = MutableStateFlow<EtatSurveillance>(EtatSurveillance.Inactive)
        val etat: StateFlow<EtatSurveillance> = _etat.asStateFlow()

        /** Démarré depuis l'écran (au premier plan) : c'est la seule origine autorisée pour un foreground service. */
        fun demarrer(ctx: Context, seuil: Float) = ContextCompat.startForegroundService(
            ctx, Intent(ctx, SurveillanceService::class.java).putExtra(EXTRA_SEUIL, seuil),
        )

        fun arreter(ctx: Context) {
            ctx.stopService(Intent(ctx, SurveillanceService::class.java))
        }
    }
}
