package fr.boitedefete.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bandeau de trois commandes, dans l'esprit de la sérigraphie d'une façade
 * d'enceinte : une étiquette discrète, et la valeur en dessous, allumée en
 * orange quand le réglage est actif.
 */
@Composable
fun ControlRow(
    entries: List<ControlEntry>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = entry.onClick)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    entry.label.uppercase(),
                    style = Silkscreen.copy(fontSize = 10.sp, letterSpacing = 1.5.sp),
                    color = Party.Muted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    entry.value.uppercase(),
                    style = Silkscreen.copy(fontSize = 13.sp, letterSpacing = 1.sp),
                    color = if (entry.active) Party.Orange else Party.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class ControlEntry(
    val label: String,
    val value: String,
    val active: Boolean,
    val onClick: () -> Unit
)
