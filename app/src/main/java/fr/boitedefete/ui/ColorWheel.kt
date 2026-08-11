package fr.boitedefete.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.boitedefete.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Choix libre d'une teinte.
 *
 * La roue donne teinte et saturation d'un seul geste — l'angle pour l'une, la
 * distance au centre pour l'autre. La luminosite garde son curseur, et le champ
 * hexadecimal permet de coller une valeur precise ou de relever la sienne.
 */
@Composable
fun ColorWheel(
    initial: Color,
    onPick: (Color) -> Unit
) {
    val start = remember(initial) { initial.toHsv() }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(initial.toHex()) }

    fun publish() {
        val color = hsvToColor(hue, saturation, value)
        hex = color.toHex()
        onPick(color)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(160.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            pick(offset, size.width, size.height)?.let { (h, s) ->
                                hue = h; saturation = s; publish()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            pick(change.position, size.width, size.height)?.let { (h, s) ->
                                hue = h; saturation = s; publish()
                            }
                        }
                    }
            ) {
                val radius = size.minDimension / 2f
                val center = center

                // Teinte sur le pourtour, blanchie vers le centre : la saturation
                // se lit comme une distance, geste naturel sur une roue.
                drawCircle(
                    brush = Brush.sweepGradient(
                        (0..360 step 30).map { hsvToColor(it.toFloat(), 1f, 1f) },
                        center = center
                    ),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White, Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )

                // Reperage de la selection
                val angle = Math.toRadians(hue.toDouble())
                val marker = Offset(
                    center.x + (cos(angle) * saturation * radius).toFloat(),
                    center.y + (sin(angle) * saturation * radius).toFloat()
                )
                drawCircle(Color.White, radius = 9f, center = marker, style = Stroke(width = 3f))
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(hsvToColor(hue, saturation, value), CircleShape)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.accent_brightness),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )
        Slider(
            value = value,
            onValueChange = { value = it; publish() },
            valueRange = 0.25f..1f
        )

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { typed ->
                hex = normalizeHex(typed)
                parseHex(hex)?.let { color ->
                    val hsv = color.toHsv()
                    hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                    onPick(color)
                }
            },
            label = { Text(stringResource(R.string.accent_hex), color = Party.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Convertit un point de la roue en teinte et saturation, ou null si hors du disque. */
private fun pick(offset: Offset, width: Int, height: Int): Pair<Float, Float>? {
    val radius = minOf(width, height) / 2f
    val dx = offset.x - width / 2f
    val dy = offset.y - height / 2f
    val distance = hypot(dx, dy)
    if (distance > radius) return null
    val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
    return angle.toFloat() to (distance / radius).coerceIn(0f, 1f)
}

/** Ne conserve que six chiffres hexadecimaux, precedes d'un croisillon. */
internal fun normalizeHex(input: String): String =
    "#" + input.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        .uppercase()
        .take(6)

internal fun parseHex(hex: String): Color? {
    val digits = hex.removePrefix("#")
    if (digits.length != 6) return null
    return runCatching { Color(digits.toLong(16) or 0xFF000000L) }.getOrNull()
}

internal fun Color.toHex(): String =
    "#%02X%02X%02X".format(
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt()
    )

/** Teinte en degres, saturation et luminosite entre 0 et 1. */
internal fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt(),
        out
    )
    return out
}

internal fun hsvToColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
