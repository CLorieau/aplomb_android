package corsica.universita.aplomb.ui.ecrans

import android.Manifest
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import corsica.universita.aplomb.app
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.aplomb.capteurs.cardinal
import corsica.universita.aplomb.capteurs.fmt
import corsica.universita.aplomb.capteurs.normaliserCap
import corsica.universita.aplomb.data.Outil
import corsica.universita.aplomb.position.Fix
import corsica.universita.aplomb.ui.AplombViewModel
import corsica.universita.aplomb.ui.MesuresViewModel
import corsica.universita.aplomb.ui.composants.Bouton
import corsica.universita.aplomb.ui.composants.BoutonDiscret
import corsica.universita.aplomb.ui.composants.Carte
import corsica.universita.aplomb.ui.composants.Lecture
import corsica.universita.aplomb.ui.composants.LectureDetail
import corsica.universita.aplomb.ui.composants.Message
import corsica.universita.aplomb.ui.composants.Paragraphe
import corsica.universita.aplomb.ui.composants.Saisie
import corsica.universita.aplomb.ui.composants.Titre2
import corsica.universita.aplomb.ui.composants.Titre3
import corsica.universita.aplomb.ui.permissions.EtatPermission
import corsica.universita.aplomb.ui.permissions.rememberPermissionFlow
import corsica.universita.aplomb.ui.theme.Aplomb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** <section id="boussole"> */
@Composable
fun BoussoleEcran(vm: AplombViewModel, mesures: MesuresViewModel) {
    val c = Aplomb.couleurs
    val boussole by mesures.boussole.collectAsStateWithLifecycle()
    val fix by mesures.fix.collectAsStateWithLifecycle()
    val note by vm.noteBoussole.collectAsStateWithLifecycle()
    var sansCap by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_500)
        sansCap = mesures.boussole.value == null
    }

    val declinaison = fix?.declinaison ?: 0.0
    val cap = boussole?.let { normaliserCap(it.capMagnetique + declinaison).roundToInt() % 360 }

    Titre2("Boussole")
    Rose(rotation = boussole?.let { (it.rotationRose - declinaison).toFloat() } ?: 0f)
    Lecture(cap?.let { "$it° ${cardinal(it)}" } ?: "–")
    LectureDetail(
        when {
            !mesures.boussoleDisponible || (sansCap && boussole == null) ->
                "Cet appareil ne fournit pas de cap magnétique : la boussole est indisponible."
            boussole == null -> "Tenez le téléphone à plat, loin des objets métalliques."
            fix != null -> "Cap géographique · direction pointée par le haut du téléphone"
            else -> "Cap magnétique · direction pointée par le haut du téléphone"
        },
    )
    // Ce que le web ne dit jamais : l'OS estime la précision du magnétomètre.
    if ((boussole?.precision ?: SensorManager.SENSOR_STATUS_ACCURACY_HIGH) <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
        LectureDetail("Boussole peu précise : décrivez un « 8 » dans l'air avec le téléphone.", c.alerte)
    }
    Saisie(note, { vm.changerNote(Outil.BOUSSOLE, it) }, "Note (facultatif) : direction du col…", actif = cap != null) {
        cap?.let { vm.enregistrerBoussole(it, fix) }
    }
    CarteNordGeographique(vm, mesures, fix)
}

