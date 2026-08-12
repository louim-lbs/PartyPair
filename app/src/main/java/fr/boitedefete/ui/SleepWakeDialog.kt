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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.boitedefete.R

/**
 * Proposee quand une minuterie est posee alors que rien ne joue.
 *
 * Programmer une extinction sur des enceintes eteintes n'a aucun sens : soit on
 * voulait d'abord les allumer, soit on s'est trompe. Mieux vaut demander que
 * lancer un decompte sans objet.
 */
@Composable
fun SleepWakeDialog(
    minutes: Int,
    onWakeAndSchedule: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Party.Grille, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                stringResource(R.string.sleep_wake_title).uppercase(),
                style = Silkscreen.copy(fontSize = 13.sp),
                color = LocalAccent.current
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.sleep_wake_body, minutes),
                style = Body,
                color = Party.Silkscreen
            )

            Spacer(Modifier.height(20.dp))
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
                TextButton(onClick = onWakeAndSchedule) {
                    Text(
                        stringResource(R.string.sleep_wake_confirm).uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = LocalAccent.current
                    )
                }
            }
        }
    }
}
