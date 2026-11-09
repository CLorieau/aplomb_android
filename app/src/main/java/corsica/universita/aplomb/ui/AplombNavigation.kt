package corsica.universita.aplomb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import corsica.universita.aplomb.ui.composants.Toast
import corsica.universita.aplomb.ui.ecrans.CarteConsentement
import corsica.universita.aplomb.ui.ecrans.ChoixMesure
import corsica.universita.aplomb.app
import androidx.compose.ui.platform.LocalContext
import corsica.universita.aplomb.ui.ecrans.BoussoleEcran
import corsica.universita.aplomb.ui.ecrans.DetailReleveEcran
import corsica.universita.aplomb.ui.ecrans.Entete
import corsica.universita.aplomb.ui.ecrans.GuideEcran
import corsica.universita.aplomb.ui.ecrans.NiveauEcran
import corsica.universita.aplomb.ui.ecrans.Onglets
import corsica.universita.aplomb.ui.ecrans.RelevesEcran
import corsica.universita.aplomb.ui.theme.Aplomb
import kotlinx.serialization.Serializable

/** L'écran à onglets (les quatre vues de index.html). L'onglet actif vit dans le SavedStateHandle. */
@Serializable
data object Accueil : NavKey

/** Le détail d'un relevé. L'argument fait partie de la clé, sauvegardée avec le backstack. */
@Serializable
data class DetailReleve(val id: String) : NavKey

/**
 * Navigation 3 : le backstack est un état sauvegardé (rotation ET mort du process).
 * Naviguer = ajouter une clé ; revenir = retirer la dernière (geste retour inclus).
 */
@Composable
fun AplombNavigation(vm: AplombViewModel) {
    val mesures: MesuresViewModel = viewModel(factory = MesuresViewModel.Factory)
    val backStack = rememberNavBackStack(Accueil)
    val toast by vm.toast.collectAsStateWithLifecycle()

    // Clic sur une notification / un push qui vise un relevé → son détail.
    val demande by vm.releveDemande.collectAsStateWithLifecycle()
    LaunchedEffect(demande) {
        val id = demande ?: return@LaunchedEffect
        vm.releveOuvert()
        if (backStack.lastOrNull() != DetailReleve(id)) backStack.add(DetailReleve(id))
    }

    // Analytics : l'écran vu, envoyé à la main (une seule Activity : le suivi automatique ne verrait que MainActivity).
    // Les noms sont écrits en dur : en release, R8 renommerait les classes.
    val mesure = LocalContext.current.app.mesure
    val consentement by mesure.consentement.collectAsStateWithLifecycle()
    val ongletVu by vm.onglet.collectAsStateWithLifecycle()
    val sommet = backStack.lastOrNull()
    LaunchedEffect(sommet, ongletVu, consentement) {
        if (consentement != true) return@LaunchedEffect
        when (sommet) {
            is DetailReleve -> mesure.ecran("/releve", "Détail d'un relevé")
            Accueil -> mesure.ecran("/$ongletVu", TITRES[ongletVu] ?: ongletVu)
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Aplomb.couleurs.fond)) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Accueil> {
                    val onglet by vm.onglet.collectAsStateWithLifecycle()
                    val enLigne by vm.enLigne.collectAsStateWithLifecycle()
                    Page {
                        Entete(enLigne)
                        if (mesure.configuree && consentement == null) {
                            CarteConsentement(mesure) { oui -> vm.toast(if (oui) "Merci ! Mesure d'audience activée." else "Compris : rien ne sera mesuré.") }
                        }
                        Onglets(onglet, AplombViewModel.ONGLETS, vm::choisirOnglet)
                        when (onglet) {
                            "niveau" -> NiveauEcran(vm, mesures)
                            "boussole" -> BoussoleEcran(vm, mesures)
                            "releves" -> RelevesEcran(vm, onOuvrir = { backStack.add(DetailReleve(it)) })
                            else -> GuideEcran { ChoixMesure(mesure, consentement) }
                        }
                    }
                }
                entry<DetailReleve> { cle ->
                    val enLigne by vm.enLigne.collectAsStateWithLifecycle()
                    Page {
                        Entete(enLigne)
                        DetailReleveEcran(cle.id, vm, onRetour = { backStack.removeLastOrNull() })
                    }
                }
            },
        )
        Toast(toast)
    }
}

private val TITRES = mapOf("niveau" to "Niveau", "boussole" to "Boussole", "releves" to "Relevés", "guide" to "Guide")

/** body { max-width: 34rem; margin: 0 auto; padding: 0 1rem 4rem } */
@Composable
private fun Page(contenu: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Aplomb.couleurs.fond)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 544.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 64.dp),
            content = contenu,
        )
    }
}
