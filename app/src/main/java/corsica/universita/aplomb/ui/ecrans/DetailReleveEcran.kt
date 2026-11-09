package corsica.universita.aplomb.ui.ecrans

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.aplomb.app
import corsica.universita.aplomb.capteurs.fmt
import corsica.universita.aplomb.data.Releve
import corsica.universita.aplomb.ui.AplombViewModel
import corsica.universita.aplomb.ui.composants.Actions
import corsica.universita.aplomb.ui.composants.Bouton
import corsica.universita.aplomb.ui.composants.BoutonDiscret
import corsica.universita.aplomb.ui.composants.BoutonPrincipal
import corsica.universita.aplomb.ui.composants.Carte
import corsica.universita.aplomb.ui.composants.Message
import corsica.universita.aplomb.ui.composants.Pastille
import corsica.universita.aplomb.ui.composants.Titre2
import corsica.universita.aplomb.ui.theme.Aplomb
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Le détail d'un relevé : seconde entrée de Navigation 3 (DetailReleve(id)). Ouvert depuis la liste,
 * ou par le clic d'une notification / d'un push qui vise ce relevé — même app tuée entre-temps.
 */
@Composable
fun DetailReleveEcran(id: String, vm: AplombViewModel, onRetour: () -> Unit) {
    val ctx = LocalContext.current
    val flux = remember(id) { ctx.app.repository.releve(id) }
    val releve by flux.collectAsStateWithLifecycle(initialValue = null)
    var charge by remember { mutableStateOf(false) }
    LaunchedEffect(releve) { if (releve != null) charge = true }

    BoutonDiscret("← Mes relevés", onRetour, Modifier.padding(top = 8.dp))
    val r = releve
    if (r == null) {
        Titre2("Relevé")
        Message(if (charge) "Ce relevé a été supprimé." else "Relevé introuvable sur cet appareil (supprimé, ou pas encore synchronisé).")
        return
    }
    Titre2(titreReleve(r))
    Carte {
        Ligne("Mesure", mesure(r))
        if (r.note.isNotEmpty()) Ligne("Note", r.note)
        Ligne("Date", dateReleve(r))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("État", color = Aplomb.couleurs.texteDoux, fontSize = 14.4.sp)
            Pastille(r.etat.libelle, r.etat.couleur())
        }
        if (r.latitude != null && r.longitude != null) {
            Ligne(
                "Position",
                String.format(Locale.FRANCE, "%.5f, %.5f", r.latitude, r.longitude) +
                    (r.precisionM?.let { " · à ${it.roundToInt()} m près" } ?: ""),
            )
        }
    }
    Actions(arrangement = Arrangement.spacedBy(8.dp)) {
        BoutonPrincipal("Partager", { partager(ctx, r) })
        if (r.latitude != null && r.longitude != null) {
            Bouton("Voir sur une carte", {
                val uri = Uri.parse("geo:${r.latitude},${r.longitude}?q=${r.latitude},${r.longitude}(${Uri.encode(titreReleve(r))})")
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    vm.toast("Aucune application de carte installée.")
                }
            })
        }
    }
    BoutonDiscret("Supprimer ce relevé", {
        vm.supprimer(r.id)
        onRetour()
    })
}

@Composable
private fun Ligne(libelle: String, valeur: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(libelle, color = Aplomb.couleurs.texteDoux, fontSize = 14.4.sp)
        Text(valeur, color = Aplomb.couleurs.texte, fontSize = 16.sp)
    }
}

private fun mesure(r: Releve) = if (r.x != null && r.y != null) {
    "${fmt(r.valeur)}° · gauche-droite ${fmt(r.x)}° · haut-bas ${fmt(r.y)}°"
} else {
    titreReleve(r).substringAfter(" · ")
}

/** ACTION_SEND : la feuille de partage du système, vers Messages, Gmail, WhatsApp… */
private fun partager(ctx: android.content.Context, r: Releve) {
    val texte = buildString {
        append(titreReleve(r))
        if (r.note.isNotEmpty()) append(" — ").append(r.note)
        append(" — ").append(dateReleve(r))
        if (r.latitude != null) append(" — https://maps.google.com/?q=${r.latitude},${r.longitude}")
        append(" (mesuré avec Aplomb)")
    }
    val envoi = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texte)
    ctx.startActivity(Intent.createChooser(envoi, "Partager le relevé").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
