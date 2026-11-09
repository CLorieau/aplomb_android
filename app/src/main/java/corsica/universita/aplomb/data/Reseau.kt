package corsica.universita.aplomb.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * La pastille « En ligne / Hors ligne ». Là où la PWA lisait navigator.onLine (« en ligne » sur le Wi-Fi
 * d'un hôtel sans Internet), on écoute NET_CAPABILITY_VALIDATED : l'OS a vérifié l'accès à Internet.
 * Sert à l'affichage ; la décision d'envoyer, c'est la contrainte réseau de WorkManager.
 */
class Reseau(context: Context, scope: CoroutineScope) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun valide(capacites: NetworkCapabilities?) =
        capacites?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun maintenant() = valide(cm.getNetworkCapabilities(cm.activeNetwork))

    val enLigne: StateFlow<Boolean> = callbackFlow {
        val rappel = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capacites: NetworkCapabilities) {
                trySend(valide(capacites))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(maintenant())
        cm.registerDefaultNetworkCallback(rappel)
        awaitClose { cm.unregisterNetworkCallback(rappel) }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, maintenant())
}
