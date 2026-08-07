package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** Etapes de la sequence. Le libelle suit la langue du telephone. */
enum class Step(@StringRes val label: Int) {
    IDLE(R.string.step_idle),
    WAKING_SECONDARY(R.string.step_waking_secondary),
    WAKING_PRIMARY(R.string.step_waking_primary),
    LINKING(R.string.step_linking),
    CONNECTING_AUDIO(R.string.step_connecting_audio),
    FADING_OUT(R.string.step_fading_out),
    POWERING_OFF(R.string.step_powering_off),
    READY(R.string.step_ready),
    FAILED(R.string.step_failed)
}

/**
 * Enchaine le reveil des deux enceintes, leur mise en paire stereo, puis la
 * connexion audio de l'enceinte principale.
 *
 * La liaison stereo survit a la fermeture des connexions BLE : une fois la
 * sequence terminee, l'application n'a plus rien a maintenir.
 */
@SuppressLint("MissingPermission")
class PartyController(private val context: Context) {

    private val settings = Settings(context)

    suspend fun run(onStep: (Step) -> Unit) {
        warning = null
        val primaryDevice = settings.primary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val secondaryDevice = settings.secondary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val adapter = adapter()

        // L'enceinte secondaire peut etre debranchee ou hors de portee. Dans ce
        // cas on continue avec la principale : de la musique sur une enceinte
        // vaut mieux que le silence sur deux.
        onStep(Step.WAKING_SECONDARY)
        val secondary = runCatching { connect(adapter, secondaryDevice) }.getOrNull()
        secondary?.awaitReady(Config.READY_TIMEOUT_MS)

        try {
            onStep(Step.WAKING_PRIMARY)
            val primary = connect(adapter, primaryDevice)

            try {
                primary.awaitReady(Config.READY_TIMEOUT_MS)

                // L'adresse du telephone ne sert qu'a la connexion audio :
                // si elle est absente ou mal formee, la paire stereo doit
                // s'etablir quand meme.
                val phoneMac = settings.phoneMac.takeIf { MacFormat.isComplete(it) }.orEmpty()
                if (phoneMac.isNotBlank()) {
                    runCatching { primary.write(JblProtocol.connectTo(phoneMac)) }
                    delay(300)
                }

                // Inutile de refaire la paire si elle tient deja.
                if (secondary != null && !primary.isStereoLinked()) {
                    onStep(Step.LINKING)
                    secondary.write(JblProtocol.TWS_LINK)
                    delay(Config.INTER_WRITE_DELAY_MS)
                    primary.write(JblProtocol.TWS_LINK)
                    delay(Config.LINK_SETTLE_MS)
                }

                if (secondary != null) rememberChannels(primary, secondary)
                applyVolume(primary)
                applyBassBoost(primary)

                if (phoneMac.isNotBlank()) {
                    onStep(Step.CONNECTING_AUDIO)
                    awaitAudio(adapter) {
                        runCatching { primary.write(JblProtocol.connectTo(phoneMac)) }
                    }
                }

                if (secondary == null) {
                    warning = context.getString(
                        R.string.warning_alone, secondaryDevice.name, primaryDevice.name
                    )
                }
                onStep(Step.READY)
            } finally {
                primary.close()
            }
        } finally {
            secondary?.close()
        }
    }

    /**
     * Message a afficher a cote de l'etat, quand la sequence a abouti mais
     * de facon degradee. Null si tout s'est passe normalement.
     */
    var warning: String? = null
        private set

    /**
     * Decide seule entre appairer et eteindre, en verifiant l'etat reel.
     *
     * L'affichage peut croire les enceintes pretes alors que la paire a ete
     * rompue entre-temps, par l'application JBL ou parce qu'une enceinte a ete
     * debranchee. Se fier a cette memoire ferait eteindre au lieu d'apparier.
     */
    suspend fun toggle(onStep: (Step) -> Unit) {
        val primaryDevice = settings.primary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val adapter = adapter()

        val linked = runCatching {
            val link = connect(adapter, primaryDevice)
            try {
                link.awaitReady(Config.READY_TIMEOUT_MS)
                link.isStereoLinked()
            } finally {
                link.close()
            }
        }.getOrDefault(false)

        if (linked) powerOff(onStep) else run(onStep)
    }

