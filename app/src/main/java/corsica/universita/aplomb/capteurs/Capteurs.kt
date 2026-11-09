package corsica.universita.aplomb.capteurs

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** Un cap brut, et la précision du magnétomètre telle que l'OS l'estime (SENSOR_STATUS_ACCURACY_*). */
data class CapBrut(val degres: Double, val precision: Int)

/**
 * Les capteurs d'orientation, en Flow. Pas de permission à demander (contrairement à iOS dans la PWA) :
 * accéléromètre et magnétomètre sont des capteurs « normaux ». Un Flow froid : le capteur n'est enregistré
 * que tant qu'un écran le collecte — collectAsStateWithLifecycle l'arrête dès que l'app passe en arrière-plan.
 */
class Capteurs(context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val ecrans = context.getSystemService(DisplayManager::class.java)

    private val gravite: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotation: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometre: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val niveauDisponible get() = gravite != null
    val boussoleDisponible get() = rotation != null

    /** 0, 90, 180 ou 270 : l'équivalent de screen.orientation.angle. */
    fun rotationEcran(): Int = when (ecrans.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    /** Le vecteur « haut », non lissé. `cadence` : SENSOR_DELAY_GAME à l'écran (≈ 50 Hz), NORMAL pour le service. */
    fun haut(cadence: Int = SensorManager.SENSOR_DELAY_GAME): Flow<Vec3> {
        val capteur = gravite ?: return emptyFlow()
        return callbackFlow {
            val ecouteur = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    trySend(hautDepuisGravite(e.values[0], e.values[1], e.values[2], rotationEcran()))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sm.registerListener(ecouteur, capteur, cadence)
            awaitClose { sm.unregisterListener(ecouteur) }
        }
    }

    /**
     * Le cap magnétique de la direction pointée par le haut du téléphone, tenu à plat.
     * La PWA n'avait que l'angle alpha de deviceorientationabsolute ; ici le vecteur de rotation fusionné
     * par l'OS, et surtout la précision du magnétomètre : le web ne dit jamais « boussole à étalonner ».
     */
    fun cap(): Flow<CapBrut> {
        val capteur = rotation ?: return emptyFlow()
        return callbackFlow {
            var precision = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            val matrice = FloatArray(9)
            val angles = FloatArray(3)
            val ecouteur = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    if (e.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                    SensorManager.getRotationMatrixFromVector(matrice, e.values)
                    SensorManager.getOrientation(matrice, angles)
                    val azimut = Math.toDegrees(angles[0].toDouble()) // 0 = nord, sens horaire
                    trySend(CapBrut(normaliserCap(azimut + rotationEcran()), precision))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) precision = accuracy
                }
            }
            sm.registerListener(ecouteur, capteur, SensorManager.SENSOR_DELAY_GAME)
            magnetometre?.let { sm.registerListener(ecouteur, it, SensorManager.SENSOR_DELAY_UI) }
            awaitClose { sm.unregisterListener(ecouteur) }
        }
    }
}
