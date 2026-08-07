package fr.boitedefete

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import fr.boitedefete.ui.BoiteDeFeteTheme
import fr.boitedefete.ui.Body
import fr.boitedefete.ui.ControlEntry
import fr.boitedefete.ui.ControlRow
import fr.boitedefete.ui.CountdownDialog
import fr.boitedefete.ui.Display
import fr.boitedefete.ui.DriverButton
import fr.boitedefete.ui.MusicAppPicker
import fr.boitedefete.ui.MusicButton
import fr.boitedefete.ui.Party
import fr.boitedefete.ui.SetupScreen
import fr.boitedefete.ui.SleepDialog
import fr.boitedefete.ui.SettingsScreen
import fr.boitedefete.ui.Silkscreen

/** Taille du titre, identique sur tous les ecrans. */
private val TITLE_SIZE = 24.sp

private enum class Screen { PARTY, SETUP, SETTINGS, MUSIC_PICKER }

class MainActivity : ComponentActivity() {

    private var permissionGranted by mutableStateOf(false)

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGranted = hasPermission()
        if (permissionGranted) {
            lifecycleScope.launch { PartyService.refreshState(this@MainActivity) }
        }
    }

    override fun onResume() {
        super.onResume()
        // L'etat vit en memoire : au retour dans l'application, il faut verifier
        // que les enceintes ne sont pas deja en service.
        if (permissionGranted) {
            lifecycleScope.launch { PartyService.refreshState(this@MainActivity) }
        }
    }

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
                    // Annuler n'a de sens que si une configuration existe deja.
                    var reconfiguring by remember { mutableStateOf(false) }

                    when {
                        !permissionGranted -> PermissionScreen(onRetry = ::askPermission)

                        screen == Screen.SETUP -> SetupScreen(
                            paired = Settings.pairedDevices(context),
                            detectedPhoneMac = Settings.detectPhoneMac(context),
                            onCancel = if (reconfiguring) {
                                { reconfiguring = false; screen = Screen.SETTINGS }
                            } else {
                                null
                            }
                        ) { primary, secondary, phoneMac ->
                            settings.primary = primary
                            settings.secondary = secondary
                            settings.phoneMac = phoneMac
                            AlarmScheduler.reschedule(context)
                            reconfiguring = false
                            screen = Screen.PARTY
                        }

                        screen == Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            onOpenUrl = ::openUrl,
                            onChangeSpeakers = { reconfiguring = true; screen = Screen.SETUP },
                            onChangeMusicApp = { screen = Screen.MUSIC_PICKER },
                            onSwapChannels = {
                                PartyService.start(this, PartyService.ACTION_SWAP_CHANNELS)
                            },
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
                            onOpenMusic = { MusicLauncher.open(this, settings) },
                            onPickMusic = { screen = Screen.MUSIC_PICKER },
                            onSettings = { screen = Screen.SETTINGS },
                            onCycleBass = ::cycleBassBoost,
                            onSleepTimer = ::setSleepTimer,
                            onToggleAlarm = ::toggleAlarm
                        )
                    }
                }
            }
        }
    }

    /**
     * Un appui reveille et apparie ; le suivant met en veille.
     * La decision revient au controleur, qui verifie l'etat reel des enceintes.
     */
    private fun togglePower() = PartyService.start(this, PartyService.ACTION_TOGGLE)

    /**
     * Fait defiler les trois etats du renforcement des graves.
     * Si les enceintes jouent, le changement s'applique tout de suite.
     */
    private fun cycleBassBoost(): Int {
        val settings = Settings(this)
        val next = (settings.bassBoost + 1) % 3
        settings.bassBoost = next
        if (PartyService.state.value.step == Step.READY) {
            PartyService.start(this, PartyService.ACTION_APPLY_SOUND)
        }
        return next
    }

    private fun setSleepTimer(minutes: Int?) {
        if (minutes == null) SleepTimer.cancel(this) else SleepTimer.schedule(this, minutes)
    }

    /** Active ou coupe le declenchement avant l'alarme du telephone. */
    private fun toggleAlarm(): Boolean {
        val settings = Settings(this)
        val wanted = !settings.alarmEnabled
        settings.alarmEnabled = wanted
        onAlarmToggled()
        return settings.alarmEnabled
    }

    /**
     * Programmer une alarme exacte demande une autorisation explicite depuis
     * Android 12 : on emmene l'utilisateur au bon endroit plutot que d'echouer
     * en silence.
     */
    private fun onAlarmToggled() {
        val settings = Settings(this)
        if (settings.alarmEnabled && !AlarmScheduler.canScheduleExact(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            }
            return
        }
        AlarmScheduler.reschedule(this)
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

/** « Cécile (G) · Hildegarde (D) », ou les seuls noms si le canal est inconnu. */
private fun speakerSummary(settings: Settings): String {
    fun label(name: String?, channel: Int): String? {
        if (name == null) return null
        val side = when (channel) {
            1 -> "G"
            2 -> "D"
            else -> return name
        }
        return "$name ($side)"
    }
    return listOfNotNull(
        label(settings.primary?.name, settings.primaryChannel),
        label(settings.secondary?.name, settings.secondaryChannel)
    ).joinToString(" · ")
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

@Composable
private fun PartyScreen(
    settings: Settings,
    musicApp: String?,
    onPress: () -> Unit,
    onOpenMusic: () -> Unit,
    onPickMusic: () -> Unit,
    onSettings: () -> Unit,
    onCycleBass: () -> Int,
    onSleepTimer: (Int?) -> Unit,
    onToggleAlarm: () -> Boolean
) {
    val state by PartyService.state.collectAsState()
    val context = LocalContext.current

    var bass by remember { mutableStateOf(settings.bassBoost) }
    var alarmOn by remember { mutableStateOf(settings.alarmEnabled) }
    var sleepLeft by remember { mutableStateOf(SleepTimer.remainingMinutes(context)) }
    var sleepDialog by remember { mutableStateOf(false) }

    // La proposition vit dans le service, pas dans cet ecran : un aller-retour
    // dans les reglages detruirait l'etat local et relancerait le decompte.
    val promptPending by PartyService.musicPrompt.collectAsState()
    val showCountdown = promptPending && musicApp != null

    val progress = when (state.step) {
        Step.IDLE, Step.FAILED -> 0f
        Step.WAKING_SECONDARY -> 0.25f
        Step.WAKING_PRIMARY -> 0.5f
        Step.LINKING -> 0.75f
        Step.CONNECTING_AUDIO -> 0.9f
        Step.FADING_OUT, Step.POWERING_OFF -> 0.15f
        Step.READY -> 1f
    }
    val running = state.step !in setOf(Step.IDLE, Step.READY, Step.FAILED)

    val reducedMotion = AndroidSettings.Global.getFloat(
        context.contentResolver,
        AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    if (sleepDialog) {
        SleepDialog(
            activeMinutes = sleepLeft,
            onPick = { minutes ->
                onSleepTimer(minutes)
                sleepLeft = minutes
                sleepDialog = false
            },
            onDismiss = { sleepDialog = false }
        )
    }

    if (showCountdown) {
        CountdownDialog(
            seconds = Config.MUSIC_COUNTDOWN_SECONDS,
            onOpenNow = {
                PartyService.musicPrompt.value = false
                onOpenMusic()
            },
            onDismiss = { PartyService.musicPrompt.value = false }
        )
    }

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
                style = Display.copy(fontSize = TITLE_SIZE),
                color = Party.Silkscreen
            )
            Text(
                stringResource(R.string.settings_glyph),
                style = Display.copy(fontSize = 20.sp),
                color = Party.Muted,
                modifier = Modifier
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
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

            state.warning?.let { warning ->
                Spacer(Modifier.height(6.dp))
                Text(
                    warning,
                    style = Body.copy(fontSize = 13.sp),
                    color = Party.Orange,
                    textAlign = TextAlign.Center
                )
            }

            if (state.step == Step.READY) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.press_again_to_sleep),
                    style = Body.copy(fontSize = 13.sp),
                    color = Party.Muted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(14.dp))

            ControlRow(
                entries = listOf(
                    ControlEntry(
                        label = stringResource(R.string.bass_boost_short),
                        value = stringResource(
                            when (bass) {
                                1 -> R.string.bass_deep
                                2 -> R.string.bass_punchy
                                else -> R.string.bass_off
                            }
                        ),
                        active = bass > 0,
                        onClick = { bass = onCycleBass() }
                    ),
                    ControlEntry(
                        label = stringResource(R.string.section_sleep),
                        value = sleepLeft?.let { stringResource(R.string.sleep_minutes, it) }
                            ?: stringResource(R.string.bass_off),
                        active = sleepLeft != null,
                        onClick = {
                            sleepLeft = SleepTimer.remainingMinutes(context)
                            sleepDialog = true
                        }
                    ),
                    ControlEntry(
                        label = stringResource(R.string.section_alarm),
                        value = stringResource(
                            if (alarmOn) R.string.control_on else R.string.bass_off
                        ),
                        active = alarmOn,
                        onClick = { alarmOn = onToggleAlarm() }
                    )
                )
            )

            if (alarmOn && !AlarmScheduler.canScheduleExact(context)) {
                Text(
                    stringResource(R.string.alarm_permission_needed),
                    style = Body.copy(fontSize = 12.sp),
                    color = Party.Orange,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(10.dp))

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
                text = speakerSummary(settings),
                style = Body.copy(fontSize = 13.sp),
                color = Party.Muted
            )
        }
    }
}