    /**
     * Remet les deux enceintes en veille, apres un fondu sonore.
     *
     * Couper net une enceinte qui joue fort est desagreable ; on abaisse donc le
     * volume par paliers avant d'eteindre, en memorisant le niveau de depart pour
     * le retrouver au reveil suivant.
     */
    suspend fun powerOff(onStep: (Step) -> Unit) {
        val primaryDevice = settings.primary
        val secondaryDevice = settings.secondary
        if (primaryDevice == null || secondaryDevice == null) {
            throw SpeakerException(context.getString(R.string.error_not_configured))
        }
        val adapter = adapter()

        onStep(Step.FADING_OUT)
        runCatching {
            val primary = connect(adapter, primaryDevice)
            try {
                primary.awaitReady(Config.READY_TIMEOUT_MS)
                fadeOut(primary)
                primary.write(JblProtocol.POWER_OFF)
            } finally {
                primary.close()
            }
        }

        onStep(Step.POWERING_OFF)
        runCatching {
            val secondary = connect(adapter, secondaryDevice)
            try {
                secondary.write(JblProtocol.POWER_OFF)
                delay(Config.INTER_WRITE_DELAY_MS)
            } finally {
                secondary.close()
            }
        }

        onStep(Step.IDLE)
    }

    /** Abaisse progressivement le volume jusqu'au silence. */
    private suspend fun fadeOut(primary: SpeakerLink) {
        val start = primary.readVolume() ?: return
        if (start <= 0) return
        settings.lastVolume = start

        val stepSize = max(1, start / Config.FADE_STEPS)
        var level = start
        while (level > 0) {
            level = (level - stepSize).coerceAtLeast(0)
            primary.write(JblProtocol.setVolume(level))
            delay(Config.FADE_STEP_MS)
        }
    }

    /**
     * Applique le volume de reveil, puis l'equilibre entre les deux enceintes.
     *
     * Le protocole permet de regler chaque enceinte separement : on abaisse
     * celle du cote le plus proche plutot que de monter l'autre, pour ne jamais
     * depasser le niveau demande.
     */
    private suspend fun applyVolume(primary: SpeakerLink) {
        val wanted = when {
            settings.wakeVolume >= 0 -> settings.wakeVolume
            settings.lastVolume >= 0 -> settings.lastVolume
            else -> return
        }
        primary.write(JblProtocol.setVolume(wanted))
        delay(200)

        val balance = settings.balance
        if (balance != 0) {
            val primaryLevel = wanted - maxOf(0, balance)
            val secondaryLevel = wanted - maxOf(0, -balance)
            primary.write(JblProtocol.setPrimaryVolume(primaryLevel))
            delay(120)
            primary.write(JblProtocol.setSecondaryVolume(secondaryLevel))
            delay(120)
        }
    }

    /** Applique le renforcement des graves retenu dans les reglages. */
    private suspend fun applyBassBoost(primary: SpeakerLink) {
        primary.write(JblProtocol.setBassBoost(settings.bassBoost))
        delay(150)
    }

    /** Releve le canal de chaque enceinte pour pouvoir l'afficher plus tard. */
    private suspend fun rememberChannels(primary: SpeakerLink, secondary: SpeakerLink) {
        primary.readChannel()?.let { settings.primaryChannel = it.toInt() and 0xFF }
        secondary.readChannel()?.let { settings.secondaryChannel = it.toInt() and 0xFF }
    }

    /**
     * Echange les canaux gauche et droite des deux enceintes.
     * Utile quand la paire a ete montee a l'envers de leur disposition reelle.
     */
    suspend fun swapChannels() {
        val primaryDevice = settings.primary
        val secondaryDevice = settings.secondary
        if (primaryDevice == null || secondaryDevice == null) {
            throw SpeakerException(context.getString(R.string.error_not_configured))
        }
        val adapter = adapter()

        val primaryTarget = if (settings.primaryChannel == 1) {
            JblProtocol.CHANNEL_RIGHT
        } else {
            JblProtocol.CHANNEL_LEFT
        }
        val secondaryTarget = if (primaryTarget == JblProtocol.CHANNEL_LEFT) {
            JblProtocol.CHANNEL_RIGHT
        } else {
            JblProtocol.CHANNEL_LEFT
        }

        listOf(primaryDevice to primaryTarget, secondaryDevice to secondaryTarget)
            .forEach { (device, channel) ->
                val link = connect(adapter, device)
                try {
                    link.awaitReady(Config.READY_TIMEOUT_MS)
                    link.write(JblProtocol.setChannel(channel))
                    delay(Config.INTER_WRITE_DELAY_MS)
                } finally {
                    link.close()
                }
            }

        settings.primaryChannel = primaryTarget.toInt()
        settings.secondaryChannel = secondaryTarget.toInt()
    }

