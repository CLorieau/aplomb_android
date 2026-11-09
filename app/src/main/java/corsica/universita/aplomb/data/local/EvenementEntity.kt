package corsica.universita.aplomb.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * v3 : la file des événements d'analytics, en attente d'envoi à Umami.
 * Umami n'a pas de SDK Android (Annexe 4) : pas de file sur le disque fournie, on l'écrit — c'est l'outbox du Jour 5.
 * `nom` null = une page vue (un écran) ; sinon un événement nommé, avec ses `donnees` en JSON.
 */
@Entity(tableName = "evenements")
data class EvenementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String?,
    val ecran: String,      // l'« URL » Umami : /niveau, /boussole, /releves, /guide, /releve
    val titre: String,
    val donnees: String?,   // JSON, jamais de donnée personnelle (ni note, ni position)
    val horodatage: Long,   // ms : envoyé en secondes, Umami date l'événement au moment où il a eu lieu
)

@Dao
interface EvenementDao {
    @Insert
    suspend fun ajouter(e: EvenementEntity)

    @Query("SELECT * FROM evenements ORDER BY id LIMIT :n")
    suspend fun lot(n: Int): List<EvenementEntity>

    @Query("DELETE FROM evenements WHERE id = :id")
    suspend fun retirer(id: Long)

    @Query("SELECT COUNT(*) FROM evenements")
    suspend fun taille(): Int

    /** File bornée (comme maxQueueSize de PostHog) : au-delà, les plus anciens partent à la poubelle. */
    @Query("DELETE FROM evenements WHERE id NOT IN (SELECT id FROM evenements ORDER BY id DESC LIMIT :max)")
    suspend fun borner(max: Int)

    /** Refus de consentement : rien ne part, et ce qui attendait est effacé. */
    @Query("DELETE FROM evenements")
    suspend fun vider()
}
