package corsica.universita.aplomb.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * La base SQLite d'Aplomb : /data/data/corsica.universita.aplomb/databases/aplomb.db.
 * Elle remplace IndexedDB (`aplomb-db`) : mêmes tables `releves` et `outbox`.
 * `exportSchema` : le schéma de chaque version est écrit dans app/schemas/ (commité).
 */
@Database(entities = [ReleveEntity::class, OutboxEntity::class, EvenementEntity::class], version = 3, exportSchema = true)
abstract class AplombDatabase : RoomDatabase() {
    abstract fun releveDao(): ReleveDao
    abstract fun evenementDao(): EvenementDao

    companion object {
        fun build(context: Context): AplombDatabase =
            Room.databaseBuilder(context.applicationContext, AplombDatabase::class.java, "aplomb.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // jamais fallbackToDestructiveMigration : il effacerait les relevés
                .build()
    }
}
