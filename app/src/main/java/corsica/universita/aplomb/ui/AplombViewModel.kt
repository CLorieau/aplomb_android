package corsica.universita.aplomb.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import corsica.universita.aplomb.AplombApp
import corsica.universita.aplomb.capteurs.LectureNiveau
import corsica.universita.aplomb.capteurs.arrondi1
import corsica.universita.aplomb.data.Etat
import corsica.universita.aplomb.data.ModeNiveau
import corsica.universita.aplomb.data.Outil
import corsica.universita.aplomb.data.Releve
import corsica.universita.aplomb.position.Fix
import corsica.universita.aplomb.work.SyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * L'état partagé des écrans d'Aplomb. Il survit à la rotation ; ce qui doit survivre à la mort du process
 * est soit sur le disque (les relevés, dans Room), soit dans le SavedStateHandle (l'onglet, les notes en cours).
 */
class AplombViewModel(
    private val savedState: SavedStateHandle,
    private val app: AplombApp,
) : ViewModel() {
    private val repository = app.repository

    val releves: StateFlow<List<Releve>> = repository.releves
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val enLigne: StateFlow<Boolean> = app.reseau.enLigne

    val fcmToken: StateFlow<String?> = app.reglages.fcmToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Le Bundle : l'onglet affiché et les notes en cours de saisie survivent à `am kill`.
    val onglet: StateFlow<String> = savedState.getStateFlow(KEY_ONGLET, ONGLETS.first())
    val noteNiveau: StateFlow<String> = savedState.getStateFlow(KEY_NOTE_NIVEAU, "")
    val noteBoussole: StateFlow<String> = savedState.getStateFlow(KEY_NOTE_BOUSSOLE, "")

    /** Le relevé à ouvrir (clic de notification, App Link) : consommé par la navigation. */
    val releveDemande: StateFlow<String?> = savedState.getStateFlow(KEY_RELEVE, null)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private var toastJob: Job? = null

    fun toast(texte: String) {
        _toast.value = texte
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(3_500)
            _toast.value = null
        }
    }

    fun choisirOnglet(nom: String) {
        if (nom in ONGLETS) savedState[KEY_ONGLET] = nom
    }

    fun demanderReleve(id: String) {
        savedState[KEY_ONGLET] = "releves"
        savedState[KEY_RELEVE] = id
    }

    fun releveOuvert() {
        savedState[KEY_RELEVE] = null
    }

    /**
     * TD A3 : un texte partagé depuis une autre app (ACTION_SEND text/plain) préremplit la note en cours.
     * Celle de la boussole si c'est l'onglet affiché, sinon celle du niveau (et l'onglet Niveau s'ouvre).
     * Dans le SavedStateHandle : le texte reçu survit à la rotation et à la mort du process.
     */
    fun recevoirTexte(texte: String) {
        val outil = if (onglet.value == "boussole") Outil.BOUSSOLE else Outil.NIVEAU
        if (outil == Outil.NIVEAU) savedState[KEY_ONGLET] = "niveau"
        changerNote(outil, texte.trim())
        toast("Texte reçu : il est dans la note du relevé.")
    }

    fun changerNote(outil: Outil, texte: String) {
        savedState[if (outil == Outil.NIVEAU) KEY_NOTE_NIVEAU else KEY_NOTE_BOUSSOLE] = texte.take(140)
    }

    fun enregistrerNiveau(l: LectureNiveau) {
        val releve = Releve(
            id = "", outil = Outil.NIVEAU, valeur = arrondi1(l.valeur),
            mode = if (l is LectureNiveau.Plat) ModeNiveau.PLAT else ModeNiveau.CHANT,
            x = (l as? LectureNiveau.Plat)?.x?.let(::arrondi1), y = (l as? LectureNiveau.Plat)?.y?.let(::arrondi1),
            note = noteNiveau.value.trim(), createdAt = 0, etat = Etat.ATTENTE,
        )
        savedState[KEY_NOTE_NIVEAU] = ""
        enregistrer(releve)
    }

    fun enregistrerBoussole(cap: Int, fix: Fix?) {
        val releve = Releve(
            id = "", outil = Outil.BOUSSOLE, valeur = cap.toDouble(), mode = null, x = null, y = null,
            note = noteBoussole.value.trim(), createdAt = 0, etat = Etat.ATTENTE,
            latitude = fix?.latitude, longitude = fix?.longitude, precisionM = fix?.precisionM, nordGeographique = fix != null,
        )
        savedState[KEY_NOTE_BOUSSOLE] = ""
        enregistrer(releve)
    }

    private fun enregistrer(releve: Releve) = viewModelScope.launch {
        repository.enregistrer(releve)
        // Analytics — activation : le premier geste utile (premier = 1 la toute première fois). Écrit-on hors ligne ?
        app.mesure.evenement(
            "releve_saved", "/${releve.outil.cle}",
            "outil" to releve.outil.cle,
            "offline" to if (enLigne.value) 0 else 1,
            "premier" to if (app.reglages.premiereFois("releve_saved")) 1 else 0,
            "nord_geographique" to if (releve.nordGeographique) 1 else 0,
        )
        toast(if (enLigne.value) "Relevé enregistré." else "Relevé enregistré : il sera envoyé au retour du réseau.")
    }

    fun supprimer(id: String) = viewModelScope.launch { repository.supprimer(id) }

    /** Le bouton « Synchroniser » : relance l'envoi tout de suite, puis suit le travail jusqu'à son issue. */
    fun synchroniser() = viewModelScope.launch {
        if (releves.value.none { it.etat == Etat.ATTENTE }) {
            SyncWorker.planifier(app, immediat = true) // des suppressions peuvent attendre aussi
            toast("Tout est déjà synchronisé.")
            return@launch
        }
        val id = SyncWorker.planifier(app, immediat = true)
        val issue = withTimeoutOrNull(30_000) {
            WorkManager.getInstance(app).getWorkInfoByIdFlow(id).first { info ->
                info != null && (info.state.isFinished || (info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount > 0))
            }
        }
        toast(
            when (issue?.state) {
                WorkInfo.State.SUCCEEDED -> "Relevés synchronisés."
                null -> "Envoi en cours : il se terminera même si vous fermez l'application."
                else -> "Serveur injoignable : nouvel essai automatique plus tard."
            },
        )
    }

    companion object {
        val ONGLETS = listOf("niveau", "boussole", "releves", "guide")
        private const val KEY_ONGLET = "onglet"
        private const val KEY_NOTE_NIVEAU = "noteNiveau"
        private const val KEY_NOTE_BOUSSOLE = "noteBoussole"
        private const val KEY_RELEVE = "releveDemande"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AplombApp
                AplombViewModel(createSavedStateHandle(), app)
            }
        }
    }
}
