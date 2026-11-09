package corsica.universita.aplomb.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 : la boussole peut corriger le nord magnétique avec la position (déclinaison).
 * La position du relevé est gardée avec lui. Les relevés de la v1 sont conservés : colonnes NULL / 0.
 *
 * Sans cette migration, la v2 installée par-dessus la v1 plante au lancement :
 * « A migration from 1 to 2 was required but not found ».
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE releves ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE releves ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE releves ADD COLUMN precisionM REAL")
        db.execSQL("ALTER TABLE releves ADD COLUMN nordGeographique INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v2 → v3 : la file des événements d'analytics (Umami). Une table ajoutée, aucune donnée touchée.
 * Le CREATE TABLE est recopié de app/schemas/…/3.json : Room vérifie le résultat contre ce schéma.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(SQL_EVENEMENTS)
    }
}

private const val SQL_EVENEMENTS =
    "CREATE TABLE IF NOT EXISTS `evenements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nom` TEXT, " +
        "`ecran` TEXT NOT NULL, `titre` TEXT NOT NULL, `donnees` TEXT, `horodatage` INTEGER NOT NULL)"
