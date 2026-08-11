package fr.boitedefete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.boitedefete.R
import fr.boitedefete.SleepTimer

/** Choix de la durée avant mise en veille automatique. */
@Composable
fun SleepDialog(
    activeMinutes: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Party.Grille, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Text(
                stringResource(R.string.section_sleep).uppercase(),
                style = Silkscreen.copy(fontSize = 13.sp),
                color = LocalAccent.current
            )
            Spacer(Modifier.height(6.dp))
            Text(
                activeMinutes?.let { stringResource(R.string.sleep_in, it) }
                    ?: stringResource(R.string.sleep_none),
                style = Body.copy(fontSize = 13.sp),
                color = Party.Muted
            )
            Spacer(Modifier.height(14.dp))

            SleepTimer.CHOICES.forEach { minutes ->
                Text(
                    stringResource(R.string.sleep_minutes, minutes),
                    style = Body.copy(fontSize = 16.sp),
                    color = Party.Silkscreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(minutes) }
                        .padding(vertical = 13.dp)
                )
            }

            if (activeMinutes != null) {
                Text(
                    stringResource(R.string.sleep_cancel),
                    style = Body.copy(fontSize = 16.sp),
                    color = LocalAccent.current,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(vertical = 13.dp)
                )
            }
        }
    }
}
