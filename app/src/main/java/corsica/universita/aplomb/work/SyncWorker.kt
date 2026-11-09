package corsica.universita.aplomb.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import corsica.universita.aplomb.app
import corsica.universita.aplomb.notif.Notifications
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Vide l'outbox. Créé par WorkManager, jamais par l'app : l'OS le lance quand le réseau est là,
 * app ouverte, fermée ou tuée, et même après un redémarrage. C'est ce que Background Sync faisait
 * dans la PWA… sur Chromium seulement, et sans garantie.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.app
        Log.d(TAG, "SyncWorker démarré (tentative ${runAttemptCount + 1})")
        return try {
            val resultat = app.repository.envoyerOutbox()
            Log.d(TAG, "SyncWorker : ${resultat.traites} ordre(s) traité(s), ${resultat.differesArrives} différé(s) arrivé(s)")
            // Comme le push de la PWA : seulement pour des relevés pris hors ligne, et pas si l'utilisateur regarde déjà.
            if (resultat.differesArrives > 0 && !app.auPremierPlan) {
                Notifications.relevesSynchronises(applicationContext, resultat.differesArrives)
            }
            // Analytics : combien de temps un relevé attend-il le réseau ? (l'attente du plus ancien, en secondes)
            if (resultat.envoyes > 0) {
                app.mesure.evenement("sync_done", "/releves", "count" to resultat.envoyes, "delay_s" to resultat.attenteMaxS)
            }
            Result.success(workDataOf("traites" to resultat.traites))
        } catch (e: IOException) {
            Log.w(TAG, "SyncWorker : réseau indisponible, nouvel essai plus tard — ${e.message}")
            Result.retry() // 30 s, 1 min, 2 min… (backoff exponentiel)
        } catch (e: HttpException) {
            if (e.code() >= 500) {
                Log.w(TAG, "SyncWorker : serveur indisponible (${e.code()}), nouvel essai plus tard")
                Result.retry()
            } else {
                // Une autre erreur 4xx (mauvaise URL…) : réessayer à l'identique ne changerait rien.
                // L'ordre reste dans l'outbox ; il repartira au prochain démarrage de l'app.
                Log.e(TAG, "SyncWorker : erreur ${e.code()}, abandon", e)
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "Aplomb"
        private const val NOM = "sync"

        /**
         * Un seul travail « sync » : dix relevés pris hors ligne = une seule demande en file.
         * - immediat = false (nouveau relevé) : KEEP garde celle qui attend déjà ;
         * - immediat = true (bouton « Synchroniser », retour du réseau) : REPLACE annule un éventuel délai de réessai.
         * Une seule condition, « réseau connecté » : chaque contrainte ajoutée « par prudence » retarderait l'envoi.
         */
        fun planifier(ctx: Context, immediat: Boolean = false): UUID {
            val demande = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NOM, if (immediat) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, demande,
            )
            return demande.id // celui du travail en file seulement avec REPLACE (KEEP peut garder l'ancien)
        }
    }
}
