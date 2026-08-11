package fr.boitedefete.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
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
import fr.boitedefete.AppLanguage
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
    onOpenUrl: (String) -> Unit,
    onChangeSpeakers: () -> Unit,
    onChangeMusicApp: () -> Unit,
    onSwapChannels: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdate: () -> Unit,
    notificationsAllowed: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onLanguage: (String) -> Unit,
    onAccent: (String) -> Unit,
    currentAccent: String,
    currentLanguage: String,
    updateStatus: UpdateStatus?,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var volume by remember { mutableFloatStateOf(settings.wakeVolume.toFloat()) }
    var url by remember { mutableStateOf(settings.musicUrl) }
    var playlistName by remember { mutableStateOf(settings.playlistName) }
    var balance by remember { mutableFloatStateOf(settings.balance.toFloat()) }
    // Copie locale : l'echange doit se lire a l'ecran avant meme d'atteindre
    // les enceintes, qui peuvent etre eteintes.
    var channels by remember { mutableStateOf(settings.primaryChannel to settings.secondaryChannel) }
    var customPicker by remember { mutableStateOf(false) }
    var fromHour by remember { mutableFloatStateOf(settings.alarmFromHour.toFloat()) }
    var toHour by remember { mutableFloatStateOf(settings.alarmToHour.toFloat()) }

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

        Spacer(Modifier.height(4.dp))
        Text(
            channelSummary(settings, channels.first),
            style = Body.copy(fontSize = 14.sp),
            color = Party.Muted
        )
        Entry(stringResource(R.string.swap_channels)) {
            channels = channels.second to channels.first
            onSwapChannels()
        }

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
        Text(
            balanceLabel(settings, balance.toInt()),
            style = Body,
            color = Party.Silkscreen
        )
        Slider(
            value = balance,
            onValueChange = { balance = it },
            onValueChangeFinished = { settings.balance = balance.toInt() },
            valueRange = -Settings.MAX_BALANCE.toFloat()..Settings.MAX_BALANCE.toFloat(),
            steps = Settings.MAX_BALANCE * 2 - 1
        )
        Text(
            stringResource(R.string.balance_hint),
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

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it; settings.playlistName = it },
            label = { Text(stringResource(R.string.playlist_name), color = Party.Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.playlist_name_hint),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )

        Section(stringResource(R.string.section_alarm))
        Text(
            stringResource(R.string.alarm_window, fromHour.toInt(), toHour.toInt()),
            style = Body,
            color = Party.Silkscreen
        )
        Slider(
            value = fromHour,
            onValueChange = { fromHour = it },
            onValueChangeFinished = { settings.alarmFromHour = fromHour.toInt() },
            valueRange = 0f..23f,
            steps = 22
        )
        Slider(
            value = toHour,
            onValueChange = { toHour = it },
            onValueChangeFinished = { settings.alarmToHour = toHour.toInt() },
            valueRange = 0f..23f,
            steps = 22
        )
        Text(
            stringResource(R.string.alarm_window_hint),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )

        Section(stringResource(R.string.section_accent))
        Row(modifier = Modifier.fillMaxWidth()) {
            val choices = buildList {
                if (dynamicAccent() != null) add(Settings.ACCENT_DYNAMIC to dynamicAccent()!!)
                ACCENTS.forEach { (name, color) -> add(name to color) }
            }
            choices.forEach { (name, color) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        // Hauteur egale a la largeur : sans cela, une largeur
                        // repartie donne des ovales et non des pastilles.
                        .aspectRatio(1f)
                        .background(color, CircleShape)
                        .border(
                            width = if (currentAccent == name) 3.dp else 0.dp,
                            color = Party.Silkscreen,
                            shape = CircleShape
                        )
                        .clickable { customPicker = false; onAccent(name) }
                )
            }

            // Derniere pastille : la teinte libre, reconnaissable a son degrade.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .aspectRatio(1f)
                    .background(
                        Brush.sweepGradient(
                            (0..360 step 45).map { hsvToColor(it.toFloat(), 0.85f, 1f) }
                        ),
                        CircleShape
                    )
                    .border(
                        width = if (currentAccent.startsWith("#")) 3.dp else 0.dp,
                        color = Party.Silkscreen,
                        shape = CircleShape
                    )
                    .clickable { customPicker = true }
            )
        }

        // Les pastilles portent deja leur propre marge : sans ce repos, la
        // legende venait se coller a leur bord inferieur.
        Spacer(Modifier.height(10.dp))

        if (customPicker) {
            ColorPickerDialog(
                initial = parseHex(currentAccent) ?: Party.Orange,
                onConfirm = { customPicker = false; onAccent(it.toHex()) },
                onDismiss = { customPicker = false }
            )
        }
        Text(
            stringResource(R.string.accent_hint),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )

        if (AppLanguage.isSupported) {
            Section(stringResource(R.string.section_language))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    R.string.language_system to "",
                    R.string.language_fr to "fr",
                    R.string.language_en to "en"
                ).forEach { (labelRes, code) ->
                    Text(
                        stringResource(labelRes).uppercase(),
                        style = Silkscreen.copy(fontSize = 12.sp),
                        color = if (currentLanguage == code) LocalAccent.current else Party.Muted,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onLanguage(code) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }

        if (!notificationsAllowed) {
            Section(stringResource(R.string.section_notifications))
            Text(
                stringResource(R.string.notifications_blocked),
                style = Body.copy(fontSize = 13.sp),
                color = Party.Muted
            )
            Entry(stringResource(R.string.notifications_open), onClick = onOpenNotificationSettings)
        }

        Section(stringResource(R.string.section_backup))
        Text(
            stringResource(R.string.backup_hint),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )
        Entry(stringResource(R.string.backup_copy), onClick = onExport)
        Entry(stringResource(R.string.backup_restore), onClick = onImport)

        Section(stringResource(R.string.section_about))
        InfoRow(stringResource(R.string.info_version), BuildConfig.VERSION_NAME)
        InfoRow(stringResource(R.string.info_build), BuildConfig.BUILD_DATE)
        InfoRow(stringResource(R.string.info_license), "MIT")

        Spacer(Modifier.height(14.dp))
        Entry(stringResource(R.string.check_update), onClick = onCheckUpdate)
        updateStatus?.let { status ->
            Text(
                status.text,
                style = Body.copy(fontSize = 13.sp),
                color = if (status.actionable) LocalAccent.current else Party.Muted,
                modifier = status.onClick
                    ?.let { Modifier.fillMaxWidth().clickable(onClick = it).padding(vertical = 8.dp) }
                    ?: Modifier
            )
        }
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
                color = LocalAccent.current
            )
        }
    }
}

