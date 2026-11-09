package corsica.universita.aplomb.capteurs

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Les calculs du niveau et de la boussole, repris ligne à ligne de app.js (PWA) : mêmes constantes,
 * mêmes formules. Du Kotlin pur, sans Android : testé par MesuresTest (src/test).
 */

const val LISSAGE = 0.15      // part de la nouvelle mesure dans la valeur affichée (filtre passe-bas)
const val ANGLE_MAX = 10.0    // écart, en degrés, qui amène la bulle en butée
const val SEUIL_NIVEAU = 0.5  // « de niveau » en dessous de 0,5°
private const val RAD = PI / 180

/** Le vecteur « haut » (opposé à la gravité) dans le repère de l'écran. À plat : (0, 0, 1) ; debout : (0, 1, 0). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun norme() = sqrt(x * x + y * y + z * z)
    fun normalise(): Vec3 = norme().let { n -> if (n == 0.0) this else Vec3(x / n, y / n, z / n) }
    infix fun scal(o: Vec3) = x * o.x + y * o.y + z * o.z
}

data class Etalonnage(val platX: Double = 0.0, val platY: Double = 0.0, val chant: Double = 0.0)

sealed interface LectureNiveau {
    val valeur: Double

    /** À plat : pente de chaque axe de l'écran par rapport à l'horizontale. */
    data class Plat(val x: Double, val y: Double, override val valeur: Double) : LectureNiveau

    /** Sur chant : rotation dans le plan de l'écran, ramenée à l'horizontale ou la verticale la plus proche. */
    data class Chant(val cran: Int, override val valeur: Double) : LectureNiveau

    val deNiveau: Boolean get() = abs(valeur) < SEUIL_NIVEAU
}

/**
 * La gravité mesurée par Android (TYPE_GRAVITY, ou l'accéléromètre) pointe déjà « vers le haut » au repos :
 * (0, 0, +9,81) à plat. On la normalise, puis on la tourne de l'angle d'affichage (0 en portrait),
 * comme app.js le faisait avec screen.orientation.angle.
 */
fun hautDepuisGravite(gx: Float, gy: Float, gz: Float, rotationEcranDeg: Int): Vec3 {
    val v = Vec3(gx.toDouble(), gy.toDouble(), gz.toDouble()).normalise()
    val a = rotationEcranDeg * RAD
    return Vec3(v.x * cos(a) - v.y * sin(a), v.x * sin(a) + v.y * cos(a), v.z)
}

fun lisser(ancien: Vec3?, nouveau: Vec3): Vec3 = ancien?.let {
    Vec3(it.x + (nouveau.x - it.x) * LISSAGE, it.y + (nouveau.y - it.y) * LISSAGE, it.z + (nouveau.z - it.z) * LISSAGE)
} ?: nouveau

/** lectureNiveau() de app.js : la mesure courante, étalonnage déduit. */
fun lireNiveau(haut: Vec3, e: Etalonnage): LectureNiveau {
    if (abs(haut.z) > 0.7) {
        val x = atan2(haut.x, abs(haut.z)) / RAD - e.platX
        val y = atan2(haut.y, abs(haut.z)) / RAD - e.platY
        return LectureNiveau.Plat(x, y, hypot(x, y))
    }
    val angle = atan2(haut.x, haut.y) / RAD
    val cran = 90 * (angle / 90).roundToInt()
    return LectureNiveau.Chant(cran, angle - cran - e.chant)
}

/** etalonner() de app.js : la lecture est déjà corrigée de l'étalonnage précédent, on cumule. */
fun etalonner(lecture: LectureNiveau, e: Etalonnage): Etalonnage = when (lecture) {
    is LectureNiveau.Plat -> e.copy(platX = e.platX + lecture.x, platY = e.platY + lecture.y)
    is LectureNiveau.Chant -> e.copy(chant = e.chant + lecture.valeur)
}

/** Angle, en degrés, entre deux directions « haut » : ce que surveille le service d'aplomb. */
fun ecartAngulaire(a: Vec3, b: Vec3): Double =
    kotlin.math.acos((a.normalise() scal b.normalise()).coerceIn(-1.0, 1.0)) / RAD

// ---------------------------------------------------------------- Boussole

fun normaliserCap(degres: Double) = ((degres % 360) + 360) % 360

/** Plus court chemin entre deux caps (359° → 1° = +2°, pas −358°), sinon la rose ferait un tour complet. */
fun ecartCap(mesure: Double, cap: Double) = ((mesure - cap + 540) % 360) - 180

private val CARDINAUX = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
fun cardinal(degres: Int) = CARDINAUX[(degres / 45.0).roundToInt() % 8]

// ---------------------------------------------------------------- Affichage

/** fmt() de app.js : 1 décimale, virgule française, vrai signe moins ; « −0,0 » est ramené à « 0,0 ». */
fun fmt(n: Double): String {
    val arrondi = (n * 10).roundToLong() / 10.0
    val texte = String.format(java.util.Locale.ROOT, "%.1f", if (arrondi == 0.0) 0.0 else arrondi)
    return texte.replace('.', ',').replace('-', '−')
}

fun arrondi1(n: Double) = (n * 10).roundToLong() / 10.0
