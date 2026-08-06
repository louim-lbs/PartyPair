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
    fontSize = 12.sp,
    letterSpacing = 3.sp
)

val Display = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 28.sp,
    letterSpacing = 6.sp
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
