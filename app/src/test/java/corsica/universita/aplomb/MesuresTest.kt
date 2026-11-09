package corsica.universita.aplomb

import corsica.universita.aplomb.capteurs.Etalonnage
import corsica.universita.aplomb.capteurs.LectureNiveau
import corsica.universita.aplomb.capteurs.Vec3
import corsica.universita.aplomb.capteurs.cardinal
import corsica.universita.aplomb.capteurs.ecartAngulaire
import corsica.universita.aplomb.capteurs.ecartCap
import corsica.universita.aplomb.capteurs.etalonner
import corsica.universita.aplomb.capteurs.fmt
import corsica.universita.aplomb.capteurs.hautDepuisGravite
import corsica.universita.aplomb.capteurs.lireNiveau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Les calculs portés de app.js doivent donner les mêmes résultats que la PWA. */
class MesuresTest {

    @Test
    fun aPlatHorizontal() {
        val l = lireNiveau(hautDepuisGravite(0f, 0f, 9.81f, 0), Etalonnage())
        assertTrue(l is LectureNiveau.Plat)
        assertEquals(0.0, l.valeur, 1e-9)
        assertTrue(l.deNiveau)
    }

    @Test
    fun aPlatInclineDe2DegresAGauche() {
        // Bord droit plus bas de 2° : le « haut » penche vers −x, la bulle part à gauche (côté le plus haut).
        val a = 2 * PI / 180
        val l = lireNiveau(hautDepuisGravite((-9.81 * sin(a)).toFloat(), 0f, (9.81 * cos(a)).toFloat(), 0), Etalonnage()) as LectureNiveau.Plat
        assertEquals(-2.0, l.x, 1e-3)
        assertEquals(0.0, l.y, 1e-3)
        assertEquals("−2,0", fmt(l.x))
    }

    @Test
    fun surChantRameneALaVerticaleLaPlusProche() {
        val a = 3 * PI / 180 // debout, tourné de 3°
        val l = lireNiveau(Vec3(sin(a), cos(a), 0.0), Etalonnage()) as LectureNiveau.Chant
        assertEquals(0, l.cran)
        assertEquals(3.0, l.valeur, 1e-6)
        val couche = lireNiveau(Vec3(1.0, 0.02, 0.0), Etalonnage()) as LectureNiveau.Chant
        assertEquals(90, couche.cran)
    }

    @Test
    fun etalonnageCumule() {
        val haut = Vec3(-0.05, 0.03, 1.0)
        val e1 = etalonner(lireNiveau(haut, Etalonnage()), Etalonnage())
        assertEquals(0.0, lireNiveau(haut, e1).valeur, 1e-9)
        // Réétalonner à la même position ne change rien.
        assertEquals(e1, etalonner(lireNiveau(haut, e1), e1).let { e -> e.copy(platX = e.platX + 0.0) })
    }

    @Test
    fun boussole() {
        assertEquals(2.0, ecartCap(1.0, 359.0), 1e-9) // 359° → 1° = +2°, pas −358°
        assertEquals(-2.0, ecartCap(359.0, 1.0), 1e-9)
        assertEquals("N", cardinal(359))
        assertEquals("SE", cardinal(135))
        assertEquals("O", cardinal(270))
    }

    @Test
    fun formatage() {
        assertEquals("0,0", fmt(-0.04)) // « −0,0 » ramené à « 0,0 »
        assertEquals("1,3", fmt(1.25))
        assertEquals("−12,5", fmt(-12.46))
    }

    @Test
    fun ecartDeSurveillance() {
        val a = 1.5 * PI / 180
        assertEquals(1.5, ecartAngulaire(Vec3(0.0, 0.0, 1.0), Vec3(sin(a), 0.0, cos(a))), 1e-6)
    }
}