    /**
     * Verifie si les enceintes sont deja en service, sans les reveiller.
     *
     * Ouvrir une connexion BLE sortirait une enceinte de veille : on interroge
     * donc Android sur ses connexions audio, ce qui n'emet rien vers l'enceinte.
     * Si la principale est connectee, elle est allumee et la paire stereo, qui
     * survit a l'extinction du telephone, est presque toujours encore la.
     */
    suspend fun isAlreadyOn(): Boolean {
        val primaryMac = settings.primary?.mac ?: return false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter?.takeIf { it.isEnabled } ?: return false

        return withTimeoutOrNull(Config.PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val settled = AtomicBoolean(false)
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        val connected = runCatching {
                            proxy.connectedDevices.any { it.address.equals(primaryMac, true) }
                        }.getOrDefault(false)
                        runCatching { adapter.closeProfileProxy(profile, proxy) }
                        if (settled.compareAndSet(false, true)) cont.resume(connected)
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        if (settled.compareAndSet(false, true)) cont.resume(false)
                    }
                }
                val started = runCatching {
                    adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
                }.getOrDefault(false)
                if (!started && settled.compareAndSet(false, true)) cont.resume(false)
            }
        } ?: false
    }

    /** Rompt la paire stereo sans eteindre. */
    suspend fun unlink() {
        val adapter = adapter()
        val devices = listOfNotNull(settings.secondary, settings.primary)
        if (devices.isEmpty()) {
            throw SpeakerException(context.getString(R.string.error_not_configured))
        }
        devices.forEach { device ->
            val link = connect(adapter, device)
            try {
                link.awaitReady(Config.READY_TIMEOUT_MS)
                link.write(JblProtocol.TWS_UNLINK)
                delay(Config.INTER_WRITE_DELAY_MS)
            } finally {
                link.close()
            }
        }
    }

    /**
     * Ouvre une connexion, avec une seconde tentative.
     *
     * Une enceinte en veille profonde rate parfois la premiere sollicitation ;
     * le message d'erreur nomme l'enceinte concernee plutot que son adresse.
     */
    private suspend fun connect(adapter: BluetoothAdapter, device: Speaker): SpeakerLink {
        repeat(Config.CONNECT_ATTEMPTS) { attempt ->
            val link = runCatching {
                withTimeout(Config.CONNECT_TIMEOUT_MS) {
                    SpeakerLink.open(context, adapter, device.mac)
                }
            }.getOrNull()
            if (link != null) return link
            if (attempt < Config.CONNECT_ATTEMPTS - 1) delay(1_500)
        }
        throw SpeakerException(context.getString(R.string.error_speaker_unreachable, device.name))
    }

    /**
     * Attend que l'enceinte principale rejoigne le telephone en audio,
     * en relancant l'invitation a mi-parcours si rien ne vient.
     */
    private suspend fun awaitAudio(adapter: BluetoothAdapter, retry: () -> Unit) {
        val deadline = System.currentTimeMillis() + Config.AUDIO_TIMEOUT_MS
        val halfway = System.currentTimeMillis() + Config.AUDIO_TIMEOUT_MS / 2
        var retried = false
        while (System.currentTimeMillis() < deadline) {
            if (adapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
                BluetoothProfile.STATE_CONNECTED
            ) return
            if (!retried && System.currentTimeMillis() > halfway) {
                retried = true
                retry()
            }
            delay(800)
        }
    }

    private fun adapter(): BluetoothAdapter {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
            ?: throw SpeakerException(context.getString(R.string.error_no_bluetooth))
        if (!adapter.isEnabled) {
            throw SpeakerException(context.getString(R.string.error_bluetooth_off))
        }
        return adapter
    }
}
