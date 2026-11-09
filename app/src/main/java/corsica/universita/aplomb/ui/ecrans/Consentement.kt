package corsica.universita.aplomb.ui.ecrans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import corsica.universita.aplomb.mesure.Mesure
import corsica.universita.aplomb.ui.composants.Actions
import corsica.universita.aplomb.ui.composants.Bouton
import corsica.universita.aplomb.ui.composants.BoutonDiscret
import corsica.universita.aplomb.ui.composants.BoutonPrincipal
import corsica.universita.aplomb.ui.composants.Carte
import corsica.universita.aplomb.ui.composants.Message
import corsica.universita.aplomb.ui.composants.Paragraphe
import corsica.universita.aplomb.ui.composants.Titre3

private const val EXPLICATION =
    "Aplomb peut compter, de façon anonyme, l'usage de ses outils : écrans ouverts, relevés enregistrés, " +
        "surveillances lancées. Mesure faite avec Umami, sans cookie ni identifiant publicitaire. " +
        "Jamais le texte de vos notes, ni votre position."

/**
 * L'écran de choix (Annexe 4, le consentement) : tant qu'il n'a pas reçu de réponse, rien n'est collecté.
 * « Non » : rien ne part, l'app marche pareil. Le choix se change dans le Guide (Vie privée).
 */
@Composable
fun CarteConsentement(mesure: Mesure, onReponse: (Boolean) -> Unit) {
    Carte(Modifier.padding(bottom = 16.dp)) {
        Titre3("Mesure d'audience", enTete = true)
        Paragraphe(EXPLICATION, taille = 15.2.sp)
        Actions(arrangement = Arrangement.spacedBy(8.dp)) {
            BoutonPrincipal("Accepter", {
                mesure.repondre(true)
                onReponse(true)
            })
            Bouton("Refuser", {
                mesure.repondre(false)
                onReponse(false)
            })
        }
    }
}

/** Dans le Guide : l'état du choix, et de quoi le changer à tout moment. */
@Composable
fun ChoixMesure(mesure: Mesure, consentement: Boolean?) {
    if (!mesure.configuree) return
    Message(
        when (consentement) {
            true -> "Mesure d'audience : acceptée. $EXPLICATION"
            false -> "Mesure d'audience : refusée. Rien n'est collecté."
            null -> "Mesure d'audience : pas encore choisie. Rien n'est collecté."
        },
    )
    if (consentement == true) {
        BoutonDiscret("Ne plus mesurer", { mesure.repondre(false) })
    } else {
        BoutonDiscret("Accepter la mesure d'audience", { mesure.repondre(true) })
    }
}
