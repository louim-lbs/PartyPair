package fr.boitedefete.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * L'element signature : un cone de haut-parleur.
 *
 * Au repos il est eteint. Pendant la sequence la membrane respire.
 * L'anneau exterieur se remplit d'orange au fil des etapes.
 */
@Composable
fun DriverButton(
    progress: Float,
    active: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // L'animation tourne en permanence : la rendre conditionnelle romprait
    // les regles de composition. On annule seulement son amplitude au repos.
    val transition = rememberInfiniteTransition(label = "membrane")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "excursion"
    )
    // Battement lent une fois la paire etablie : l'enceinte "respire" au repos.
    val glow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )

    val ready = progress >= 1f
    val breathe = if (active && !reducedMotion) pulse else 0f
    val halo = if (ready && !reducedMotion) glow else if (ready) 1f else 0f

    val sweep by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "anneau"
    )

    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .size(260.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        val r = size.minDimension / 2f
        val c = center

        // Saladier
        drawCircle(color = Party.Grille, radius = r, center = c)
        drawCircle(color = Party.Edge, radius = r, center = c, style = Stroke(width = 2f))

        // Suspension
        drawCircle(
            color = Party.Edge,
            radius = r * (0.80f + breathe * 0.015f),
            center = c,
            style = Stroke(width = 10f)
        )
        drawCircle(color = Party.Edge, radius = r * 0.62f, center = c, style = Stroke(width = 3f))

        // Halo de veille active : signale que la paire stereo tient
        if (halo > 0f) {
            drawCircle(
                color = accent.copy(alpha = 0.05f + halo * 0.10f),
                radius = r * (0.93f + halo * 0.05f),
                center = c
            )
        }

        // Anneau de progression
        if (sweep > 0f) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(accent, accent.copy(alpha = 0.35f), accent),
                    center = c
                ),
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.92f, c.y - r * 0.92f),
                size = Size(r * 1.84f, r * 1.84f),
                style = Stroke(width = 6f)
            )
        }

        // Membrane
        drawCircle(color = Party.Cabinet, radius = r * (0.60f - breathe * 0.01f), center = c)

        // Dome central : le seul element qui s'allume
        drawCircle(
            color = if (active || ready) accent else Party.Edge,
            radius = r * (0.26f + breathe * 0.02f + halo * 0.03f),
            center = c
        )
    }
}
