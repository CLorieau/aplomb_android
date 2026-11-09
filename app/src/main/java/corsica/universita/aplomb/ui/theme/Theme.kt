package corsica.universita.aplomb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Les variables CSS de styles.css (:root), une à une : --fond, --surface, --texte… */
@Immutable
data class Couleurs(
    val fond: Color,
    val surface: Color,
    val texte: Color,
    val texteDoux: Color,
    val bordure: Color,
    val accent: Color,
    val accentTexte: Color,
    val ok: Color,
    val alerte: Color,
    val erreur: Color,
    val bulle: Color,
)

private val Clair = Couleurs(
    fond = Color(0xFFF8FAFC), surface = Color(0xFFFFFFFF), texte = Color(0xFF0F172A), texteDoux = Color(0xFF475569),
    bordure = Color(0xFFCBD5E1), accent = Color(0xFF0F766E), accentTexte = Color(0xFFFFFFFF), ok = Color(0xFF16A34A),
    alerte = Color(0xFFB45309), erreur = Color(0xFFB91C1C), bulle = Color(0xFFA3E635),
)

// @media (prefers-color-scheme: dark)
private val Sombre = Couleurs(
    fond = Color(0xFF0B1220), surface = Color(0xFF131C2E), texte = Color(0xFFE2E8F0), texteDoux = Color(0xFF94A3B8),
    bordure = Color(0xFF334155), accent = Color(0xFF14B8A6), accentTexte = Color(0xFF042F2E), ok = Color(0xFF4ADE80),
    alerte = Color(0xFFFBBF24), erreur = Color(0xFFF87171), bulle = Color(0xFFA3E635),
)

private val LocalCouleurs = staticCompositionLocalOf { Clair }

object Aplomb {
    val couleurs: Couleurs
        @Composable @ReadOnlyComposable get() = LocalCouleurs.current
}

@Composable
fun AplombTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) Sombre else Clair
    val schema = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = c.accent, onPrimary = c.accentTexte, background = c.fond, surface = c.surface, onSurface = c.texte, onBackground = c.texte, outline = c.bordure, error = c.erreur)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.accentTexte, background = c.fond, surface = c.surface, onSurface = c.texte, onBackground = c.texte, outline = c.bordure, error = c.erreur)
    }
    CompositionLocalProvider(LocalCouleurs provides c) {
        MaterialTheme(colorScheme = schema, content = content)
    }
}
