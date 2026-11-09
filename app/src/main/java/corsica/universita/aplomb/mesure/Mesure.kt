package corsica.universita.aplomb.mesure

import android.content.Context
import android.util.Log
import corsica.universita.aplomb.BuildConfig
import corsica.universita.aplomb.data.local.EvenementDao
import corsica.universita.aplomb.data.local.EvenementEntity
import corsica.universita.aplomb.data.local.Reglages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * La mesure d'audience d'Aplomb, avec Umami (outil sans cookie du Jour 1).
 *
 * Le trajet d'un événement (Annexe 4) : l'app appelle evenement() et repart aussitôt → l'événement est écrit
 * dans Room (table `evenements`) → MesureWorker l'envoie par lots, quand le réseau est là → Umami agrège.
 * Hors ligne, il attend sur le disque. Rien n'est écrit ni envoyé avant le « oui » du consentement.
 *
 * Le plan de marquage (chaque événement répond à une question) est dans EXPLICATIONS.md.
 * Aucune donnée personnelle : ni texte de note, ni position, ni identifiant d'appareil.
 */
class Mesure(
    private val ctx: Context,
    private val dao: EvenementDao,
    private val reglages: Reglages,
    private val scope: CoroutineScope,
) {
    /** Configurée = un identifiant de site Umami dans local.properties. Sinon : ni bandeau, ni collecte. */
    val configuree: Boolean = BuildConfig.UMAMI_WEBSITE_ID.isNotBlank()

    val consentement: StateFlow<Boolean?> = reglages.consentementMesure
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun repondre(oui: Boolean) = scope.launch {
        reglages.sauverConsentement(oui)
        Log.i(TAG, "Mesure d'audience : ${if (oui) "acceptée" else "refusée"}")
        if (!oui) dao.vider()
    }

    /** L'écran vu. Avec Compose, une seule Activity : le suivi automatique ne verrait que « MainActivity ». */
    fun ecran(chemin: String, titre: String) = enregistrer(null, chemin, titre, emptyMap())

    /** Un événement nommé objet_verbe, au passé, en snake_case ; jamais renommé ensuite. */
    fun evenement(nom: String, ecran: String = "/", vararg donnees: Pair<String, Any>) =
        enregistrer(nom, ecran, nom, donnees.toMap())

    private fun enregistrer(nom: String?, ecran: String, titre: String, donnees: Map<String, Any>) = scope.launch {
        val trace = "${nom ?: "écran"} $ecran ${if (donnees.isEmpty()) "" else donnees}"
        if (!configuree) {
            Log.d(TAG, "Mesure (non configurée, ignorée) : $trace")
            return@launch
        }
        // La vraie valeur sur le disque, pas la StateFlow : au démarrage, elle n'est peut-être pas encore lue.
        if (reglages.consentementMesure.first() != true) {
            Log.d(TAG, "Mesure (sans consentement, ignorée) : $trace")
            return@launch
        }
        val json = if (donnees.isEmpty()) null else buildJsonObject {
            donnees.forEach { (k, v) ->
                put(
                    k,
                    when (v) {
                        is Number -> JsonPrimitive(v)
                        is Boolean -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    },
                )
            }
        }.toString()
        dao.ajouter(EvenementEntity(nom = nom, ecran = ecran, titre = titre, donnees = json, horodatage = System.currentTimeMillis()))
        dao.borner(MAX_FILE)
        Log.d(TAG, "Mesure (en file) : $trace")
        MesureWorker.planifier(ctx, plein = dao.taille() >= TAILLE_LOT)
    }

    companion object {
        private const val TAG = "Aplomb"
        const val TAILLE_LOT = 20
        private const val MAX_FILE = 1000
    }
}
