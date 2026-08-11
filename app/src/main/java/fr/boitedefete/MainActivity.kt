package fr.boitedefete

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import fr.boitedefete.ui.ACCENTS
import fr.boitedefete.ui.BoiteDeFeteTheme
import fr.boitedefete.ui.dynamicAccent
import fr.boitedefete.ui.Body
import fr.boitedefete.ui.ConfirmDialog
import fr.boitedefete.ui.ControlEntry
import fr.boitedefete.ui.ControlRow
import fr.boitedefete.ui.CountdownDialog
import fr.boitedefete.ui.Display
import fr.boitedefete.ui.LocalAccent
import fr.boitedefete.ui.parseHex
import fr.boitedefete.ui.DriverButton
import fr.boitedefete.ui.MusicAppPicker
import fr.boitedefete.ui.MusicButton
import fr.boitedefete.ui.Party
import fr.boitedefete.ui.SetupScreen
import fr.boitedefete.ui.SleepDialog
import fr.boitedefete.ui.SettingsScreen
import fr.boitedefete.ui.UpdateStatus
import fr.boitedefete.ui.Silkscreen

/** Taille du titre, identique sur tous les ecrans. */
private val TITLE_SIZE = 24.sp

/** Hauteur reservee au libelle d'etat, pour que la mise en page ne bouge pas. */
private val STATUS_HEIGHT = 56.dp

private enum class Screen { PARTY, SETUP, SETTINGS, MUSIC_PICKER }

class MainActivity : ComponentActivity() {

