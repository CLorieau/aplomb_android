package corsica.universita.aplomb.ui.ecrans

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import corsica.universita.aplomb.BuildConfig
import corsica.universita.aplomb.ui.composants.BoutonDiscret
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import corsica.universita.aplomb.ui.composants.Bandeau
import corsica.universita.aplomb.ui.composants.Pastille
import corsica.universita.aplomb.ui.theme.Aplomb

/** <header class="entete"> : le titre, l'accroche, la pastille réseau. */
@Composable
fun Entete(enLigne: Boolean) {
    val c = Aplomb.couleurs
    val contexte = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Aplomb", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.texte)
            Text("Niveau à bulle et boussole, même sans réseau.", fontSize = 14.4.sp, color = c.texteDoux)
        }
        // .entete-actions : la pastille, et sous elle le lien discret (là où la PWA mettait « Installer »).
        Column(Modifier.padding(start = 16.dp), horizontalAlignment = Alignment.End) {
            Pastille(if (enLigne) "En ligne" else "Hors ligne", if (enLigne) c.ok else c.alerte)
            BoutonDiscret("Ouvrir la PWA", { ouvrirLaPwa(contexte) })
        }
    }
    if (!enLigne) {
        Bandeau {
            Text(
                "Hors ligne : les outils fonctionnent, l'envoi des relevés reprendra au retour du réseau.",
                color = c.texte, fontSize = 14.4.sp,
            )
        }
    }
}

/**
 * TD A3 : ouvrir la PWA dans le navigateur (ACTION_VIEW). Piège : une fois l'App Link vérifié, un simple
 * ACTION_VIEW sur https://<pwa> reviendrait… dans Aplomb. Il faut donc viser un navigateur.
 * Le corrigé du TD utilise un sélecteur CATEGORY_APP_BROWSER ; sur l'émulateur testé, il proposait
 * Paramètres, Gmail, Agenda… On demande plutôt à l'OS qui ouvrirait une URL que personne ne revendique
 * (le navigateur par défaut), puis on lui confie l'URL de la PWA. Exige le bloc <queries> du manifest.
 */
private fun ouvrirLaPwa(contexte: Context) {
    val pm = contexte.packageManager
    val sonde = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
    val navigateur = pm.resolveActivity(sonde, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        ?.takeIf { it != "android" } // "android" = le sélecteur système : pas de navigateur par défaut choisi
        ?: pm.queryIntentActivities(sonde, 0).firstOrNull()?.activityInfo?.packageName
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://${BuildConfig.PWA_HOST}/"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .setPackage(navigateur)
    try {
        contexte.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Log.w("Aplomb", "Aucun navigateur installé")
    }
}

private val LIBELLES =mapOf("niveau" to "Niveau", "boussole" to "Boussole", "releves" to "Relevés", "guide" to "Guide")

/** <nav class="onglets"> : quatre onglets, l'actif en couleur d'accent. */
@Composable
fun Onglets(actif: String, onglets: List<String>, onChoisir: (String) -> Unit) {
    val c = Aplomb.couleurs
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp)
            .background(c.surface, RoundedCornerShape(10.dp))
            .border(1.dp, c.bordure, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (nom in onglets) {
            val selectionne = nom == actif
            Box(
                Modifier
                    .weight(1f)
                    .background(if (selectionne) c.accent else c.surface, RoundedCornerShape(6.dp))
                    .semantics { selected = selectionne }
                    .clickable(role = Role.Tab) { onChoisir(nom) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    LIBELLES.getValue(nom),
                    color = if (selectionne) c.accentTexte else c.texte,
                    fontWeight = if (selectionne) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 15.2.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
