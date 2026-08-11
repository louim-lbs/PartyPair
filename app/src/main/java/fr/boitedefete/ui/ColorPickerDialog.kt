package fr.boitedefete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.boitedefete.R

/**
 * Choix d'une teinte libre, dans une fenetre.
 *
 * La roue occupe beaucoup de place : la presenter en surimpression evite de
 * repousser tout le reste des reglages vers le bas.
 */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    // Choix provisoire : rien n'est retenu tant que la validation n'a pas eu lieu.
    var picked by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Party.Grille, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Text(
                stringResource(R.string.accent_custom).uppercase(),
                style = Silkscreen.copy(fontSize = 13.sp),
                color = LocalAccent.current
            )
            Spacer(Modifier.height(16.dp))

            ColorWheel(initial = initial, onPick = { picked = it })

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.cancel).uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = Party.Muted
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onConfirm(picked) }) {
                    Text(
                        stringResource(R.string.confirm).uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = picked
                    )
                }
            }
        }
    }
}
