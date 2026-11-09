package corsica.universita.aplomb.data

import android.content.Context
import android.util.Log
import corsica.universita.aplomb.data.local.OutboxEntity
import corsica.universita.aplomb.data.local.Reglages
import corsica.universita.aplomb.data.local.ReleveDao
import corsica.universita.aplomb.data.local.ReleveEntity
import corsica.universita.aplomb.data.remote.AplombApi
import corsica.universita.aplomb.data.remote.ReleveDto
import corsica.universita.aplomb.work.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

/** Un passage de l'outbox : ordres traités, et relevés « différés » (pris hors ligne) arrivés au serveur. */
data class ResultatSync(val traites: Int, val differesArrives: Int, val envoyes: Int = 0, val attenteMaxS: Long = 0)

/**
 * La seule porte d'entrée vers les données. Elle sert l'écran, le SyncWorker, le service de surveillance
 * et le service FCM. Room est la source de vérité : l'écran ne lit que Room, le réseau ne parle qu'à Room.
 */
class RelevesRepository(
    private val context: Context,
    private val dao: ReleveDao,
    private val api: AplombApi,
    private val reglages: Reglages,
    private val reseau: Reseau,
) {
    val releves: Flow<List<Releve>> = dao.tous().map { liste -> liste.map { it.versModele() } }

    fun releve(id: String): Flow<Releve?> = dao.observer(id).map { it?.versModele() }

    /** Local d'abord : le relevé existe dès maintenant, avec ou sans réseau ; l'envoi est confié à WorkManager. */
    suspend fun enregistrer(r: Releve): Releve {
        val releve = r.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis(), etat = Etat.ATTENTE)
        // Pris hors ligne = « différé » dès le départ : son arrivée au serveur sera notifiée.
        dao.ajouter(releve.versEntite(), differe = !reseau.enLigne.value)
        SyncWorker.planifier(context)
        return releve
    }

    suspend fun supprimer(id: String) {
        dao.supprimer(id, differe = !reseau.enLigne.value)
        SyncWorker.planifier(context)
    }

    /**
     * Appelé par SyncWorker : parcourt l'outbox dans l'ordre (même algorithme que sync.js de la PWA).
     * - 2xx : l'ordre est retiré, le relevé passe « Envoyé » ;
     * - 400 / 413 : refus définitif, l'ordre est retiré, le relevé passe « Refusé » (la file n'est pas bloquée) ;
     * - réseau coupé ou 5xx : l'ordre est marqué différé et l'exception remonte au worker (retry).
     * On relit l'outbox jusqu'à ce qu'elle soit vide : un relevé ajouté pendant l'envoi part dans le même travail.
     */
    suspend fun envoyerOutbox(): ResultatSync {
        val appareil = reglages.deviceId()
        var traites = 0
        var differesArrives = 0
        var envoyes = 0
        var attenteMaxS = 0L
        while (true) {
            val file = dao.outbox()
            if (file.isEmpty()) break
            for (ordre in file) {
                val suppression = ordre.op == OutboxEntity.OP_DELETE
                val releve = if (suppression) null else dao.lire(ordre.releveId)
                if (!suppression && releve == null) {
                    dao.retirerOrdre(ordre.seq) // supprimé entre-temps : la suppression suit dans l'outbox
                    continue
                }
                val reponse = try {
                    if (releve == null) {
                        api.supprimer(ordre.releveId, appareil)
                    } else {
                        api.envoyer(ordre.releveId, appareil, if (ordre.differe) "1" else null, releve.versDto())
                    }
                } catch (e: IOException) {
                    if (!ordre.differe) dao.marquerDiffere(ordre.seq)
                    throw e
                }
                when {
                    reponse.isSuccessful -> {
                        dao.acquitter(ordre.seq, ordre.releveId, if (suppression) null else Etat.ENVOYE)
                        if (!suppression && ordre.differe) differesArrives++
                        if (releve != null) {
                            envoyes++
                            attenteMaxS = maxOf(attenteMaxS, (System.currentTimeMillis() - releve.createdAt) / 1000)
                        }
                    }
                    reponse.code() in REFUS_DEFINITIFS -> {
                        Log.w(TAG, "Relevé ${ordre.releveId} refusé par le serveur (${reponse.code()})")
                        dao.acquitter(ordre.seq, ordre.releveId, if (suppression) null else Etat.REFUSE)
                    }
                    else -> {
                        if (!ordre.differe) dao.marquerDiffere(ordre.seq)
                        throw HttpException(reponse)
                    }
                }
                traites++
            }
        }
        return ResultatSync(traites, differesArrives, envoyes, attenteMaxS)
    }

    private companion object {
        const val TAG = "Aplomb"
        val REFUS_DEFINITIFS = setOf(400, 413)
    }
}

private fun ReleveEntity.versModele() = Releve(
    id = id, outil = outil, valeur = valeur, mode = mode, x = x, y = y, note = note, createdAt = createdAt, etat = etat,
    latitude = latitude, longitude = longitude, precisionM = precisionM, nordGeographique = nordGeographique,
)

private fun Releve.versEntite() = ReleveEntity(
    id = id, outil = outil, valeur = valeur, mode = mode, x = x, y = y, note = note, createdAt = createdAt, etat = etat,
    latitude = latitude, longitude = longitude, precisionM = precisionM, nordGeographique = nordGeographique,
)

private fun ReleveEntity.versDto() = ReleveDto(
    outil = outil.cle, valeur = valeur, mode = mode?.cle, x = x, y = y, note = note.take(140), createdAt = createdAt,
)
