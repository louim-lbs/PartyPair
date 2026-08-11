package fr.boitedefete.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import fr.boitedefete.MusicApp
import fr.boitedefete.R

/** Choix de l'application musicale ouverte depuis l'ecran principal. */
@Composable
fun MusicAppPicker(
    apps: List<MusicApp>,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Party.Cabinet)
            .padding(horizontal = 28.dp, vertical = 40.dp)
    ) {
        Text(
            stringResource(R.string.pick_music_app).uppercase(),
            style = Display.copy(fontSize = 20.sp),
            color = Party.Silkscreen
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.pick_music_hint), style = Body, color = Party.Muted)
        Spacer(Modifier.height(20.dp))

        if (apps.isEmpty()) {
            Text(stringResource(R.string.pick_music_none), style = Body, color = Party.Silkscreen)
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(apps) { app ->
                val icon = remember(app.packageName) {
                    runCatching {
                        context.packageManager
                            .getApplicationIcon(app.packageName)
                            .toBitmap(96, 96)
                            .asImageBitmap()
                    }.getOrNull()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(app.packageName) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(app.name, style = Body, color = Party.Silkscreen)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.info_back).uppercase(),
                style = Silkscreen,
                color = LocalAccent.current
            )
        }
    }
}
