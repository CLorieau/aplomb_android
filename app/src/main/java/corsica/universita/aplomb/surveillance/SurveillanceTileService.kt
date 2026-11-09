package corsica.universita.aplomb.surveillance

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import corsica.universita.aplomb.MainActivity
import corsica.universita.aplomb.R
import corsica.universita.aplomb.notif.Notifications

/**
 * Une tuile dans les réglages rapides (le volet qu'on tire du haut de l'écran) : autre intégration
 * système qu'aucune PWA ne peut offrir. Allumée = une surveillance tourne ; un appui l'arrête.
 * Éteinte : un appui ouvre Aplomb directement sur le niveau, pour en lancer une.
 */
class SurveillanceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val active = SurveillanceService.etat.value is EtatSurveillance.Active
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) {
                subtitle = getString(if (active) R.string.tuile_active else R.string.tuile_inactive)
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (SurveillanceService.etat.value is EtatSurveillance.Active) {
            SurveillanceService.arreter(this)
            return
        }
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Notifications.EXTRA_ONGLET, "niveau")
            .putExtra(Notifications.EXTRA_SOURCE, "tuile")
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
