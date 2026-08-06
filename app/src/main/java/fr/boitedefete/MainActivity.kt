package fr.boitedefete

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.boitedefete.ui.BoiteDeFeteTheme
import fr.boitedefete.ui.Display
import fr.boitedefete.ui.DriverButton
import fr.boitedefete.ui.Party
import fr.boitedefete.ui.SetupScreen
import fr.boitedefete.ui.Silkscreen

class MainActivity : ComponentActivity() {

    private var permissionGranted by mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = hasPermission()
        if (!permissionGranted) askPermission()

        setContent {
            BoiteDeFeteTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Party.Cabinet) {
                    val context = LocalContext.current
                    val settings = remember { Settings(context) }
                    var configured by remember { mutableStateOf(settings.isConfigured) }

                    when {
                        !permissionGranted -> PermissionScreen(onRetry = ::askPermission)

                        !configured -> SetupScreen(
                            paired = Settings.pairedDevices(context),
                            detectedPhoneMac = Settings.detectPhoneMac(context)
                        ) { primary, secondary, phoneMac ->
                            settings.primary = primary
                            settings.secondary = secondary
                            settings.phoneMac = phoneMac
                            configured = true
                        }

                        else -> PartyScreen(
                            settings = settings,
                            onStart = { PartyService.start(this@MainActivity) },
                            onReconfigure = { settings.clear(); configured = false }
                        )
                    }
                }
            }
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    private fun askPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionGranted = true
        }
    }
}

@Composable
private fun PermissionScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Party.Cabinet).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.permission_rationale),
            style = Silkscreen.copy(letterSpacing = 0.sp),
            color = Party.Silkscreen,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRetry) {
            Text(
                stringResource(R.string.permission_allow).uppercase(),
                style = Silkscreen,
                color = Party.Orange
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PartyScreen(
    settings: Settings,
    onStart: () -> Unit,
    onReconfigure: () -> Unit
) {
    val state by PartyService.state.collectAsState()
    val context = LocalContext.current

    val progress = when (state.step) {
        Step.IDLE, Step.FAILED -> 0f
        Step.WAKING_SECONDARY -> 0.25f
        Step.WAKING_PRIMARY -> 0.5f
        Step.LINKING -> 0.8f
        Step.READY -> 1f
    }
    val running = state.step != Step.IDLE &&
        state.step != Step.READY &&
        state.step != Step.FAILED

    val reducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Party.Cabinet)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.app_name).uppercase(),
            style = Display,
            color = Party.Silkscreen,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelMark(stringResource(R.string.channel_left), lit = progress >= 0.5f)
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                DriverButton(
                    progress = progress,
                    active = running,
                    reducedMotion = reducedMotion,
                    onClick = onStart
                )
            }
            ChannelMark(stringResource(R.string.channel_right), lit = progress >= 0.25f)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = (state.error ?: stringResource(state.step.label)).uppercase(),
                style = Silkscreen,
                color = if (state.step == Step.FAILED) Party.Orange else Party.Silkscreen,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = listOfNotNull(settings.primary?.name, settings.secondary?.name)
                    .joinToString(" · "),
                style = Silkscreen.copy(letterSpacing = 1.5.sp, fontSize = 11.sp),
                color = Party.Muted,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onReconfigure
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.reconfigure_hint),
                style = Silkscreen.copy(letterSpacing = 0.sp, fontSize = 9.sp),
                color = Party.Muted.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ChannelMark(letter: String, lit: Boolean) {
    Text(letter, style = Silkscreen, color = if (lit) Party.Orange else Party.Muted)
}
