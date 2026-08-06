package fr.boitedefete.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.boitedefete.R
import fr.boitedefete.Speaker

/**
 * Configuration au premier lancement.
 *
 * Les enceintes sont proposees depuis la liste des appareils deja appairés :
 * rien a recopier a la main. Seule l'adresse du telephone peut demander une
 * saisie, Android ne la laissant plus lire sur tous les appareils.
 */
@Composable
fun SetupScreen(
    paired: List<Speaker>,
    detectedPhoneMac: String?,
    onDone: (primary: Speaker, secondary: Speaker, phoneMac: String) -> Unit
) {
    var primary by remember { mutableStateOf<Speaker?>(null) }
    var secondary by remember { mutableStateOf<Speaker?>(null) }
    var phoneMac by remember { mutableStateOf(detectedPhoneMac.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Party.Cabinet)
            .padding(horizontal = 28.dp, vertical = 40.dp)
    ) {
        Text(
            stringResource(R.string.setup_title).uppercase(),
            style = Display.copy(fontSize = 20.sp),
            color = Party.Silkscreen
        )
        Spacer(Modifier.height(28.dp))

        when {
            primary == null -> Picker(
                question = stringResource(R.string.setup_primary_question),
                hint = stringResource(R.string.setup_primary_hint),
                devices = paired,
                onPick = { primary = it }
            )

            secondary == null -> Picker(
                question = stringResource(R.string.setup_secondary_question),
                hint = stringResource(R.string.setup_secondary_hint),
                devices = paired.filter { it.mac != primary!!.mac },
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

@Composable
private fun Picker(
    question: String,
    hint: String,
    devices: List<Speaker>,
    onPick: (Speaker) -> Unit
) {
    Text(question, style = Silkscreen.copy(letterSpacing = 1.sp), color = Party.Orange)
    Spacer(Modifier.height(6.dp))
    Text(hint, style = Silkscreen.copy(letterSpacing = 0.sp), color = Party.Muted)
    Spacer(Modifier.height(20.dp))

    if (devices.isEmpty()) {
        Text(
            stringResource(R.string.setup_no_devices),
            style = Silkscreen.copy(letterSpacing = 0.sp),
            color = Party.Silkscreen
        )
        return
    }

    LazyColumn {
        items(devices) { device ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(device) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(device.name, style = Silkscreen.copy(letterSpacing = 1.sp), color = Party.Silkscreen)
                Text(device.mac, style = Silkscreen.copy(letterSpacing = 0.sp, fontSize = 10.sp), color = Party.Muted)
            }
        }
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

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.setup_phone_mac_placeholder), color = Party.Muted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(28.dp))
    TextButton(onClick = onValidate, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.setup_finish).uppercase(),
            style = Silkscreen,
            color = Party.Orange,
            textAlign = TextAlign.Center
        )
    }
}
