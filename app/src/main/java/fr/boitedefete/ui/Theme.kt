package fr.boitedefete.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette tiree de l'objet lui-meme : caisson noir mat, grille sombre,
 * serigraphie creme, badge orange.
 */
object Party {
    val Cabinet = Color(0xFF050505)
    val Grille = Color(0xFF141414)
    val Edge = Color(0xFF262626)
    val Orange = Color(0xFFFF6B00)
    val Silkscreen = Color(0xFFE8E4DF)
    val Muted = Color(0xFF6E6A66)
}

/** Serigraphie de facade : capitales, tres espacees, petites tailles. */
val Silkscreen = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    letterSpacing = 2.5.sp,
    lineHeight = 22.sp
)

/** Variante courante pour les textes explicatifs, sans espacement force. */
val Body = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp
)

val Display = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 30.sp,
    letterSpacing = 5.sp
)

@Composable
fun BoiteDeFeteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Party.Orange,
            background = Party.Cabinet,
            surface = Party.Grille,
            onBackground = Party.Silkscreen,
            onSurface = Party.Silkscreen
        ),
        typography = Typography(),
        content = content
    )
}
