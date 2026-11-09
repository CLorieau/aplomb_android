package corsica.universita.aplomb.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.aplomb.AplombApp
import corsica.universita.aplomb.capteurs.Etalonnage
import corsica.universita.aplomb.capteurs.LISSAGE
import corsica.universita.aplomb.capteurs.LectureNiveau
import corsica.universita.aplomb.capteurs.Vec3
import corsica.universita.aplomb.capteurs.ecartCap
import corsica.universita.aplomb.capteurs.etalonner
import corsica.universita.aplomb.capteurs.lireNiveau
import corsica.universita.aplomb.capteurs.lisser
import corsica.universita.aplomb.capteurs.normaliserCap
import corsica.universita.aplomb.position.Fix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** La boussole lissée : le cap affiché, et l'angle cumulé de la rose (pour qu'elle ne fasse jamais un tour complet). */
data class EtatBoussole(val capMagnetique: Double, val rotationRose: Double, val precision: Int)

/**
 * Les mesures en direct. Les capteurs ne tournent que tant qu'un écran les observe :
 * WhileSubscribed(5 s) + collectAsStateWithLifecycle → écran caché, capteur désenregistré, batterie épargnée.
 */
class MesuresViewModel(private val app: AplombApp) : ViewModel() {
    private val capteurs = app.capteurs
    val niveauDisponible = capteurs.niveauDisponible
    val boussoleDisponible = capteurs.boussoleDisponible

    private val etalonnage: StateFlow<Etalonnage> = app.reglages.etalonnage
        .stateIn(viewModelScope, SharingStarted.Eagerly, Etalonnage())

    val niveau: StateFlow<LectureNiveau?> = capteurs.haut()
        .scan(null as Vec3?) { ancien, nouveau -> lisser(ancien, nouveau) }
        .filterNotNull()
        .combine(etalonnage) { haut, e -> lireNiveau(haut, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val boussole: StateFlow<EtatBoussole?> = capteurs.cap()
        .scan(null as EtatBoussole?) { precedent, brut ->
            if (precedent == null) {
                EtatBoussole(brut.degres, -brut.degres, brut.precision)
            } else {
                val ecart = ecartCap(brut.degres, precedent.capMagnetique) * LISSAGE
                EtatBoussole(normaliserCap(precedent.capMagnetique + ecart), precedent.rotationRose - ecart, brut.precision)
            }
        }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // La position du moment n'est pas stockée : « recalculée à chaque fois », sinon une seconde source de vérité.
    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix.asStateFlow()
    private val _localisation = MutableStateFlow(false)
    val localisationEnCours: StateFlow<Boolean> = _localisation.asStateFlow()

    val seuilSurveillance: StateFlow<Float> = app.reglages.seuilSurveillance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    fun etalonner(): Boolean {
        val lecture = niveau.value ?: return false
        viewModelScope.launch { app.reglages.sauverEtalonnage(etalonner(lecture, etalonnage.value)) }
        return true
    }

    fun annulerEtalonnage() = viewModelScope.launch { app.reglages.sauverEtalonnage(Etalonnage()) }

    /** Demande une position (permission déjà accordée) ; false si l'OS n'en a donné aucune. */
    suspend fun localiser(): Boolean {
        _localisation.value = true
        val resultat = try {
            app.position.actuelle()
        } finally {
            _localisation.value = false
        }
        if (resultat != null) _fix.value = resultat
        return resultat != null
    }

    fun revenirAuNordMagnetique() {
        _fix.value = null
    }

    fun choisirSeuil(seuil: Float) = viewModelScope.launch { app.reglages.sauverSeuil(seuil) }

    suspend fun seuilActuel() = app.reglages.seuilSurveillance.first()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MesuresViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AplombApp)
            }
        }
    }
}
