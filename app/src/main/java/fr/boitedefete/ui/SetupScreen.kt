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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.boitedefete.MacFormat
import fr.boitedefete.R
import fr.boitedefete.ScannedSpeaker
import fr.boitedefete.Speaker
import fr.boitedefete.SpeakerScanner

/**
 * Configuration au premier lancement.
 *
 * L'enceinte principale est choisie parmi les appareils appairés : c'est elle
 * qui transporte l'audio, l'appairage lui est nécessaire.
 *
 * L'enceinte secondaire est cherchée par un scan : elle n'a pas besoin d'être
 * appairée, et mieux vaut ne pas le faire pour qu'elle n'occupe pas une
 * connexion Bluetooth pour rien.
 */
@Composable
fun SetupScreen(
    paired: List<Speaker>,
    detectedPhoneMac: String?,
    /** Null au tout premier lancement : il n'y a alors nulle part ou revenir. */
    onCancel: (() -> Unit)? = null,
    onDone: (primary: Speaker, secondary: Speaker, phoneMac: String) -> Unit
) {
    onCancel?.let { BackHandler(onBack = it) }

    var primary by remember { mutableStateOf<Speaker?>(null) }
    var secondary by remember { mutableStateOf<Speaker?>(null) }
    var phoneMac by remember { mutableStateOf(detectedPhoneMac.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Party.Cabinet)
            .padding(horizontal = 28.dp, vertical = 40.dp)
    ) {
        // Le bouton d'abandon occupe sa propre ligne : cote a cote, il rognait
        // le titre, dont les capitales espacees prennent toute la largeur.
        onCancel?.let { cancel ->
            Text(
                stringResource(R.string.cancel).uppercase(),
                style = Silkscreen.copy(fontSize = 13.sp),
                color = Party.Muted,
                maxLines = 1,
                modifier = Modifier
                    .clickable(onClick = cancel)
                    .padding(vertical = 8.dp, horizontal = 2.dp)
            )
            Spacer(Modifier.height(10.dp))
        }

        Text(
            stringResource(R.string.setup_title).uppercase(),
            style = Display.copy(fontSize = 22.sp),
            color = Party.Silkscreen
        )
        Spacer(Modifier.height(24.dp))

        when {
            primary == null -> SpeakerStep(
                question = stringResource(R.string.setup_primary_question),
                hint = stringResource(R.string.setup_primary_hint),
                paired = paired,
                exclude = null,
                onPick = { primary = it }
            )

            secondary == null -> SpeakerStep(
                question = stringResource(R.string.setup_secondary_question),
                hint = stringResource(R.string.setup_secondary_hint),
                paired = paired,
                exclude = primary!!.mac,
                onPick = { secondary = it }
            )

            else -> PhoneMacStep(
                value = phoneMac,
                detected = detectedPhoneMac != null,
                onChange = { phoneMac = it },
                onValidate = { onDone(primary!!, secondary!!, phoneMac.trim()) }
            )
        }
    }
}

/**
 * Choix d'une enceinte : les appareils appairés d'abord, puis ceux repérés par
 * un scan. La recherche s'arrête d'elle-même au bout de quelques secondes.
 */
