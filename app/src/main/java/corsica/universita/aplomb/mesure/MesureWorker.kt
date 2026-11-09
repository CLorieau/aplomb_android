package corsica.universita.aplomb.mesure

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
import corsica.universita.aplomb.BuildConfig
import corsica.universita.aplomb.app
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Envoie la file d'événements à Umami, par lots. La même logique que l'outbox (SyncWorker) :
 * écrire d'abord, envoyer plus tard, quand le réseau est là — app fermée comprise.
 * Par lots : on attend un peu (30 s en release, comme PostHog) pour réveiller la radio une fois, pas à chaque clic.
 */
class MesureWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val dao = app.database.evenementDao()
        if (app.mesure.consentement.value == false) {
            dao.vider()
            return Result.success()
        }
        var envoyes = 0
        return try {
            while (true) {
                val lot = dao.lot(Mesure.TAILLE_LOT)
                if (lot.isEmpty()) break
                for (e in lot) {
                    val reponse = app.umami.envoyer(UmamiApi.userAgent, UmamiApi.corps(applicationContext, e))
                    when {
                        reponse.isSuccessful -> {
                            val corps = reponse.body()?.string().orEmpty()
                            // Umami répond 200 {"beep":"boop"} à ce qu'il prend pour un robot : l'événement n'est pas compté.
                            if ("beep" in corps) Log.w(TAG, "Umami a classé l'envoi comme robot : vérifier le User-Agent")
                            dao.retirer(e.id)
                            envoyes++
                        }
                        reponse.code() >= 500 -> throw IOException("Umami indisponible (${reponse.code()})")
                        else -> {
                            // 4xx : l'événement est invalide (ou l'identifiant de site faux) ; réessayer n'y changerait rien.
                            Log.e(TAG, "Umami refuse l'événement ${e.nom ?: e.ecran} (${reponse.code()}) : ${reponse.errorBody()?.string()}")
                            dao.retirer(e.id)
                        }
                    }
                }
            }
            Log.d(TAG, "MesureWorker : $envoyes événement(s) envoyé(s) à Umami")
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "MesureWorker : réseau ou Umami indisponible, nouvel essai plus tard — ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "Aplomb"
        private const val NOM = "mesure"

        /** Un seul travail « mesure » ; `plein` (20 événements en file) : on n'attend plus, on envoie. */
        fun planifier(ctx: Context, plein: Boolean = false) {
            val delai = when {
                plein -> 0L
                BuildConfig.DEBUG -> 5L // en debug, presque en direct : l'équivalent du DebugView de Firebase
                else -> 30L
            }
            val demande = OneTimeWorkRequestBuilder<MesureWorker>()
                .setInitialDelay(delai, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NOM, if (plein) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, demande,
            )
        }
    }
}
