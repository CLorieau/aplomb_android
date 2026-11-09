package corsica.universita.aplomb.ui.composants

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import corsica.universita.aplomb.ui.theme.Aplomb

/** Les classes de styles.css, en composables : .carte, .bouton-principal, .bouton-discret, .pastille, .bandeau… */

private val Rayon = RoundedCornerShape(8.dp)      // 0.5rem
private val RayonCarte = RoundedCornerShape(10.dp) // 0.6rem

@Composable
fun Titre2(texte: String, modifier: Modifier = Modifier) =
    Text(texte, modifier.padding(top = 16.dp, bottom = 12.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Aplomb.couleurs.texte)

@Composable
fun Titre3(texte: String, modifier: Modifier = Modifier, enTete: Boolean = false) =
    Text(texte, modifier.padding(top = if (enTete) 0.dp else 20.dp, bottom = 4.dp), fontSize = 16.8.sp, fontWeight = FontWeight.Bold, color = Aplomb.couleurs.texte)

@Composable
fun Paragraphe(texte: String, modifier: Modifier = Modifier, couleur: Color = Aplomb.couleurs.texte, taille: TextUnit = 16.sp) =
    Text(texte, modifier, fontSize = taille, lineHeight = taille * 1.5f, color = couleur)

@Composable
fun Message(texte: String, modifier: Modifier = Modifier, couleur: Color = Aplomb.couleurs.texteDoux) =
    Paragraphe(texte, modifier.padding(vertical = 8.dp), couleur)

@Composable
fun Carte(modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) = Column(
    modifier
        .fillMaxWidth()
        .background(Aplomb.couleurs.surface, RayonCarte)
        .border(1.dp, Aplomb.couleurs.bordure, RayonCarte)
        .padding(16.dp),
    content = contenu,
)

@Composable
fun BoutonPrincipal(texte: String, onClick: () -> Unit, modifier: Modifier = Modifier, actif: Boolean = true) {
    val c = Aplomb.couleurs
    Button(
        onClick, modifier.heightIn(min = 44.dp), enabled = actif, shape = Rayon,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent, contentColor = c.accentTexte,
            disabledContainerColor = c.accent.copy(alpha = 0.5f), disabledContentColor = c.accentTexte.copy(alpha = 0.8f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) { Text(texte, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun Bouton(texte: String, onClick: () -> Unit, modifier: Modifier = Modifier, actif: Boolean = true, taille: TextUnit = 16.sp) {
    val c = Aplomb.couleurs
    OutlinedButton(
        onClick, modifier.heightIn(min = 44.dp), enabled = actif, shape = Rayon,
        border = BorderStroke(1.dp, c.bordure),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = c.surface, contentColor = c.texte, disabledContainerColor = c.surface, disabledContentColor = c.texte.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) { Text(texte, fontSize = taille) }
}

@Composable
fun BoutonDiscret(texte: String, onClick: () -> Unit, modifier: Modifier = Modifier, actif: Boolean = true) {
    val c = Aplomb.couleurs
    TextButton(onClick, modifier.heightIn(min = 32.dp), enabled = actif, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(texte, color = if (actif) c.accent else c.accent.copy(alpha = 0.5f), textDecoration = TextDecoration.Underline, fontSize = 16.sp)
    }
}

/** .pastille : « En ligne » en vert, « Hors ligne » en orange. */
@Composable
fun Pastille(texte: String, couleur: Color, modifier: Modifier = Modifier, taille: TextUnit = 12.8.sp) = Text(
    texte,
    modifier
        .border(1.dp, couleur, RoundedCornerShape(50))
        .padding(horizontal = 9.dp, vertical = 1.dp),
    color = couleur, fontSize = taille, maxLines = 1,
)

@Composable
fun Bandeau(modifier: Modifier = Modifier, bordure: Color = Aplomb.couleurs.alerte, contenu: @Composable RowScope.() -> Unit) = Row(
    modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
        .border(1.dp, bordure, Rayon)
        .padding(horizontal = 13.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
    verticalAlignment = Alignment.CenterVertically,
    content = contenu,
)

/** .actions : une rangée qui passe à la ligne. */
@Composable
fun Actions(modifier: Modifier = Modifier, arrangement: Arrangement.Horizontal = Arrangement.SpaceBetween, contenu: @Composable RowScope.() -> Unit) = Row(
    modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
    horizontalArrangement = arrangement,
    verticalAlignment = Alignment.CenterVertically,
    content = contenu,
)

/** .saisie : le champ « Note (facultatif) » et le bouton Enregistrer. maxlength="140" comme dans la PWA. */
@Composable
fun Saisie(note: String, onNote: (String) -> Unit, indication: String, actif: Boolean, onEnregistrer: () -> Unit) {
    val c = Aplomb.couleurs
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = note,
            onValueChange = { onNote(it.take(140)) },
            singleLine = true,
            textStyle = TextStyle(color = c.texte, fontSize = 16.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (actif) onEnregistrer() }),
            modifier = Modifier.weight(1f),
            decorationBox = { champ ->
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .background(c.surface, Rayon)
                        .border(1.dp, c.bordure, Rayon)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (note.isEmpty()) Text(indication, color = c.texteDoux, fontSize = 16.sp, maxLines = 1)
                    champ()
                }
            },
        )
        BoutonPrincipal("Enregistrer", onEnregistrer, actif = actif)
    }
}

/** .option : une case à cocher et son libellé. */
@Composable
fun Option(texte: String, coche: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Checkbox(coche, onChange, colors = CheckboxDefaults.colors(checkedColor = Aplomb.couleurs.accent))
    Text(texte, color = Aplomb.couleurs.texteDoux, fontSize = 14.4.sp)
}

/** .lecture : le grand chiffre, en chiffres tabulaires. */
@Composable
fun Lecture(texte: String, couleur: Color = Aplomb.couleurs.texte) = Text(
    texte, Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center,
    fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, color = couleur,
    style = TextStyle(fontFeatureSettings = "tnum"),
)

@Composable
fun LectureDetail(texte: String, couleur: Color = Aplomb.couleurs.texteDoux) =
    Text(texte, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = couleur, fontSize = 14.4.sp)

/** #toast : le message en bas de l'écran, 3,5 s. */
@Composable
fun BoxScope.Toast(texte: String?) {
    val c = Aplomb.couleurs
    AnimatedVisibility(
        texte != null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
        enter = fadeIn(), exit = fadeOut(),
    ) {
        Text(
            texte.orEmpty(),
            Modifier.widthIn(max = 520.dp).background(c.texte, Rayon).padding(horizontal = 16.dp, vertical = 10.dp),
            color = c.fond, fontSize = 14.4.sp,
        )
    }
}
