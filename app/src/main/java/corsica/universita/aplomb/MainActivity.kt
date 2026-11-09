package corsica.universita.aplomb

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import corsica.universita.aplomb.notif.Notifications
import corsica.universita.aplomb.ui.AplombNavigation
import corsica.universita.aplomb.ui.AplombViewModel
import corsica.universita.aplomb.ui.theme.AplombTheme

/**
 * L'unique Activity. L'OS la lance sur :
 * - MAIN / LAUNCHER (l'icône) ;
 * - VIEW https://<PWA_HOST>/… (App Link vérifié : l'URL de la PWA ouvre l'app, #niveau / #releves… choisit l'onglet) ;
 * - SEND text/plain (« Partager » depuis une autre app : le texte préremplit la note) ;
 * - le clic d'une notification, d'un raccourci de l'icône ou de la tuile (extras ONGLET / RELEVE_ID).
 */
class MainActivity : ComponentActivity() {

    private val vm: AplombViewModel by viewModels { AplombViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // avant super.onCreate : l'écran de démarrage, puis Theme.Aplomb.Main
        super.onCreate(savedInstanceState)
        // Seulement au premier lancement : après une rotation, le même intent serait retraité.
        if (savedInstanceState == null) traiter(intent)
        enableEdgeToEdge()
        setContent {
            AplombTheme { AplombNavigation(vm) }
        }
    }

    // launchMode="singleTop" : Aplomb déjà affiché, l'intent arrive ici au lieu d'empiler une seconde Activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        traiter(intent)
    }

    override fun onStart() {
        super.onStart()
        app.auPremierPlan = true
    }

    override fun onStop() {
        app.auPremierPlan = false
        super.onStop()
    }

    private fun traiter(intent: Intent) {
        mesurerOuverture(intent)
        // Deux origines pour un clic de notification :
        // - nos notifications (PendingIntent construit par Notifications.ouvrir) : extras préfixés corsica.universita.aplomb.* ;
        // - un message FCM « notification » (console Firebase), app en arrière-plan ou tuée : c'est la bibliothèque
        //   Firebase qui affiche la notification, et au clic elle lance MainActivity avec les données du message
        //   en extras, sous leur nom brut (« onglet », « releveId »). Sans cette lecture, le clic ouvrirait l'app
        //   sur le dernier écran, pas sur le bon.
        (intent.getStringExtra(Notifications.EXTRA_RELEVE_ID) ?: intent.getStringExtra(FCM_RELEVE_ID))?.let {
            Log.d(TAG, "Ouvert sur le relevé $it (notification)")
            vm.demanderReleve(it)
            return
        }
        (intent.getStringExtra(Notifications.EXTRA_ONGLET) ?: intent.getStringExtra(FCM_ONGLET))?.let {
            Log.d(TAG, "Ouvert sur l'onglet $it")
            vm.choisirOnglet(it)
            return
        }
        when (intent.action) {
            // TD A3 : « Partager » depuis une autre app (Chrome, Notes…) → le texte préremplit la note.
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                Log.d(TAG, "ACTION_SEND reçu (partage depuis une autre app)")
                vm.recevoirTexte(it)
            }
            // TD A3 : App Link vérifié. Les mêmes adresses que la PWA : https://<pwa>/#releves ouvre l'onglet Relevés.
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                Log.d(TAG, "ACTION_VIEW reçu (App Link) : $uri")
                uri.fragment?.let(vm::choisirOnglet)
            }
        }
    }

    /**
     * Analytics — rétention : chaque ouverture, et par où (icône, notification, App Link, partage, tuile, raccourci).
     * Une notification ouverte compte aussi à part (notification_opened) : les notifications font-elles revenir ?
     * Pas compté à la rotation : traiter() n'est appelé qu'au premier lancement et dans onNewIntent.
     */
    private fun mesurerOuverture(intent: Intent) {
        val source = intent.getStringExtra(Notifications.EXTRA_SOURCE) ?: when {
            // Une notification affichée par Firebase porte l'identifiant du message FCM.
            intent.hasExtra(FCM_MESSAGE_ID) -> "notification:push"
            intent.action == Intent.ACTION_VIEW -> "app_link"
            intent.action == Intent.ACTION_SEND -> "partage"
            else -> "icone"
        }
        app.mesure.evenement("app_opened", "/", "source" to source.substringBefore(':'))
        if (source.startsWith("notification:")) {
            app.mesure.evenement("notification_opened", "/", "type" to source.substringAfter(':'))
        }
    }

    private companion object {
        const val TAG = "Aplomb"

        // Les clés des « données personnalisées » d'un message FCM (console Firebase › Options supplémentaires).
        const val FCM_ONGLET = "onglet"
        const val FCM_RELEVE_ID = "releveId"
        const val FCM_MESSAGE_ID = "google.message_id"
    }
}
