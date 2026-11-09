package corsica.universita.aplomb.ui.ecrans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import corsica.universita.aplomb.ui.composants.Paragraphe
import corsica.universita.aplomb.ui.composants.Titre2
import corsica.universita.aplomb.ui.composants.Titre3
import corsica.universita.aplomb.ui.theme.Aplomb

/** <section id="guide"> : le même guide que la PWA, complété de ce que seule l'app sait faire. */
@Composable
fun GuideEcran(choixMesure: @Composable () -> Unit = {}) {
    Titre2("Guide")
    Titre3("À quoi sert Aplomb ?")
    Paragraphe(
        "Aplomb transforme un téléphone en deux outils de terrain : un niveau à bulle pour vérifier qu'une surface est horizontale ou verticale, " +
            "et une boussole pour relever une direction. L'application fonctionne sans connexion, sur un chantier comme en montagne.",
    )
    Titre3("Utiliser le niveau à bulle")
    Puce("À plat", " : posez le téléphone écran vers le haut sur la surface. La bulle se déplace vers le côté le plus haut ; elle est au centre quand la surface est horizontale.")
    Puce("Sur chant", " : posez le téléphone sur la tranche, comme un niveau de maçon. L'écart affiché est mesuré par rapport à l'horizontale ou à la verticale la plus proche.")
    Puce("Étalonner", " : posez le téléphone sur une surface de référence et appuyez sur « Étalonner » pour compenser le décalage du capteur ou de la coque.")
    Paragraphe("Le niveau est considéré comme atteint en dessous de 0,5° d'écart.", Modifier.padding(top = 8.dp))
    Titre3("Surveiller un aplomb, écran éteint")
    Paragraphe(
        "Sous le niveau, « Surveiller cet aplomb » garde la position actuelle comme référence, puis vous alerte (notification et vibration) " +
            "dès que l'inclinaison s'en écarte de plus que le seuil choisi, même écran éteint ou application fermée. " +
            "Une notification permanente indique que la surveillance tourne ; elle s'arrête d'elle-même après 2 heures. " +
            "Elle se lance aussi depuis la tuile « Aplomb » des réglages rapides.",
    )
    Titre3("Utiliser la boussole")
    Puce(null, "Tenez le téléphone à plat : le cap affiché est la direction pointée par le haut de l'appareil, en degrés depuis le nord (0° nord, 90° est, 180° sud, 270° ouest).")
    Puce(null, "Par défaut, la boussole indique le nord magnétique. « Utiliser ma position » corrige la déclinaison magnétique du lieu et affiche le nord géographique des cartes.")
    Puce(null, "Éloignez-vous des masses métalliques, des aimants et des enceintes. Si Aplomb signale une boussole peu précise, décrivez un « 8 » dans l'air avec le téléphone.")
    Titre3("Relevés et synchronisation")
    Paragraphe(
        "Chaque mesure peut être enregistrée avec une note. Les relevés sont conservés sur l'appareil et restent consultables hors ligne. " +
            "Ceux qui ont été pris sans réseau sont marqués « En attente » et envoyés automatiquement au retour de la connexion, " +
            "même si l'application est fermée. Si vous activez les notifications, Aplomb vous prévient quand ces relevés sont arrivés.",
    )
    Titre3("Précision et limites")
    Paragraphe(
        "Les capteurs d'un téléphone donnent une mesure indicative. Aplomb ne remplace pas un instrument étalonné pour un travail qui engage la sécurité.",
    )
    Titre3("Vie privée")
    Paragraphe(
        "Aplomb ne demande ni compte ni position obligatoire. Seuls les relevés que vous enregistrez sont envoyés au serveur, rattachés à un identifiant " +
            "aléatoire propre à l'appareil ; la position, si vous l'utilisez pour la boussole, reste sur le téléphone. " +
            "Les notifications sont facultatives et se règlent depuis l'onglet Relevés.",
    )
    choixMesure()
}

@Composable
private fun Puce(gras: String?, texte: String) {
    val c = Aplomb.couleurs
    Row(Modifier.padding(start = 6.dp, top = 4.dp)) {
        Text("•  ", color = c.texte, fontSize = 16.sp)
        Column {
            Text(
                buildAnnotatedString {
                    if (gras != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(gras) }
                    append(texte)
                },
                color = c.texte, fontSize = 16.sp, lineHeight = 24.sp,
            )
        }
    }
}
