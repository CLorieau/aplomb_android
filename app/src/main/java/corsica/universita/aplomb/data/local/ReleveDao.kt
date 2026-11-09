package corsica.universita.aplomb.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import corsica.universita.aplomb.data.Etat
import kotlinx.coroutines.flow.Flow

/** Le SQL d'Aplomb, vérifié à la compilation. Lire = un Flow ; écrire = suspend, hors du main thread. */
@Dao
abstract class ReleveDao {

    @Query("SELECT * FROM releves ORDER BY createdAt DESC")
    abstract fun tous(): Flow<List<ReleveEntity>>

    @Query("SELECT * FROM releves WHERE id = :id")
    abstract fun observer(id: String): Flow<ReleveEntity?>

    @Query("SELECT * FROM releves WHERE id = :id")
    abstract suspend fun lire(id: String): ReleveEntity?

    @Query("SELECT * FROM outbox ORDER BY seq")
    abstract suspend fun outbox(): List<OutboxEntity>

    @Insert
    protected abstract suspend fun insererReleve(releve: ReleveEntity)

    @Query("DELETE FROM releves WHERE id = :id")
    protected abstract suspend fun supprimerReleve(id: String)

    @Insert
    protected abstract suspend fun insererOrdre(ordre: OutboxEntity)

    @Query("DELETE FROM outbox WHERE seq = :seq")
    abstract suspend fun retirerOrdre(seq: Long)

    @Query("UPDATE outbox SET differe = 1 WHERE seq = :seq")
    abstract suspend fun marquerDiffere(seq: Long)

    @Query("UPDATE releves SET etat = :etat WHERE id = :id")
    protected abstract suspend fun changerEtat(id: String, etat: Etat)

    /** Le relevé et son ordre d'envoi dans la même transaction : jamais l'un sans l'autre (comme db.js). */
    @Transaction
    open suspend fun ajouter(releve: ReleveEntity, differe: Boolean) {
        insererReleve(releve)
        insererOrdre(OutboxEntity(op = OutboxEntity.OP_PUT, releveId = releve.id, differe = differe))
    }

    @Transaction
    open suspend fun supprimer(id: String, differe: Boolean) {
        supprimerReleve(id)
        insererOrdre(OutboxEntity(op = OutboxEntity.OP_DELETE, releveId = id, differe = differe))
    }

    /** L'envoi a abouti (ou a été refusé pour de bon) : l'ordre quitte l'outbox, le badge change. */
    @Transaction
    open suspend fun acquitter(seq: Long, releveId: String, etat: Etat?) {
        retirerOrdre(seq)
        if (etat != null) changerEtat(releveId, etat)
    }
}
