package corsica.universita.aplomb.ui.ecrans

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.aplomb.app
import corsica.universita.aplomb.capteurs.cardinal
import corsica.universita.aplomb.capteurs.fmt
import corsica.universita.aplomb.data.Etat
import corsica.universita.aplomb.data.ModeNiveau
import corsica.universita.aplomb.data.Outil
import corsica.universita.aplomb.data.Releve
import corsica.universita.aplomb.notif.Notifications
import corsica.universita.aplomb.ui.AplombViewModel
import corsica.universita.aplomb.ui.composants.Actions
import corsica.universita.aplomb.ui.composants.Bouton
import corsica.universita.aplomb.ui.composants.BoutonDiscret
import corsica.universita.aplomb.ui.composants.BoutonPrincipal
import corsica.universita.aplomb.ui.composants.Carte
import corsica.universita.aplomb.ui.composants.Message
import corsica.universita.aplomb.ui.composants.Paragraphe
import corsica.universita.aplomb.ui.composants.Pastille
import corsica.universita.aplomb.ui.composants.Titre2
import corsica.universita.aplomb.ui.composants.Titre3
import corsica.universita.aplomb.ui.permissions.EtatPermission
import corsica.universita.aplomb.ui.permissions.rememberPermissionFlow
import corsica.universita.aplomb.ui.theme.Aplomb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** titreReleve() de app.js */
fun titreReleve(r: Releve): String = when (r.outil) {
    Outil.BOUSSOLE -> {
        val v = r.valeur.toInt()
        "Boussole · $v° ${cardinal(v)}" + if (r.nordGeographique) " (géographique)" else ""
    }
    Outil.NIVEAU -> "Niveau · ${fmt(r.valeur)}° ${if (r.mode == ModeNiveau.CHANT) "sur chant" else "à plat"}"
}

fun dateReleve(r: Releve): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(r.createdAt))

val Etat.libelle get() = when (this) {
    Etat.ATTENTE -> "En attente"
    Etat.ENVOYE -> "Envoyé"
    Etat.REFUSE -> "Refusé par le serveur"
}

@Composable
fun Etat.couleur(): Color = when (this) {
    Etat.ATTENTE -> Aplomb.couleurs.alerte
    Etat.ENVOYE -> Aplomb.couleurs.ok
    Etat.REFUSE -> Aplomb.couleurs.erreur
}

/** <section id="releves"> */
@Composable
fun RelevesEcran(vm: AplombViewModel, onOuvrir: (String) -> Unit) {
    val c = Aplomb.couleurs
    val releves by vm.releves.collectAsStateWithLifecycle()
    val enLigne by vm.enLigne.collectAsStateWithLifecycle()
    val enAttente = releves.count { it.etat == Etat.ATTENTE }

    Titre2("Mes relevés")
    Actions {
        Text(
            if (releves.isEmpty()) "" else
                "${releves.size} relevé${if (releves.size > 1) "s" else ""}" + if (enAttente > 0) " · $enAttente en attente d'envoi" else "",
            color = c.texteDoux, fontSize = 14.4.sp, modifier = Modifier.weight(1f),
        )
        // Action indisponible sans réseau : signalée, pas en erreur (comme la PWA).
        Bouton("Synchroniser", { vm.synchroniser() }, actif = enLigne)
    }
    if (releves.isEmpty()) {
        Message("Aucun relevé pour l'instant. Enregistrez une mesure depuis le niveau ou la boussole.")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in releves) LigneReleve(r, onOuvrir = { onOuvrir(r.id) }, onSupprimer = { vm.supprimer(r.id) })
    }
    CarteNotifications(vm)
}

/** .releve : titre, note, date, et à droite l'état et « Supprimer ». Un appui ouvre le détail. */
@Composable
private fun LigneReleve(r: Releve, onOuvrir: () -> Unit, onSupprimer: () -> Unit) {
    val c = Aplomb.couleurs
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(10.dp))
            .border(1.dp, c.bordure, RoundedCornerShape(10.dp))
            .clickable(onClick = onOuvrir)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titreReleve(r), fontWeight = FontWeight.SemiBold, color = c.texte, fontSize = 16.sp)
            if (r.note.isNotEmpty()) Text(r.note, color = c.texteDoux, fontSize = 13.6.sp)
            Text(dateReleve(r) + if (r.latitude != null) " · position jointe" else "", color = c.texteDoux, fontSize = 13.6.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Pastille(r.etat.libelle, r.etat.couleur(), taille = 12.sp)
            Bouton("Supprimer", onSupprimer, Modifier.heightIn(min = 32.dp), taille = 13.6.sp)
        }
    }
}

/**
 * La carte « Notifications ». Dans la PWA : abonnement Web Push. Ici : la permission POST_NOTIFICATIONS
 * (Android 13+), avec un bouton par état — expliquer, redemander, puis les réglages après le refus définitif.
 */
@Composable
private fun CarteNotifications(vm: AplombViewModel) {
    val ctx = LocalContext.current
    val jeton by vm.fcmToken.collectAsStateWithLifecycle()
    val flow = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermissionFlow(Manifest.permission.POST_NOTIFICATIONS, onAccordee = { vm.toast("Notifications activées.") })
    } else {
        null
    }
    val etat = flow?.etat ?: if (Notifications.permises(ctx)) EtatPermission.Accordee else EtatPermission.RefuseeDefinitivement
    val reglagesNotifs = {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Carte(Modifier.padding(top = 24.dp)) {
        Titre3("Notifications", enTete = true)
        Paragraphe(
            "Recevez une notification quand des relevés pris hors ligne sont arrivés au serveur, même si l'application est fermée, " +
                "et quand la surveillance d'aplomb détecte un mouvement.",
        )
        Actions(arrangement = Arrangement.spacedBy(8.dp)) {
            when (etat) {
                EtatPermission.NonDemandee -> BoutonPrincipal("Activer les notifications", { flow?.demander() })
                EtatPermission.RefuseeUneFois -> BoutonPrincipal("Autoriser les notifications", { flow?.demander() })
                EtatPermission.RefuseeDefinitivement -> Bouton("Ouvrir les réglages", reglagesNotifs)
                EtatPermission.Accordee -> BoutonDiscret("Régler ou désactiver", reglagesNotifs)
            }
        }
        when (etat) {
            EtatPermission.RefuseeUneFois -> Message(
                "Elles servent seulement à confirmer l'envoi des relevés pris hors ligne et aux alertes de surveillance. " +
                    "Sans elles, l'état de chaque relevé reste visible dans cette liste.",
            )
            EtatPermission.RefuseeDefinitivement -> Message(
                "Notifications refusées : Aplomb fonctionne sans, l'état de chaque relevé reste visible dans cette liste. " +
                    "Vous pouvez les autoriser dans les réglages.",
            )
            EtatPermission.Accordee -> Message("Notifications activées. Chaque type (synchronisation, alertes, messages) se règle séparément.")
            EtatPermission.NonDemandee -> Unit
        }
        if (ctx.app.fcmActif && jeton != null) {
            BoutonDiscret("Copier mon jeton FCM (test du push)", {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Jeton FCM", jeton))
                vm.toast("Jeton FCM copié.")
            })
        }
    }
}
