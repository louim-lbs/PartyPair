package fr.boitedefete

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.boitedefete.ui.Body
import fr.boitedefete.ui.Display
import fr.boitedefete.ui.DriverButton
import fr.boitedefete.ui.InfoScreen
import fr.boitedefete.ui.MusicAppPicker
import fr.boitedefete.ui.MusicButton
import fr.boitedefete.ui.Party
import fr.boitedefete.ui.SetupScreen
import fr.boitedefete.ui.Silkscreen

private enum class Screen { PARTY, SETUP, INFO, MUSIC_PICKER }

class MainActivity : ComponentActivity() {

    private var permissionGranted by mutableStateOf(false)

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionGranted = hasPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = hasPermission()
        if (!permissionGranted) askPermission()

        setContent {
            BoiteDeFeteTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Party.Cabinet) {
                    val context = LocalContext.current
                    val settings = remember { Settings(context) }
                    var screen by remember {
                        mutableStateOf(if (settings.isConfigured) Screen.PARTY else Screen.SETUP)
                    }
                    var musicApp by remember { mutableStateOf(settings.musicApp) }

                    when {
                        !permissionGranted -> PermissionScreen(onRetry = ::askPermission)

                        screen == Screen.SETUP -> SetupScreen(
                            paired = Settings.pairedDevices(context),
                            detectedPhoneMac = Settings.detectPhoneMac(context)
                        ) { primary, secondary, phoneMac ->
                            settings.primary = primary
                            settings.secondary = secondary
                            settings.phoneMac = phoneMac
                            screen = Screen.PARTY
                        }

                        screen == Screen.INFO -> InfoScreen(
                            onOpenUrl = ::openUrl,
                            onBack = { screen = Screen.PARTY }
                        )

                        screen == Screen.MUSIC_PICKER -> MusicAppPicker(
                            apps = Settings.musicApps(context),
                            onPick = {
                                settings.musicApp = it
                                musicApp = it
                                screen = Screen.PARTY
                            },
                            onBack = { screen = Screen.PARTY }
                        )

                        else -> PartyScreen(
                            settings = settings,
                            musicApp = musicApp,
                            onPress = ::togglePower,
                            onOpenMusic = { openMusicApp(musicApp) },
                            onPickMusic = { screen = Screen.MUSIC_PICKER },
                            onInfo = { screen = Screen.INFO },
                            onReconfigure = { settings.clear(); musicApp = null; screen = Screen.SETUP }
                        )
                    }
                }
            }
        }
    }

    /** Un appui reveille et apparie ; le suivant remet les enceintes en veille. */
    private fun togglePower() {
        val ready = PartyService.state.value.step == Step.READY
        PartyService.start(
            this,
            if (ready) PartyService.ACTION_POWER_OFF else PartyService.ACTION_START
        )
    }

    private fun openMusicApp(packageName: String?) {
        val intent = packageName?.let { packageManager.getLaunchIntentForPackage(it) } ?: return
        startActivity(intent)
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /**
     * Depuis Android 12, parler aux enceintes et les chercher sont deux
     * permissions distinctes. Avant, la recherche BLE passait par la position.
     */
    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasPermission(): Boolean = neededPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun askPermission() = requestPermissions.launch(neededPermissions())
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
            style = Body,
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
    musicApp: String?,
    onPress: () -> Unit,
    onOpenMusic: () -> Unit,
    onPickMusic: () -> Unit,
    onInfo: () -> Unit,
    onReconfigure: () -> Unit
) {
    val state by PartyService.state.collectAsState()
    val context = LocalContext.current

    val progress = when (state.step) {
        Step.IDLE, Step.FAILED -> 0f
        Step.WAKING_SECONDARY -> 0.25f
        Step.WAKING_PRIMARY -> 0.5f
        Step.LINKING -> 0.75f
        Step.CONNECTING_AUDIO -> 0.9f
        Step.POWERING_OFF -> 0.15f
        Step.READY -> 1f
    }
    val running = state.step !in setOf(Step.IDLE, Step.READY, Step.FAILED)

    val reducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Party.Cabinet)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = Display.copy(fontSize = 24.sp),
                color = Party.Silkscreen
            )
            Text(
                "i",
                style = Display.copy(fontSize = 20.sp),
                color = Party.Muted,
                modifier = Modifier
                    .clickable(onClick = onInfo)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Box(contentAlignment = Alignment.Center) {
            DriverButton(
                progress = progress,
                active = running,
                reducedMotion = reducedMotion,
                onClick = onPress
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = (state.error ?: stringResource(state.step.label)).uppercase(),
                style = Silkscreen,
                color = if (state.step == Step.FAILED) Party.Orange else Party.Silkscreen,
                textAlign = TextAlign.Center
            )

            if (state.step == Step.READY) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.press_again_to_sleep),
                    style = Body.copy(fontSize = 13.sp),
                    color = Party.Muted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))

            if (musicApp != null) {
                MusicButton(
                    packageName = musicApp,
                    label = stringResource(R.string.open_music),
                    onClick = onOpenMusic
                )
            } else {
                TextButton(onClick = onPickMusic) {
                    Text(
                        stringResource(R.string.pick_music_app).uppercase(),
                        style = Silkscreen.copy(fontSize = 13.sp),
                        color = Party.Muted
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = listOfNotNull(settings.primary?.name, settings.secondary?.name)
                    .joinToString(" · "),
                style = Body.copy(fontSize = 13.sp),
                color = Party.Muted,
                modifier = Modifier.combinedClickable(
                    onClick = onPickMusic,
                    onLongClick = onReconfigure
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.reconfigure_hint),
                style = Body.copy(fontSize = 12.sp),
                color = Party.Muted.copy(alpha = 0.6f)
            )
        }
    }
}