@Composable
private fun SpeakerStep(
    question: String,
    hint: String,
    paired: List<Speaker>,
    exclude: String?,
    onPick: (Speaker) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember { SpeakerScanner(context) }

    var scanning by remember { mutableStateOf(false) }
    var showEverything by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<ScannedSpeaker>>(emptyList()) }

    DisposableEffect(Unit) { onDispose { scanner.stop() } }

    fun launchScan(everything: Boolean) {
        scanning = true
        showEverything = everything
        scanner.start(
            includeEverything = everything,
            onUpdate = { found = it },
            onFinished = { scanning = false }
        )
    }

    Text(question, style = Silkscreen.copy(letterSpacing = 1.sp), color = Party.Orange)
    Spacer(Modifier.height(6.dp))
    Text(hint, style = Silkscreen.copy(letterSpacing = 0.sp), color = Party.Muted)
    Spacer(Modifier.height(18.dp))

    TextButton(
        onClick = { if (scanning) { scanner.stop(); scanning = false } else launchScan(false) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            stringResource(if (scanning) R.string.scan_stop else R.string.scan_start).uppercase(),
            style = Silkscreen,
            color = Party.Orange
        )
    }

    if (scanning) {
        Text(
            stringResource(R.string.scan_running),
            style = Silkscreen.copy(letterSpacing = 0.sp, fontSize = 10.sp),
            color = Party.Muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(12.dp))

    val pairedShown = paired.filter { it.mac != exclude }
    val foundShown = found.filter { it.mac != exclude && pairedShown.none { p -> p.mac == it.mac } }

    if (pairedShown.isEmpty() && foundShown.isEmpty() && !scanning) {
        Text(
            stringResource(R.string.setup_no_devices),
            style = Silkscreen.copy(letterSpacing = 0.sp),
            color = Party.Silkscreen
        )
    }

    LazyColumn {
        // Les appareils reperes par la recherche viennent en premier : c'est ce
        // que l'on vient de declencher, et l'enceinte secondaire s'y trouve.
        if (foundShown.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.section_nearby)) }
            items(foundShown) { device ->
                DeviceRow(
                    name = device.name,
                    detail = "${device.mac}   ${device.rssi} dBm",
                    highlighted = device.isPartyBox,
                    onClick = { onPick(Speaker(device.name, device.mac)) }
                )
            }
        }

        if (pairedShown.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.section_paired)) }
            items(pairedShown) { device ->
                DeviceRow(
                    name = device.name,
                    detail = device.mac,
                    highlighted = false,
                    onClick = { onPick(device) }
                )
            }
        }

        if (!scanning && !showEverything && found.isEmpty()) {
            item {
                TextButton(onClick = { launchScan(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.scan_all).uppercase(),
                        style = Silkscreen.copy(fontSize = 11.sp),
                        color = Party.Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text.uppercase(), style = Silkscreen.copy(fontSize = 9.sp), color = Party.Muted)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DeviceRow(
    name: String,
    detail: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = Silkscreen.copy(letterSpacing = 1.sp),
            color = if (highlighted) Party.Orange else Party.Silkscreen
        )
        Text(
            detail,
            style = Silkscreen.copy(letterSpacing = 0.sp, fontSize = 10.sp),
            color = Party.Muted
        )
    }
}

@Composable
private fun PhoneMacStep(
    value: String,
    detected: Boolean,
    onChange: (String) -> Unit,
    onValidate: () -> Unit
) {
    Text(
        stringResource(R.string.setup_phone_mac),
        style = Silkscreen.copy(letterSpacing = 1.sp),
        color = Party.Orange
    )
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(
            if (detected) R.string.setup_phone_mac_detected
            else R.string.setup_phone_mac_manual
        ),
        style = Silkscreen.copy(letterSpacing = 0.sp),
        color = Party.Muted
    )
    Spacer(Modifier.height(20.dp))

    // Les deux-points sont ajoutes au fil de la frappe, et un collage est
    // accepte quelle que soit sa forme : minuscules, tirets, espaces.
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(MacFormat.normalize(it)) },
        placeholder = { Text(stringResource(R.string.setup_phone_mac_placeholder), color = Party.Muted) },
        singleLine = true,
        isError = value.isNotBlank() && !MacFormat.isComplete(value),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth()
    )

    if (value.isNotBlank() && !MacFormat.isComplete(value)) {
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.mac_incomplete),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Orange
        )
    }

    Spacer(Modifier.height(28.dp))
    TextButton(
        onClick = onValidate,
        enabled = value.isBlank() || MacFormat.isComplete(value),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            stringResource(R.string.setup_finish).uppercase(),
            style = Silkscreen,
            color = Party.Orange,
            textAlign = TextAlign.Center
        )
    }
}
