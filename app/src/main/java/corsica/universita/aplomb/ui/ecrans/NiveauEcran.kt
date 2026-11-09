package corsica.universita.aplomb.ui.ecrans

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.aplomb.app
import corsica.universita.aplomb.capteurs.ANGLE_MAX
import corsica.universita.aplomb.capteurs.LectureNiveau
import corsica.universita.aplomb.capteurs.fmt
import corsica.universita.aplomb.data.Outil
import corsica.universita.aplomb.surveillance.EtatSurveillance
import corsica.universita.aplomb.surveillance.SurveillanceService
import corsica.universita.aplomb.ui.AplombViewModel
import corsica.universita.aplomb.ui.MesuresViewModel
import corsica.universita.aplomb.ui.composants.Actions
import corsica.universita.aplomb.ui.composants.Bouton
import corsica.universita.aplomb.ui.composants.BoutonDiscret
import corsica.universita.aplomb.ui.composants.BoutonPrincipal
import corsica.universita.aplomb.ui.composants.Carte
import corsica.universita.aplomb.ui.composants.Lecture
import corsica.universita.aplomb.ui.composants.LectureDetail
import corsica.universita.aplomb.ui.composants.Message
import corsica.universita.aplomb.ui.composants.Option
import corsica.universita.aplomb.ui.composants.Paragraphe
import corsica.universita.aplomb.ui.composants.Saisie
import corsica.universita.aplomb.ui.composants.Titre2
import corsica.universita.aplomb.ui.composants.Titre3
import corsica.universita.aplomb.ui.permissions.EtatPermission
import corsica.universita.aplomb.ui.permissions.rememberPermissionFlow
import corsica.universita.aplomb.ui.theme.Aplomb
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

/** <section id="niveau"> */
@Composable
fun NiveauEcran(vm: AplombViewModel, mesures: MesuresViewModel) {
    val c = Aplomb.couleurs
    val lecture by mesures.niveau.collectAsStateWithLifecycle()
    val note by vm.noteNiveau.collectAsStateWithLifecycle()
    var sansMesure by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_500)
        sansMesure = mesures.niveau.value == null
    }

    if (!mesures.niveauDisponible || (sansMesure && lecture == null)) {
        Carte { Message("Aucune mesure reçue : cet appareil n'a pas de capteur d'orientation. Le niveau ne peut pas mesurer.", couleur = c.erreur) }
    }
    EcranAllume()

    Titre2("Niveau à bulle")
    VisuelNiveau(lecture)
    Lecture(lecture?.let { "${fmt(it.valeur)}°" } ?: "–", if (lecture?.deNiveau == true) c.ok else c.texte)
    LectureDetail(
        when (val l = lecture) {
            null -> "Posez le téléphone à plat ou sur la tranche."
            is LectureNiveau.Plat -> "À plat · gauche-droite ${fmt(l.x)}° · haut-bas ${fmt(l.y)}°"
            is LectureNiveau.Chant -> "Sur chant · écart à l'horizontale ou à la verticale"
        },
    )
    Actions {
        Bouton("Étalonner", { if (mesures.etalonner()) vm.toast("Étalonné : cette position est le nouveau zéro.") }, actif = lecture != null)
        BoutonDiscret("Annuler l'étalonnage", {
            mesures.annulerEtalonnage()
            vm.toast("Étalonnage annulé.")
        }, actif = lecture != null)
    }
    Saisie(note, { vm.changerNote(Outil.NIVEAU, it) }, "Note (facultatif) : étagère du salon…", actif = lecture != null) {
        lecture?.let(vm::enregistrerNiveau)
    }
    CarteSurveillance(vm, mesures)
}

/** « Garder l'écran allumé pendant la mesure » : la Screen Wake Lock de la PWA, ici sans permission (keepScreenOn). */
@Composable
private fun EcranAllume() {
    var coche by rememberSaveable { mutableStateOf(false) }
    val vue = LocalView.current
    DisposableEffect(coche) {
        vue.keepScreenOn = coche
        onDispose { vue.keepScreenOn = false }
    }
    Option("Garder l'écran allumé pendant la mesure", coche) { coche = it }
}

/** La fiole (à plat) ou le tube (sur chant), comme .niveau-visuel dans styles.css. */
@Composable
private fun VisuelNiveau(lecture: LectureNiveau?) {
    val c = Aplomb.couleurs
    val bordure = if (lecture?.deNiveau == true) c.ok else c.bordure
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Canvas(Modifier.widthIn(max = 288.dp).fillMaxWidth().aspectRatio(1f)) {
        val bordurePx = 3.dp.toPx()
        if (lecture is LectureNiveau.Chant) {
            rotate(lecture.cran.toFloat()) { dessinerTube(lecture.valeur, bordure, bordurePx, c.surface, c.texteDoux, c.bulle) }
        } else {
            dessinerFiole(lecture as LectureNiveau.Plat?, bordure, bordurePx, c.surface, c.bordure, c.texteDoux, c.bulle)
        }
    }
    }
}

private fun DrawScope.dessinerFiole(l: LectureNiveau.Plat?, bordure: Color, bordurePx: Float, surface: Color, traits: Color, cible: Color, bulle: Color) {
    val r = size.minDimension / 2
    val centre = center
    drawCircle(surface, r)
    drawLine(traits, Offset(centre.x - r, centre.y), Offset(centre.x + r, centre.y), 1.dp.toPx())
    drawLine(traits, Offset(centre.x, centre.y - r), Offset(centre.x, centre.y + r), 1.dp.toPx())
    drawCircle(bordure, r - bordurePx / 2, style = Stroke(bordurePx))
    val interieur = size.minDimension - 2 * bordurePx
    drawCircle(cible, interieur * 0.13f, style = Stroke(2.dp.toPx())) // .cible { inset: 37% }

    val diametre = interieur * 0.24f
    val course = (interieur - diametre) / 2
    var dx = 0.0
    var dy = 0.0
    if (l != null) {
        // La bulle monte vers le côté le plus haut. L'axe y de l'écran pointe vers le bas, d'où le signe.
        dx = l.x / ANGLE_MAX
        dy = -l.y / ANGLE_MAX
        val norme = hypot(dx, dy)
        if (norme > 1) { dx /= norme; dy /= norme }
    }
    dessinerBulle(Offset(centre.x + (dx * course).toFloat(), centre.y + (dy * course).toFloat()), diametre / 2, bulle)
}

