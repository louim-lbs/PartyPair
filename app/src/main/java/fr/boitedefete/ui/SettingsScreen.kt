package fr.boitedefete.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.boitedefete.BuildConfig
import fr.boitedefete.JblProtocol
import fr.boitedefete.R
import fr.boitedefete.Settings

const val REPO_URL = "https://github.com/louim-lbs/PartyPair"
const val ISSUES_URL = "$REPO_URL/issues"
const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"

/** Reglages, informations et liens vers le depot. */
@Composable
fun SettingsScreen(
    settings: Settings,
    canScheduleExactAlarms: Boolean,
    onOpenUrl: (String) -> Unit,
    onChangeSpeakers: () -> Unit,
    onChangeMusicApp: () -> Unit,
    onAlarmToggled: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var volume by remember { mutableFloatStateOf(settings.wakeVolume.toFloat()) }
    var alarmOn by remember { mutableStateOf(settings.alarmEnabled) }
    var url by remember { mutableStateOf(settings.musicUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Party.Cabinet)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 38.dp)
    ) {
        Text(
            stringResource(R.string.settings_title).uppercase(),
            style = Display.copy(fontSize = 24.sp),
            color = Party.Silkscreen
        )

        Section(stringResource(R.string.section_devices))
        Entry(stringResource(R.string.change_speakers), onClick = onChangeSpeakers)
        Entry(stringResource(R.string.change_music_app), onClick = onChangeMusicApp)

        Section(stringResource(R.string.section_playback))
        Text(
            stringResource(R.string.wake_volume, volume.toInt()),
            style = Body,
            color = Party.Silkscreen
        )
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { settings.wakeVolume = volume.toInt() },
            valueRange = 0f..JblProtocol.MAX_VOLUME.toFloat(),
            steps = JblProtocol.MAX_VOLUME - 1
        )
        Text(
            stringResource(R.string.wake_volume_hint),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; settings.musicUrl = it },
            label = { Text(stringResource(R.string.playlist_url), color = Party.Muted) },
            placeholder = { Text("https://…", color = Party.Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.playlist_hint),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )

        Section(stringResource(R.string.section_alarm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.alarm_follow),
                style = Body,
                color = Party.Silkscreen,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
            Switch(
                checked = alarmOn,
                onCheckedChange = {
                    alarmOn = it
                    settings.alarmEnabled = it
                    onAlarmToggled()
                }
            )
        }
        Text(
            stringResource(R.string.alarm_hint),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )
        if (alarmOn && !canScheduleExactAlarms) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.alarm_permission_needed),
                style = Body.copy(fontSize = 13.sp),
                color = Party.Orange
            )
        }

        Section(stringResource(R.string.section_about))
        InfoRow(stringResource(R.string.info_version), BuildConfig.VERSION_NAME)
        InfoRow(stringResource(R.string.info_build), BuildConfig.BUILD_DATE)
        InfoRow(stringResource(R.string.info_license), "MIT")

        Spacer(Modifier.height(14.dp))
        Entry(stringResource(R.string.info_repo)) { onOpenUrl(REPO_URL) }
        Entry(stringResource(R.string.info_issues)) { onOpenUrl(ISSUES_URL) }
        Entry(stringResource(R.string.info_license_link)) { onOpenUrl(LICENSE_URL) }

        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.info_disclaimer),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )

        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.info_back).uppercase(),
                style = Silkscreen,
                color = Party.Orange
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(26.dp))
    Text(title.uppercase(), style = Silkscreen.copy(fontSize = 12.sp), color = Party.Muted)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Entry(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = Body.copy(fontSize = 16.sp),
        color = Party.Orange,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = Body.copy(fontSize = 14.sp), color = Party.Muted)
        Text(value, style = Body, color = Party.Silkscreen)
    }
}
