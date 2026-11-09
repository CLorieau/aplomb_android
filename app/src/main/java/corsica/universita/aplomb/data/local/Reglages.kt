package corsica.universita.aplomb.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import corsica.universita.aplomb.capteurs.Etalonnage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.reglagesStore by preferencesDataStore(name = "reglages")

/**
 * Les petites valeurs isolées : l'équivalent du store `meta` de la PWA (identifiant, étalonnage)
 * et de son localStorage. Lire = un Flow ; écrire = suspend, jamais sur le main thread.
 */
class Reglages(context: Context) {
    private val store = context.applicationContext.reglagesStore

    /** Identifiant aléatoire de l'appareil (en-tête X-Device-Id) : rattache les relevés sans compte. */
    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        val nouveau = UUID.randomUUID().toString()
        store.edit { if (it[DEVICE_ID] == null) it[DEVICE_ID] = nouveau }
        return store.data.first()[DEVICE_ID]!!
    }

    val etalonnage: Flow<Etalonnage> = store.data.map {
        Etalonnage(
            platX = it[ETAL_X]?.toDouble() ?: 0.0,
            platY = it[ETAL_Y]?.toDouble() ?: 0.0,
            chant = it[ETAL_CHANT]?.toDouble() ?: 0.0,
        )
    }

    suspend fun sauverEtalonnage(e: Etalonnage) {
        store.edit {
            it[ETAL_X] = e.platX.toFloat()
            it[ETAL_Y] = e.platY.toFloat()
            it[ETAL_CHANT] = e.chant.toFloat()
        }
    }

    val fcmToken: Flow<String?> = store.data.map { it[FCM_TOKEN] }

    suspend fun sauverFcmToken(token: String) {
        store.edit { it[FCM_TOKEN] = token }
    }

    /** Seuil de la surveillance d'aplomb, en degrés. */
    val seuilSurveillance: Flow<Float> = store.data.map { it[SEUIL] ?: 1f }

    suspend fun sauverSeuil(seuil: Float) {
        store.edit { it[SEUIL] = seuil }
    }

    /**
     * Les permissions déjà demandées. L'OS ne distingue pas « jamais demandée » de « refusée définitivement »
     * (mêmes réponses) : seul ce drapeau, gardé par l'app, les sépare (Jour 5, chapitre 6).
     */
    val permissionsDemandees: Flow<Set<String>> = store.data.map { it[DEMANDEES] ?: emptySet() }

    suspend fun marquerDemandee(permissions: List<String>) {
        store.edit { it[DEMANDEES] = (it[DEMANDEES] ?: emptySet()) + permissions }
    }

    suspend fun oublierDemande(permissions: List<String>) {
        store.edit { it[DEMANDEES] = (it[DEMANDEES] ?: emptySet()) - permissions.toSet() }
    }

    /**
     * Consentement à la mesure d'audience : null = pas encore demandé, true = oui, false = non.
     * Rien n'est collecté avant le « oui » (Annexe 4, le consentement).
     */
    val consentementMesure: Flow<Boolean?> = store.data.map { it[CONSENTEMENT] }

    suspend fun sauverConsentement(oui: Boolean) {
        store.edit { it[CONSENTEMENT] = oui }
    }

    /** Le premier relevé a-t-il déjà été enregistré ? (l'activation ne se compte qu'une fois) */
    suspend fun premiereFois(cle: String): Boolean {
        var premiere = false
        store.edit {
            val fait = it[DEJA_FAIT] ?: emptySet()
            premiere = cle !in fait
            if (premiere) it[DEJA_FAIT] = fait + cle
        }
        return premiere
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("deviceId")
        val ETAL_X = floatPreferencesKey("etalonnagePlatX")
        val ETAL_Y = floatPreferencesKey("etalonnagePlatY")
        val ETAL_CHANT = floatPreferencesKey("etalonnageChant")
        val FCM_TOKEN = stringPreferencesKey("fcmToken")
        val SEUIL = floatPreferencesKey("seuilSurveillance")
        val DEMANDEES = stringSetPreferencesKey("permissionsDemandees")
        val CONSENTEMENT = booleanPreferencesKey("consentementMesure")
        val DEJA_FAIT = stringSetPreferencesKey("dejaFait")
    }
}
