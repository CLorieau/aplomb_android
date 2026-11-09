package corsica.universita.aplomb.position

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Un relevé de position, et la déclinaison magnétique qui en découle à cet endroit, aujourd'hui. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val precisionM: Float,
    val precise: Boolean,
    /** Écart entre nord magnétique et nord géographique, en degrés (positif vers l'est). */
    val declinaison: Double,
)

/**
 * La position, pour corriger la boussole. La PWA donnait le nord magnétique « sans correction de déclinaison »
 * (DOC-TECHNIQUE, limites) ; Android fournit le modèle magnétique mondial (GeomagneticField) : il ne manque
 * que l'endroit. Position précise ou approximative : les deux suffisent (la déclinaison varie sur des centaines de km).
 */
class Position(context: Context) {
    private val ctx = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(ctx)

    private fun accordee(permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    /** null : ni précise ni approximative accordée, ou aucune position disponible (GPS coupé…). */
    @SuppressLint("MissingPermission") // vérifiée juste en dessous
    suspend fun actuelle(): Fix? {
        val fine = accordee(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = accordee(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return null
        // La précision demandée suit ce que l'utilisateur a accordé.
        val priorite = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val annulation = CancellationTokenSource()
        val loc: Location = suspendCancellableCoroutine<Location?> { cont ->
            client.getCurrentLocation(priorite, annulation.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { annulation.cancel() } // coroutine annulée → demande annulée
        } ?: return null
        val champ = GeomagneticField(
            loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), System.currentTimeMillis(),
        )
        return Fix(loc.latitude, loc.longitude, loc.accuracy, precise = fine, declinaison = champ.declination.toDouble())
    }
}
