package corsica.universita.aplomb.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import corsica.universita.aplomb.data.Etat
import corsica.universita.aplomb.data.ModeNiveau
import corsica.universita.aplomb.data.Outil

/** Une ligne de la table `releves` (l'object store `releves` de la PWA). */
@Entity(tableName = "releves")
data class ReleveEntity(
    @PrimaryKey val id: String,
    val outil: Outil,
    val valeur: Double,
    val mode: ModeNiveau?,
    val x: Double?,
    val y: Double?,
    val note: String,
    val createdAt: Long,
    val etat: Etat,
    // v2 : la position du relevé de boussole (nord géographique). Restent sur l'appareil, jamais envoyées.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val precisionM: Float? = null,
    // Même valeur par défaut ici et dans le ALTER TABLE de MIGRATION_1_2, sinon Room refuse la base migrée.
    @ColumnInfo(defaultValue = "0") val nordGeographique: Boolean = false,
)