    private var permissionGranted by mutableStateOf(false)

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* rien a faire : l'utilisateur verra l'etat se mettre a jour */ }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGranted = hasPermission()
        if (permissionGranted) {
            lifecycleScope.launch { PartyService.refreshState(this@MainActivity) }
        }
    }

    override fun onPause() {
        super.onPause()
        MusicLauncher.setForeground(false)
        // Rendre la liaison : la garder ouverte hors de l'ecran empecherait
        // l'application JBL de parler aux enceintes.
        lifecycleScope.launch { WarmLink.release() }
    }

    override fun onResume() {
        super.onResume()
        MusicLauncher.setForeground(true)
        // L'etat vit en memoire : au retour dans l'application, il faut verifier
        // que les enceintes ne sont pas deja en service.
        if (permissionGranted) {
            SleepTimer.restore(this)
            lifecycleScope.launch {
                PartyService.refreshState(this@MainActivity)
                // Preparer la liaison si les enceintes tournent : le premier
                // reglage rapide sera alors immediat.
                if (PartyService.state.value.step == Step.READY) {
                    PartyService.start(this@MainActivity, PartyService.ACTION_WARM_UP)
                }
                UpdateChecker.checkQuietly(this@MainActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = hasPermission()
        if (!permissionGranted) askPermission()

        setContent {
            val settingsStore = remember { Settings(this) }
            var accentName by remember { mutableStateOf(settingsStore.accent) }
            val dynamic = dynamicAccent()
            val accent = when {
                accentName.startsWith("#") -> parseHex(accentName) ?: Party.Orange
                accentName == Settings.ACCENT_DYNAMIC && dynamic != null -> dynamic
                else -> ACCENTS[accentName] ?: ACCENTS.getValue(Settings.DEFAULT_ACCENT)
            }

            BoiteDeFeteTheme(accent = accent) {
                Surface(modifier = Modifier.fillMaxSize(), color = Party.Cabinet) {
                    val context = LocalContext.current
                    val settings = remember { Settings(context) }
                    var screen by remember {
                        mutableStateOf(if (settings.isConfigured) Screen.PARTY else Screen.SETUP)
                    }
                    var musicApp by remember { mutableStateOf(settings.musicApp) }
                    // Annuler n'a de sens que si une configuration existe deja.
                    var reconfiguring by remember { mutableStateOf(false) }
                    // D'ou vient-on ? Le retour doit ramener a l'ecran precedent,
                    // pas systematiquement a l'accueil.
                    var musicPickerFrom by remember { mutableStateOf(Screen.PARTY) }
                    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }

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
                            onChangeMusicApp = {
                                musicPickerFrom = Screen.SETTINGS
                                screen = Screen.MUSIC_PICKER
                            },
                            onSwapChannels = {
                                PartyService.start(this, PartyService.ACTION_SWAP_CHANNELS)
                            },
                            onCheckUpdate = { checkUpdate { updateStatus = it } },
                            notificationsAllowed = SleepTimer.notificationsAllowed(context),
                            onOpenNotificationSettings = ::openNotificationSettings,
                            onLanguage = { AppLanguage.set(this, it) },
                            onAccent = { settings.accent = it; accentName = it },
                            currentAccent = accentName,
                            currentLanguage = AppLanguage.current(context),
                            updateStatus = updateStatus,
                            onExport = ::exportSetup,
                            onImport = {
                                if (importSetup()) {
                                    musicApp = settings.musicApp
                                    screen = Screen.PARTY
                                }
                            },
                            // Repartir d'une page vierge au retour suivant.
                            onBack = { updateStatus = null; screen = Screen.PARTY }
                        )

                        screen == Screen.MUSIC_PICKER -> MusicAppPicker(
                            apps = Settings.musicApps(context),
                            onPick = {
                                settings.musicApp = it
                                musicApp = it
                                screen = musicPickerFrom
                            },
                            onBack = { screen = musicPickerFrom }
                        )

                        else -> PartyScreen(
                            settings = settings,
                            musicApp = musicApp,
                            onPress = ::togglePower,
                            onStandby = ::standby,
                            onOpenMusic = ::openMusic,
                            onPlayMusic = ::playMusic,
                            onPickMusic = {
                                musicPickerFrom = Screen.PARTY
                                screen = Screen.MUSIC_PICKER
                            },
                            onSettings = { screen = Screen.SETTINGS },
                            onCycleBass = ::cycleBassBoost,
                            onSleepTimer = ::setSleepTimer,
                            onToggleAlarm = ::toggleAlarm,
                            bluetoothReady = bluetoothReady(),
                            onEnableBluetooth = ::askEnableBluetooth
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
    private fun togglePower() {
        PartyService.start(this, PartyService.ACTION_TOGGLE)
    }

    /**
     * Mise en veille demandee sans ambiguite.
     *
     * On saute la verification de l'etat de la paire : l'utilisateur vient de
     * confirmer, inutile de lui faire attendre une interrogation Bluetooth
     * avant que le fondu ne commence.
     */
    private fun standby() {
        PartyService.start(this, PartyService.ACTION_POWER_OFF)
    }

    /**
     * Fait defiler les trois etats du renforcement des graves.
     * Si les enceintes jouent, le changement s'applique tout de suite.
     */
    private fun cycleBassBoost(): Int {
        val settings = Settings(this)
        val next = (settings.bassBoost + 1) % 3
        settings.bassBoost = next
        if (PartyService.state.value.step == Step.READY) {
            PartyService.start(this, PartyService.ACTION_APPLY_BASS)
        }
        return next
    }

    /**
     * Verifie la presence d'une nouvelle version, et la telecharge le cas echeant.
     * Le compte rendu passe par le rappel, pour s'afficher sous le bouton.
     */
    private fun checkUpdate(report: (UpdateStatus?) -> Unit) {
        report(UpdateStatus(getString(R.string.update_checking)))
        lifecycleScope.launch {
            val outcome = runCatching { UpdateChecker.checkNow(this@MainActivity) }
                .getOrDefault(UpdateChecker.Outcome.Unreachable)
            when (outcome) {
                UpdateChecker.Outcome.UpToDate ->
                    report(UpdateStatus(getString(R.string.update_none)))

                // Rien a proposer : on l'ecrit, sans emmener l'utilisateur ailleurs.
                UpdateChecker.Outcome.NoRelease ->
                    report(UpdateStatus(getString(R.string.update_no_release)))

                UpdateChecker.Outcome.Unreachable ->
                    report(UpdateStatus(getString(R.string.update_failed)))

                is UpdateChecker.Outcome.Available -> {
                    val release = outcome.release
                    val apkUrl = release.apkUrl
                    if (apkUrl == null) {
                        report(
                            UpdateStatus(
                                getString(R.string.update_found, release.version),
                                actionable = true,
                                onClick = { openUrl(UpdateChecker.releasesPage()) }
                            )
                        )
                    } else {
                        report(UpdateStatus(getString(R.string.update_downloading), true))
                        val ok = UpdateChecker.downloadAndInstall(this@MainActivity, apkUrl)
                        if (ok) {
                            report(null)
                        } else {
                            report(
                                UpdateStatus(
                                    getString(R.string.update_failed),
                                    actionable = true,
                                    onClick = { openUrl(UpdateChecker.releasesPage()) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /** Copie la configuration dans le presse-papiers. */
    private fun exportSetup() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.app_name), Settings(this).export())
        )
        Toast.makeText(this, R.string.backup_copied, Toast.LENGTH_SHORT).show()
    }

    /** Restaure une configuration collee depuis le presse-papiers. */
    private fun importSetup(): Boolean {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()
        val restored = !text.isNullOrBlank() && Settings(this).restore(text)
        Toast.makeText(
            this,
            if (restored) R.string.backup_restored else R.string.backup_invalid,
            Toast.LENGTH_LONG
        ).show()
        if (restored) AlarmScheduler.reschedule(this)
        return restored
    }

    /** Vrai si le Bluetooth est en service. */
    private fun bluetoothReady(): Boolean {
        val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    /** Propose d'allumer le Bluetooth plutot que de se contenter de le signaler. */
    private fun askEnableBluetooth() {
        runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
    }

    /**
     * Ouvre l'application musicale, sans plus.
     *
     * Un appui explicite doit toujours ouvrir : la retenue qui evite d'interrompre
     * une ecoute en cours n'a de sens que pour un declenchement automatique.
     */
    private fun openMusic() {
        MusicLauncher.open(this, Settings(this))
    }

    /**
     * Ouvre la playlist et lance la lecture, si rien ne joue deja.
     * C'est la suite naturelle d'un allumage : des enceintes pretes et silencieuses
     * n'ont pas grand interet.
     */
    private fun playMusic() {
        val settings = Settings(this)
        // La playlist appartient au reveil : en dehors de ses horaires, on se
        // contente de relancer ce qui etait en cours.
        val morning = AlarmScheduler.inMorningWindow(System.currentTimeMillis(), settings)
        lifecycleScope.launch {
            MusicLauncher.openAndPlay(this@MainActivity, settings, usePlaylist = morning)
        }
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

    /** Emmene aux reglages de notifications de l'application. */
    private fun openNotificationSettings() {
        runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /**
     * Depuis Android 12, parler aux enceintes et les chercher sont deux
     * permissions distinctes. Avant, la recherche BLE passait par la position.
     */
    private fun neededPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Sans elle, ni la notification du service ni l'alerte d'echec
        // n'apparaissent sur Android 13 et au-dela.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    /**
     * Les notifications ne sont pas indispensables au fonctionnement : leur
     * refus ne doit pas bloquer l'application.
     */
    private fun essentialPermissions(): Array<String> =
        neededPermissions().filter { it != Manifest.permission.POST_NOTIFICATIONS }.toTypedArray()

    private fun hasPermission(): Boolean = essentialPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun askPermission() = requestPermissions.launch(neededPermissions())
}

/** « Cécile (G) · Hildegarde (D) » en français, « (L) » et « (R) » ailleurs. */
@Composable
private fun speakerSummary(settings: Settings): String {
    val left = stringResource(R.string.channel_left)
    val right = stringResource(R.string.channel_right)

    fun label(name: String?, channel: Int): String? {
        if (name == null) return null
        val side = when (channel) {
            1 -> left
            2 -> right
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
                color = LocalAccent.current
            )
        }
    }
}

@Composable
private fun PartyScreen(
    settings: Settings,
    musicApp: String?,
    onPress: () -> Unit,
    onStandby: () -> Unit,
    onOpenMusic: () -> Unit,
    onPlayMusic: () -> Unit,
    onPickMusic: () -> Unit,
    onSettings: () -> Unit,
    onCycleBass: () -> Int,
    onSleepTimer: (Int?) -> Unit,
    onToggleAlarm: () -> Boolean,
    bluetoothReady: Boolean,
    onEnableBluetooth: () -> Unit
) {
    val state by PartyService.state.collectAsState()
    val context = LocalContext.current

    var bass by remember { mutableStateOf(settings.bassBoost) }
    var alarmOn by remember { mutableStateOf(settings.alarmEnabled) }
    // Echeance partagee : annuler depuis la notification se voit ici sans delai.
    val sleepAt by SleepTimer.deadline.collectAsState()
    val sleepLeft = SleepTimer.remainingMinutes(sleepAt)
    var sleepDialog by remember { mutableStateOf(false) }
    var standbyDialog by remember { mutableStateOf(false) }

    // Des que les enceintes sont pretes, on prepare la liaison : le premier
    // reglage rapide sera alors immediat, et surtout il aboutira.
    LaunchedEffect(state.step) {
        if (state.step == Step.READY) {
            PartyService.start(context, PartyService.ACTION_WARM_UP)
        }
    }

    // La proposition vit dans le service, pas dans cet ecran : un aller-retour
    // dans les reglages detruirait l'etat local et relancerait le decompte.
    val promptPending by PartyService.musicPrompt.collectAsState()
    val showCountdown = promptPending && musicApp != null

    val progress = when (state.step) {
        Step.IDLE, Step.FAILED -> 0f
        Step.WAKING_PRIMARY -> 0.25f
        Step.WAKING_SECONDARY -> 0.5f
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

    if (standbyDialog) {
        ConfirmDialog(
            title = stringResource(R.string.standby_confirm_title),
            body = stringResource(R.string.standby_confirm_body),
            confirmLabel = stringResource(R.string.standby_confirm_yes),
            dismissLabel = stringResource(R.string.standby_confirm_no),
            onConfirm = { dontAskAgain ->
                if (dontAskAgain) settings.confirmStandby = false
                standbyDialog = false
                onStandby()
            },
            onDismiss = { standbyDialog = false }
        )
    }

    if (sleepDialog) {
        SleepDialog(
            activeMinutes = sleepLeft,
            onPick = { minutes ->
                onSleepTimer(minutes)
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
                onPlayMusic()
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
                onClick = {
                    when {
                        // Un appui accidentel ne doit pas couper la musique en cours.
                        state.step == Step.READY && settings.confirmStandby -> standbyDialog = true
                        state.step == Step.READY -> onStandby()
                        else -> onPress()
                    }
                }
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Hauteur figee : sans elle, un libelle passant sur deux lignes
            // repousserait le haut-parleur vers le haut a chaque changement d'etat.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STATUS_HEIGHT),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = (state.error
                        ?: stringResource(state.step.label, state.subject.orEmpty())).uppercase(),
                    style = Silkscreen,
                    color = if (state.step == Step.FAILED) LocalAccent.current else Party.Silkscreen,
                    textAlign = TextAlign.Center
                )
            }

            if (!bluetoothReady) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.bluetooth_enable).uppercase(),
                    style = Silkscreen.copy(fontSize = 13.sp),
                    color = LocalAccent.current,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable(onClick = onEnableBluetooth)
                        .padding(vertical = 6.dp, horizontal = 10.dp)
                )
            }

            state.warning?.let { warning ->
                Spacer(Modifier.height(6.dp))
                Text(
                    warning,
                    style = Body.copy(fontSize = 13.sp),
                    color = LocalAccent.current,
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
                        onClick = { sleepDialog = true }
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
                    color = LocalAccent.current,
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