private fun DrawScope.dessinerTube(valeur: Double, bordure: Color, bordurePx: Float, surface: Color, reperes: Color, bulle: Color) {
    val largeur = size.width * 0.92f
    val hauteur = size.height * 0.26f
    val gauche = (size.width - largeur) / 2
    val haut = (size.height - hauteur) / 2
    val rayon = CornerRadius(hauteur / 2)
    drawRoundRect(surface, Offset(gauche, haut), Size(largeur, hauteur), rayon)
    for (f in listOf(0.37f, 0.63f)) {
        drawLine(reperes, Offset(gauche + largeur * f, haut), Offset(gauche + largeur * f, haut + hauteur), 2.dp.toPx())
    }
    drawRoundRect(bordure, Offset(gauche + bordurePx / 2, haut + bordurePx / 2), Size(largeur - bordurePx, hauteur - bordurePx), rayon, style = Stroke(bordurePx))
    val interieurL = largeur - 2 * bordurePx
    val diametre = (hauteur - 2 * bordurePx) * 0.84f
    val course = (interieurL - diametre) / 2
    val d = (valeur / ANGLE_MAX).coerceIn(-1.0, 1.0)
    dessinerBulle(Offset(center.x + (d * course).toFloat(), center.y), diametre / 2, bulle)
}

private fun DrawScope.dessinerBulle(centre: Offset, rayon: Float, couleur: Color) {
    drawCircle(couleur.copy(alpha = 0.9f), rayon, centre)
    drawCircle(Color.Black.copy(alpha = 0.25f), rayon - 1.dp.toPx(), centre, style = Stroke(2.dp.toPx()))
}

private val SEUILS = listOf(0.5f, 1f, 2f, 5f)

/**
 * La fonctionnalité native : la surveillance écran éteint (foreground service).
 * Un bouton par état de la permission POST_NOTIFICATIONS : sans notification, l'alerte ne servirait à rien.
 */
@Composable
private fun CarteSurveillance(vm: AplombViewModel, mesures: MesuresViewModel) {
    val c = Aplomb.couleurs
    val ctx = LocalContext.current
    val etat by SurveillanceService.etat.collectAsStateWithLifecycle()
    val seuil by mesures.seuilSurveillance.collectAsStateWithLifecycle()
    val lancer = {
        SurveillanceService.demarrer(ctx, seuil)
        vm.toast("Surveillance lancée : posez le téléphone, la référence est prise dans 3 s.")
    }
    val notifs = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermissionFlow(Manifest.permission.POST_NOTIFICATIONS, onAccordee = lancer)
    } else {
        null
    }

    Carte(Modifier.padding(top = 24.dp)) {
        Titre3("Surveillance écran éteint", enTete = true)
        Paragraphe(
            "Posez le téléphone sur ce que vous voulez surveiller (un cadre qui sèche, une étagère qu'on charge), " +
                "puis éteignez l'écran : Aplomb vous alerte dès que l'inclinaison bouge de plus que le seuil.",
            taille = 15.2.sp,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (s in SEUILS) {
                FilterChip(
                    selected = s == seuil, onClick = { mesures.choisirSeuil(s) },
                    enabled = etat !is EtatSurveillance.Active,
                    label = { Text("${fmt(s.toDouble()).removeSuffix(",0")}°") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.accent, selectedLabelColor = c.accentTexte),
                )
            }
        }
        when (val e = etat) {
            is EtatSurveillance.Active -> {
                val depuis = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(e.depuis))
                Message(
                    "Active depuis $depuis · " +
                        (e.ecart?.let { "écart ${fmt(it)}° · " } ?: "prise de la référence… · ") +
                        (if (e.alertes == 0) "aucune alerte" else "${e.alertes} alerte${if (e.alertes > 1) "s" else ""}"),
                    couleur = if (e.alertes > 0) c.alerte else c.ok,
                )
                Bouton("Arrêter la surveillance", { SurveillanceService.arreter(ctx) })
            }
            EtatSurveillance.Inactive -> when (notifs?.etat ?: EtatPermission.Accordee) {
                EtatPermission.NonDemandee -> BoutonPrincipal("Surveiller cet aplomb", { notifs?.demander() })
                EtatPermission.RefuseeUneFois -> {
                    Message("Aplomb a besoin des notifications pour vous prévenir quand l'aplomb bouge, écran éteint.")
                    BoutonPrincipal("Autoriser et surveiller", { notifs?.demander() })
                }
                EtatPermission.RefuseeDefinitivement -> {
                    Message(
                        "Notifications refusées : la surveillance ne pourrait pas vous alerter. " +
                            "Le niveau reste utilisable ; pour surveiller, autorisez les notifications dans les réglages.",
                    )
                    Bouton("Ouvrir les réglages", {
                        ctx.app.mesure.evenement("surveillance_blocked", "/niveau", "raison" to "notifications_refusees")
                        notifs?.ouvrirReglages?.invoke()
                    })
                }
                EtatPermission.Accordee -> BoutonPrincipal("Surveiller cet aplomb", lancer)
            }
        }
    }
}

