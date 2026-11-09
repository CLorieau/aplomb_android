package corsica.universita.aplomb

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import corsica.universita.aplomb.capteurs.Capteurs
import corsica.universita.aplomb.data.RelevesRepository
import corsica.universita.aplomb.data.Reseau
import corsica.universita.aplomb.data.local.AplombDatabase
import corsica.universita.aplomb.data.local.Reglages
import corsica.universita.aplomb.data.remote.AplombApi
import corsica.universita.aplomb.mesure.Mesure
import corsica.universita.aplomb.mesure.MesureWorker
import corsica.universita.aplomb.mesure.UmamiApi
import corsica.universita.aplomb.notif.Notifications
import corsica.universita.aplomb.position.Position
import corsica.universita.aplomb.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Un objet Application par process, créé avant tout écran, worker ou service : il construit, une fois,
 * les objets partagés (pas de Hilt, un conteneur à la main comme CarnetContainer dans le cours).
 * Le process peut démarrer sans aucune Activity : pour le SyncWorker, un push FCM ou la tuile.
 */
class AplombApp : Application() {
    /** Scope du process : vit autant que lui. Pour ce qui n'appartient à aucun écran. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AplombDatabase.build(this) }
    val reglages by lazy { Reglages(this) }
    val reseau by lazy { Reseau(this, scope) }
    val api by lazy { AplombApi.create(this) }
    val repository by lazy { RelevesRepository(this, database.releveDao(), api, reglages, reseau) }
    val capteurs by lazy { Capteurs(this) }
    val position by lazy { Position(this) }
    val umami by lazy { UmamiApi.create() }
    val mesure by lazy { Mesure(this, database.evenementDao(), reglages, scope) }

    /** Vrai tant qu'une Activity est visible : pas de notification « synchronisé » si l'utilisateur regarde déjà la liste. */
    @Volatile var auPremierPlan = false

    val fcmActif: Boolean get() = BuildConfig.FCM_ACTIF && FirebaseApp.getApps(this).isNotEmpty()

    override fun onCreate() {
        super.onCreate()
        // Les canaux existent avant toute notification, même si le process démarre pour un push.
        Notifications.creerCanaux(this)
        // Au démarrage, ce qui attend dans l'outbox repart (le démarrage de l'app, comme dans la PWA).
        SyncWorker.planifier(this)
        // Les événements d'analytics restés en file (pris hors ligne) repartent aussi.
        if (mesure.configuree) MesureWorker.planifier(this)
        // Retour du réseau : on relance tout de suite un envoi qui serait en attente de réessai (backoff).
        scope.launch {
            reseau.enLigne.drop(1).filter { it }.collect { SyncWorker.planifier(this@AplombApp, immediat = true) }
        }
        lireJetonFcm()
    }

    private fun lireJetonFcm() {
        if (!fcmActif) {
            Log.i(TAG, "FCM désactivé : pas de google-services.json (voir README)")
            return
        }
        // FCM 25 : register() remplace getToken() ; le jeton arrive dans AplombMessagingService.onRegistered.
        FirebaseMessaging.getInstance().register()
            .addOnFailureListener { Log.w(TAG, "Inscription FCM impossible : ${it.message}") }
    }

    companion object {
        const val TAG = "Aplomb"
    }
}

val Context.app: AplombApp get() = applicationContext as AplombApp