/** « Cécile à gauche, Hildegarde à droite », si les canaux sont connus. */
@Composable
private fun channelSummary(settings: Settings, primaryChannel: Int): String {
    val primary = settings.primary?.name ?: return stringResource(R.string.channels_unknown)
    val secondary = settings.secondary?.name ?: return stringResource(R.string.channels_unknown)
    return when (primaryChannel) {
        1 -> stringResource(R.string.channels_known, primary, secondary)
        2 -> stringResource(R.string.channels_known, secondary, primary)
        else -> stringResource(R.string.channels_unknown)
    }
}

/** « Plus fort sur Hildegarde » ou « Équilibré ». */
@Composable
private fun balanceLabel(settings: Settings, value: Int): String = when {
    value == 0 -> stringResource(R.string.balance_even)
    value > 0 -> stringResource(
        R.string.balance_towards,
        settings.secondary?.name.orEmpty()
    )
    else -> stringResource(
        R.string.balance_towards,
        settings.primary?.name.orEmpty()
    )
}

/**
 * Compte rendu d'une verification de version.
 * Le texte n'est cliquable que lorsqu'il y a effectivement quelque chose a faire.
 */
data class UpdateStatus(
    val text: String,
    val actionable: Boolean = false,
    val onClick: (() -> Unit)? = null
)

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
        color = LocalAccent.current,
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
