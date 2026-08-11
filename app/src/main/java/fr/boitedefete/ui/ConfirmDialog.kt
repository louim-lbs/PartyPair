package fr.boitedefete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.boitedefete.R

/**
 * Confirmation d'une action qu'un appui accidentel rendrait desagreable.
 * La case a cocher permet de s'en passer une fois l'habitude prise.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dontAsk by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Party.Grille, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(title, style = Silkscreen.copy(fontSize = 14.sp), color = LocalAccent.current)
            Spacer(Modifier.height(10.dp))
            Text(body, style = Body, color = Party.Silkscreen)

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dontAsk = !dontAsk },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                Text(
                    stringResource(R.string.dont_ask_again),
                    style = Body.copy(fontSize = 14.sp),
                    color = Party.Muted
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        dismissLabel.uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = Party.Muted
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onConfirm(dontAsk) }) {
                    Text(
                        confirmLabel.uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = LocalAccent.current
                    )
                }
            }
        }
    }
}
