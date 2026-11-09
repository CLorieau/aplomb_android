package corsica.universita.aplomb.ui.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.aplomb.app
import kotlinx.coroutines.launch

/**
 * Une permission n'est pas un booléen. Quatre états, et l'OS n'en distingue que trois :
 *
 *   état                    checkSelfPermission  shouldShowRationale  déjà refusée (drapeau) ?
 *   NonDemandee             DENIED               false                non
 *   RefuseeUneFois          DENIED               true                 oui
 *   RefuseeDefinitivement   DENIED               false                oui   ← mêmes réponses que NonDemandee !
 *   Accordee                GRANTED              —                    —
 *
 * Seul le drapeau « déjà refusée », gardé par l'app dans DataStore (il survit au redémarrage), sépare
 * « jamais demandée » de « refusée définitivement » (deux refus, Android 11+). Il est posé au premier refus,
 * pas avant le dialogue : sinon l'écran afficherait « refusée définitivement » pendant que le dialogue est ouvert.
 */
enum class EtatPermission { NonDemandee, Accordee, RefuseeUneFois, RefuseeDefinitivement }

class PermissionFlow(
    val etat: EtatPermission,
    /** Ouvre le dialogue de l'OS (inutile après un refus définitif : il ne s'afficherait pas). */
    val demander: () -> Unit,
    /** La page « Infos sur l'appli » des réglages : seule issue après un refus définitif. */
    val ouvrirReglages: () -> Unit,
)

private fun Context.accordee(permissions: List<String>, auMoinsUne: Boolean): Boolean {
    val accordees = permissions.map { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    return if (auMoinsUne) accordees.any { it } else accordees.all { it }
}

/**
 * Une seule classe pour toutes les permissions d'Aplomb (position, notifications).
 * @param auMoinsUne position : FINE + COARSE demandées ensemble ; « Approximative » (COARSE seule) n'est pas un refus.
 * @param onAccordee appelé juste après que l'utilisateur a accordé : l'action qu'il avait demandée continue.
 */
@Composable
fun rememberPermissionFlow(
    vararg permissions: String,
    auMoinsUne: Boolean = true,
    onAccordee: () -> Unit = {},
): PermissionFlow {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val reglages = context.app.reglages
    val scope = rememberCoroutineScope()
    val liste = permissions.toList()
    val demandees by reglages.permissionsDemandees.collectAsStateWithLifecycle(initialValue = emptySet())
    var version by remember { mutableIntStateOf(0) } // relit l'état de l'OS quand il change

    fun rationale() = activity != null && liste.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    fun calculer(): EtatPermission = when {
        context.accordee(liste, auMoinsUne) -> EtatPermission.Accordee
        rationale() -> EtatPermission.RefuseeUneFois
        liste.any { it in demandees } -> EtatPermission.RefuseeDefinitivement
        else -> EtatPermission.NonDemandee
    }

    var lanceA by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultat ->
        Log.d("Aplomb", "Permission ${liste.joinToString()} → $resultat")
        val sansDialogue = SystemClock.elapsedRealtime() - lanceA < 400
        if (if (auMoinsUne) resultat.values.any { it } else resultat.values.all { it }) {
            onAccordee()
        } else if (rationale() || sansDialogue) {
            // Un premier refus (rationale = true) : on le note. Au refus suivant, rationale repasse à false
            // et seul ce drapeau dit « définitif ». Un dialogue simplement fermé ne laisse pas de trace :
            // rationale reste false, le drapeau n'est pas posé, l'app pourra redemander.
            // Réponse instantanée = l'OS a refusé sans même afficher le dialogue : c'est déjà définitif.
            scope.launch { reglages.marquerDemandee(liste) }
        }
        version++
    }

    // Retour des réglages, révocation pendant que l'app était cachée, « Une fois » expirée : on relit l'état réel.
    LifecycleResumeEffect(liste) {
        version++
        onPauseOrDispose { }
    }
    // Accordée = l'historique des refus ne compte plus : on efface le drapeau (sur le disque, car une révocation
    // dans les réglages tue le process). Révoquée plus tard, elle redevient « jamais demandée » : l'app redemande proprement.
    LaunchedEffect(version, demandees) {
        if (context.accordee(liste, auMoinsUne) && liste.any { it in demandees }) reglages.oublierDemande(liste)
    }

    val etat = remember(version, demandees) { calculer() }

    return PermissionFlow(
        etat = etat,
        demander = {
            when (etat) {
                EtatPermission.Accordee -> onAccordee()
                EtatPermission.RefuseeDefinitivement -> Unit
                else -> {
                    lanceA = SystemClock.elapsedRealtime()
                    launcher.launch(liste.toTypedArray())
                }
            }
        },
        ouvrirReglages = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}
