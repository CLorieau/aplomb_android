package corsica.universita.aplomb.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * L'outbox : la file des envois en attente (l'object store `outbox` de la PWA).
 * `seq` auto-incrémenté = l'ordre des actions de l'utilisateur, respecté à l'envoi.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val op: String,          // OP_PUT ou OP_DELETE
    val releveId: String,
    /** Un premier envoi a échoué (ou le relevé a été pris hors ligne) : son arrivée sera notifiée. */
    @ColumnInfo(defaultValue = "0") val differe: Boolean = false,
) {
    companion object {
        const val OP_PUT = "put"
        const val OP_DELETE = "delete"
    }
}