/** Le <svg id="rose"> de index.html, coordonnée par coordonnée (viewBox -100 -100 200 200). */
@Composable
private fun Rose(rotation: Float) {
    val c = Aplomb.couleurs
    val mesureur = rememberTextMeasurer()
    val style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 288.dp).fillMaxWidth()) {
            // .repere : le triangle fixe qui marque le haut du téléphone
            Canvas(Modifier.fillMaxWidth().padding(bottom = 0.dp).aspectRatio(288f / 14.4f)) {
                val l = 9.6.dp.toPx()
                drawPath(
                    Path().apply {
                        moveTo(center.x - l, 0f); lineTo(center.x + l, 0f); lineTo(center.x, l); close()
                    },
                    c.accent,
                )
            }
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val k = size.width / 200f
                translate(center.x, center.y) {
                    rotate(rotation, pivot = Offset.Zero) {
                        scale(k, k, pivot = Offset.Zero) {
                            drawCircle(c.surface, 96f, Offset.Zero)
                            drawCircle(c.bordure, 96f, Offset.Zero, style = Stroke(2f))
                            for (angle in 0 until 360 step 10) {
                                val majeure = angle % 30 == 0
                                rotate(angle.toFloat(), pivot = Offset.Zero) {
                                    drawLine(
                                        if (majeure) c.texte else c.texteDoux,
                                        Offset(0f, -94f), Offset(0f, if (majeure) -82f else -88f), if (majeure) 2f else 1f,
                                    )
                                }
                            }
                            drawPath(Path().apply { moveTo(0f, -46f); lineTo(7f, 0f); lineTo(-7f, 0f); close() }, c.erreur)
                            drawPath(Path().apply { moveTo(0f, 46f); lineTo(7f, 0f); lineTo(-7f, 0f); close() }, c.texteDoux)
                        }
                        // Les lettres : position SVG (x, y de ligne de base) → coin haut-gauche du texte mesuré.
                        for ((lettre, x, y) in listOf(Triple("N", 0f, -58f), Triple("E", 66f, 7f), Triple("S", 0f, 72f), Triple("O", -66f, 7f))) {
                            val mesure = mesureur.measure(lettre, style.copy(color = if (lettre == "N") c.erreur else c.texte, fontSize = (18 * k).toSp()))
                            drawText(
                                mesure,
                                topLeft = Offset(x * k - mesure.size.width / 2f, y * k - mesure.firstBaseline),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nord géographique : la permission de position, demandée au bon moment (après un appui, avec la raison sous les yeux),
 * jusqu'au refus définitif. Précise ou approximative : les deux suffisent pour la déclinaison.
 */
@Composable
private fun CarteNordGeographique(vm: AplombViewModel, mesures: MesuresViewModel, fix: Fix?) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val enCours by mesures.localisationEnCours.collectAsStateWithLifecycle()
    val localiser: () -> Unit = {
        scope.launch {
            val ok = mesures.localiser()
            if (ok) vm.toast("Nord géographique : déclinaison corrigée.")
            else vm.toast("Position indisponible pour l'instant (localisation coupée ?).")
            ctx.app.mesure.evenement("north_corrected", "/boussole", "result" to if (ok) "ok" else "no_fix")
        }
    }
    val position = rememberPermissionFlow(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
        auMoinsUne = true, onAccordee = localiser,
    )

    Carte(Modifier.padding(top = 24.dp)) {
        Titre3("Nord géographique", enTete = true)
        if (fix != null) {
            val signe = if (fix.declinaison >= 0) "+" else ""
            Paragraphe(
                "Déclinaison $signe${fmt(fix.declinaison)}° corrigée · position " +
                    (if (fix.precise) "à ${fix.precisionM.roundToInt()} m près" else "approximative (à ${(fix.precisionM / 1000).roundToInt().coerceAtLeast(1)} km près)") +
                    ". Le cap affiché est celui des cartes ; la position est jointe aux relevés de boussole et reste sur le téléphone.",
                taille = 15.2.sp,
            )
            BoutonDiscret("Revenir au nord magnétique", { mesures.revenirAuNordMagnetique() })
            return@Carte
        }
        Paragraphe(
            "La boussole indique le nord magnétique. Avec votre position, Aplomb calcule la déclinaison du lieu " +
                "et affiche le nord géographique des cartes. La position n'est jamais envoyée au serveur.",
            taille = 15.2.sp,
        )
        if (enCours) {
            Message("Recherche de la position…")
            return@Carte
        }
        when (position.etat) {
            EtatPermission.NonDemandee, EtatPermission.Accordee ->
                Bouton("Utiliser ma position", { if (position.etat == EtatPermission.Accordee) localiser() else position.demander() })
            EtatPermission.RefuseeUneFois -> {
                Message("Sans position, la boussole reste en nord magnétique. Une position approximative suffit.")
                Bouton("Autoriser la position", position.demander)
            }
            EtatPermission.RefuseeDefinitivement -> {
                Message("Position refusée : la boussole reste en nord magnétique. Vous pouvez l'autoriser dans les réglages d'Aplomb.")
                Bouton("Ouvrir les réglages", {
                    ctx.app.mesure.evenement("north_corrected", "/boussole", "result" to "denied_forever")
                    position.ouvrirReglages()
                })
            }
        }
    }
}
