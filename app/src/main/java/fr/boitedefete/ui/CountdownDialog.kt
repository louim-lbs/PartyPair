package fr.boitedefete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.boitedefete.R
import kotlinx.coroutines.delay

/**
 * Propose d'ouvrir l'application musicale une fois la paire etablie.
 *
 * Le decompte laisse le temps de renoncer sans avoir a se precipiter, et part
 * tout seul si personne ne repond.
 */
@Composable
fun CountdownDialog(
    seconds: Int,
    onOpenNow: () -> Unit,
    onDismiss: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(seconds) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
        onOpenNow()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Party.Grille, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            Text(
                stringResource(R.string.countdown_title),
                style = Silkscreen.copy(fontSize = 14.sp),
                color = LocalAccent.current
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.countdown_body, remaining),
                style = Body,
                color = Party.Silkscreen
            )

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.countdown_stay).uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = Party.Muted,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(0.dp))
                TextButton(onClick = onOpenNow) {
                    Text(
                        stringResource(R.string.countdown_now).uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = LocalAccent.current
                    )
                }
            }
        }
    }
}
