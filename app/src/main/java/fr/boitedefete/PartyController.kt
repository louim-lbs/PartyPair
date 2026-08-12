package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import androidx.annotation.StringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Etapes de la sequence. Le libelle suit la langue du telephone. */
enum class Step(@StringRes val label: Int) {
    IDLE(R.string.step_idle),
    CONNECTING(R.string.step_connecting),
    WAITING_TRACK(R.string.step_waiting_track),
    WAKING_SECONDARY(R.string.step_waking_named),
    WAKING_PRIMARY(R.string.step_waking_named),
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

    suspend fun run(onStep: (Step) -> Unit, opened: SpeakerLink? = null) {
        // Deux clients GATT vers la meme enceinte ne serviraient a rien.
        WarmLink.release()
        warning = null
        subject = null

        val primaryDevice = settings.primary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val secondaryDevice = settings.secondary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val adapter = adapter()

        // L'enceinte principale d'abord : c'est elle qu'on interroge pour
        // connaitre l'etat de la paire, elle est donc reveillee la premiere.
        // Quand la connexion existe deja, on la reprend au lieu d'en ouvrir une
        // seconde — et on evite d'annoncer un reveil qui a deja eu lieu.
        val primary = opened ?: run {
            subject = Elision.subject(primaryDevice.name)
            onStep(Step.WAKING_PRIMARY)
            connect(adapter, primaryDevice).also { it.awaitReady(Config.READY_TIMEOUT_MS) }
        }

        try {

            // La secondaire peut etre debranchee ou hors de portee. Dans ce cas
            // on continue avec la principale : de la musique sur une enceinte
            // vaut mieux que le silence sur deux.
            subject = Elision.subject(secondaryDevice.name)
            onStep(Step.WAKING_SECONDARY)
            val secondary = runCatching { connect(adapter, secondaryDevice) }.getOrNull()
            secondary?.awaitReady(Config.READY_TIMEOUT_MS)

            try {
                // L'adresse du telephone ne sert qu'a la connexion audio :
                // si elle est absente ou mal formee, la paire stereo doit
                // s'etablir quand meme.
                val phoneMac = settings.phoneMac.takeIf { MacFormat.isComplete(it) }.orEmpty()
                if (phoneMac.isNotBlank()) {
                    runCatching { primary.write(JblProtocol.connectTo(phoneMac)) }
                    delay(300)
                }

                // Une enceinte qui n'a jamais eu de partenaire ne peut pas etre
                // appairee par ce protocole : l'association initiale se fait
                // dans l'application JBL. Autant le dire clairement.
                if (secondary != null && primary.partnerMac == JblProtocol.NO_PARTNER) {
                    throw SpeakerException(context.getString(R.string.error_never_paired))
                }

                // Inutile de refaire la paire si elle tient deja.
                if (secondary != null && !primary.isStereoLinked()) {
                    onStep(Step.LINKING)
                    // L'application JBL ecrit d'abord sur la secondaire, puis sur
                    // la principale environ 250 ms plus tard.
                    secondary.write(JblProtocol.TWS_LINK)
                    delay(Config.INTER_WRITE_DELAY_MS)
                    primary.write(JblProtocol.TWS_LINK)
                    delay(Config.LINK_SETTLE_MS)
                }

                if (secondary != null) {
                    if (settings.pendingChannelSwap) {
                        applyPendingChannels(primary, secondary)
                    } else {
                        rememberChannels(primary, secondary)
                    }
                }

                var audioConnected = true
                if (phoneMac.isNotBlank()) {
                    onStep(Step.CONNECTING_AUDIO)
                    audioConnected = awaitAudio(adapter, primaryDevice.mac) {
                        runCatching { primary.write(JblProtocol.connectTo(phoneMac)) }
                    }
                }

                // Le volume vient en dernier : a l'etablissement de la liaison
                // audio, Android recopie son propre niveau vers l'enceinte et
                // effacerait un reglage envoye plus tot. C'est ce qui laissait
                // l'enceinte muette apres une mise en veille.
                applyVolume(primary)
                applyBassBoost(primary)

                warning = when {
                    secondary == null -> context.getString(
                        R.string.warning_alone, secondaryDevice.name, primaryDevice.name
                    )
                    // L'enceinte ne s'est pas presentee au telephone : sans ce
                    // message, on chercherait longtemps pourquoi le son ne sort pas.
                    !audioConnected -> context.getString(
                        R.string.warning_no_audio, primaryDevice.name
                    )
                    else -> null
                }
                subject = null
                onStep(Step.READY)
            } finally {
                secondary?.close()
            }
        } finally {
            if (opened == null) primary.close()
        }
    }

    /**
     * Message a afficher a cote de l'etat, quand la sequence a abouti mais
     * de facon degradee. Null si tout s'est passe normalement.
     */
    var warning: String? = null
        private set

    /**
     * Nom de l'enceinte concernee par l'etape en cours.
     * « Réveil de Hildegarde » se comprend mieux que « la seconde enceinte ».
     */
    var subject: String? = null
        private set

    /**
     * Decide seule entre appairer et eteindre, en verifiant l'etat reel.
     *
     * L'affichage peut croire les enceintes pretes alors que la paire a ete
     * rompue entre-temps, par l'application JBL ou parce qu'une enceinte a ete
     * debranchee. Se fier a cette memoire ferait eteindre au lieu d'apparier.
     */
    suspend fun toggle(onStep: (Step) -> Unit) {
        // Deux clients GATT vers la meme enceinte ne serviraient a rien.
        WarmLink.release()
        val primaryDevice = settings.primary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val adapter = adapter()

        // Tant qu'on ignore si la paire tient, on ne peut annoncer ni reveil ni
        // extinction : dire « Reveil de Cecile » avant d'eteindre serait faux.
        warning = null
        subject = null
        onStep(Step.CONNECTING)

        val link = runCatching {
            connect(adapter, primaryDevice).also { it.awaitReady(Config.READY_TIMEOUT_MS) }
        }.getOrNull()

        if (link == null) {
            run(onStep)
            return
        }

        val linked = runCatching { link.isStereoLinked() }.getOrDefault(false)

        try {
            // La connexion est deja ouverte : la reprendre fait gagner les
            // quelques secondes d'un second etablissement de liaison.
            if (linked) fadeAndStop(link, onStep) else run(onStep, opened = link)
        } finally {
            link.close()
        }

        if (linked) stopSecondary()
    }

    /**
     * Remet les deux enceintes en veille, apres un fondu sonore.
     *
     * Couper net une enceinte qui joue fort est desagreable ; on abaisse donc le
     * volume par paliers avant d'eteindre, en memorisant le niveau de depart pour
     * le retrouver au reveil suivant.
     */
    suspend fun powerOff(onStep: (Step) -> Unit) {
        // Deux clients GATT vers la meme enceinte ne serviraient a rien.
        WarmLink.release()
        val primaryDevice = settings.primary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))

        runCatching {
            val primary = connect(adapter(), primaryDevice)
            try {
                fadeAndStop(primary, onStep)
            } finally {
                primary.close()
            }
        }
        stopSecondary()
    }

    /**
     * Fondu puis extinction de l'enceinte principale.
     *
     * Le fondu est ce que l'on entend : il passe avant tout le reste, et l'ecran
     * est rendu des qu'il est termine. L'extinction de la seconde enceinte suit
     * sans faire patienter.
     */
    private suspend fun fadeAndStop(primary: SpeakerLink, onStep: (Step) -> Unit) {
        onStep(Step.FADING_OUT)
        fadeOut(primary)
        primary.write(JblProtocol.POWER_OFF)
        // Une fois l'enceinte eteinte : remonter le volume avant l'aurait rendu
        // audible, ce qui est exactement ce que le fondu cherchait a eviter.
        delay(Config.RESTORE_DELAY_MS)
        restorePhoneVolume()
        onStep(Step.IDLE)
    }

    /** Eteint l'enceinte secondaire, une fois le silence obtenu. */
    private suspend fun stopSecondary() {
        val secondaryDevice = settings.secondary ?: return
        runCatching {
            val secondary = connect(adapter(), secondaryDevice)
            try {
                secondary.write(JblProtocol.POWER_OFF)
                delay(Config.INTER_WRITE_DELAY_MS)
            } finally {
                secondary.close()
            }
        }
    }

    /**
     * Abaisse progressivement le volume jusqu'au silence.
     *
     * De preference par le volume du telephone : une fois l'audio connecte, il
     * pilote celui de l'enceinte, et surtout il part du niveau reellement en
     * cours. Deduire ce niveau puis l'imposer risquait de viser trop haut et de
     * faire monter le son l'espace d'un instant.
     */
    private suspend fun fadeOut(primary: SpeakerLink) {
        if (fadeUsingPhone()) return
        fadeUsingBluetooth(primary)
    }

    /**
     * Fondu par le volume du telephone.
     *
     * Chaque palier descend depuis la valeur courante, sans jamais la depasser.
     * Le niveau d'origine est restitue une fois l'enceinte eteinte, pour ne pas
     * laisser le telephone muet.
     */
    private suspend fun fadeUsingPhone(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val start = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
            ?: return false
        if (start <= 0) return false

        settings.lastPhoneVolume = start
        var previous = start
        for (i in 1..Config.FADE_STEPS) {
            val level = start - start * i / Config.FADE_STEPS
            // Ni remontee, ni palier redondant : on n'ecrit qu'en descendant.
            if (level >= previous) continue
            val ok = runCatching {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
            }.isSuccess
            if (!ok) {
                // Volume verrouille : la descente n'aura pas lieu par cette voie,
                // et il ne faut surtout pas restituer un niveau jamais abaisse.
                settings.lastPhoneVolume = 0
                return false
            }
            previous = level
            delay(Config.FADE_STEP_MS)
        }
        return true
    }

    /** Restitue le volume du telephone, une fois les enceintes eteintes. */
    private fun restorePhoneVolume() {
        // Efface d'abord : un echec ne doit pas laisser une valeur qui serait
        // restituee a contretemps au prochain appel.
        val level = settings.lastPhoneVolume.takeIf { it > 0 } ?: return
        settings.lastPhoneVolume = 0
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
    }

    /** Repli quand le telephone n'est pas la source : on passe par le protocole. */
    private suspend fun fadeUsingBluetooth(primary: SpeakerLink) {
        // Le niveau doit etre lu : le deduire risquerait de le surestimer, et
        // le premier palier ferait alors monter le son.
        val start = primary.readVolume(Config.VOLUME_READ_MS) ?: return
        if (start <= 0) return
        settings.lastVolume = start

        // Interpolation plutot que decrement fixe : la descente reste reguliere
        // quel que soit le niveau de depart, et atteint exactement zero.
        val balanced = settings.balance != 0
        var previous = start
        for (i in 1..Config.FADE_STEPS) {
            val level = start - start * i / Config.FADE_STEPS
            if (level >= previous) continue
            previous = level
            primary.write(JblProtocol.setVolume(level))
            if (balanced) {
                // Les niveaux par enceinte ne suivent pas le volume general :
                // sans cela, une enceinte resterait audible pendant le fondu.
                primary.write(JblProtocol.setPrimaryVolume(level))
                primary.write(JblProtocol.setSecondaryVolume(level))
            }
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
        // Le fondu precedent a peut-etre laisse le telephone au minimum :
        // le restituer avant de regler l'enceinte, sinon rien ne sortira.
        restorePhoneVolume()

        val wanted = when {
            settings.wakeVolume >= 0 -> settings.wakeVolume
            settings.lastVolume >= 0 -> settings.lastVolume
            else -> return
        }
        // Montee par paliers : passer du silence au niveau voulu d'un seul coup
        // fait claquer l'amplificateur, surtout apres une extinction en fondu.
        val steps = Config.FADE_IN_STEPS
        for (i in 1..steps) {
            primary.write(JblProtocol.setVolume(wanted * i / steps))
            delay(Config.FADE_IN_STEP_MS)
        }

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

    /**
     * Applique le renforcement des graves seul, sans toucher au volume.
     *
     * Sert quand l'utilisateur change ce reglage alors que les enceintes
     * jouent : le geste doit s'entendre tout de suite, mais il ne doit surtout
     * pas ramener le volume au niveau de reveil.
     */
    suspend fun applyBassOnly() {
        val primaryDevice = settings.primary
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))

        // Pas d'attente de disponibilite : l'enceinte joue deja, elle repond.
        // La liaison est conservee entre deux appuis, ce qui rend le reglage
        // immediat des le premier quand l'ecran est ouvert.
        WarmLink.use(context, adapter(), primaryDevice) { link ->
            link.write(JblProtocol.setBassBoost(settings.bassBoost))
        }
    }

    /** Ouvre la liaison a l'avance, pour que le premier reglage soit immediat. */
    suspend fun warmUp() {
        val primaryDevice = settings.primary ?: return
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter?.takeIf { it.isEnabled } ?: return
        WarmLink.warm(context, adapter, primaryDevice)
    }

    /** Applique le renforcement des graves retenu dans les reglages. */
    private suspend fun applyBassBoost(primary: SpeakerLink) {
        primary.write(JblProtocol.setBassBoost(settings.bassBoost))
        delay(150)
    }

    /** Applique l'echange de canaux demande pendant que les enceintes dormaient. */
    private suspend fun applyPendingChannels(primary: SpeakerLink, secondary: SpeakerLink) {
        primary.write(JblProtocol.setChannel(settings.primaryChannel.toByte()))
        delay(Config.INTER_WRITE_DELAY_MS)
        secondary.write(JblProtocol.setChannel(settings.secondaryChannel.toByte()))
        delay(Config.INTER_WRITE_DELAY_MS)
        settings.pendingChannelSwap = false
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
        // Deux clients GATT vers la meme enceinte ne serviraient a rien.
        WarmLink.release()
        val primaryDevice = settings.primary
        val secondaryDevice = settings.secondary
        if (primaryDevice == null || secondaryDevice == null) {
            throw SpeakerException(context.getString(R.string.error_not_configured))
        }

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

        // L'intention est enregistree tout de suite, pour que l'ecran reponde
        // sans attendre une liaison Bluetooth.
        settings.primaryChannel = primaryTarget.toInt()
        settings.secondaryChannel = secondaryTarget.toInt()

        // Des enceintes eteintes ne doivent pas se rallumer pour si peu :
        // l'echange sera applique au prochain allumage.
        if (!isAlreadyOn()) {
            settings.pendingChannelSwap = true
            return
        }

        val adapter = adapter()
        runCatching {
            listOf(primaryDevice to primaryTarget, secondaryDevice to secondaryTarget)
                .forEach { (device, channel) ->
                    val link = connect(adapter, device)
                    try {
                        link.write(JblProtocol.setChannel(channel))
                        delay(Config.INTER_WRITE_DELAY_MS)
                    } finally {
                        link.close()
                    }
                }
        }.onFailure { settings.pendingChannelSwap = true }
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
        return isAudioConnected(adapter, primaryMac)
    }

    /**
     * Vrai si cette enceinte precise est connectee en audio.
     *
     * L'etat global de l'adaptateur ne suffit pas : il repond « connecte » des
     * qu'un appareil quelconque l'est, casque compris.
     */
    private suspend fun isAudioConnected(adapter: BluetoothAdapter, mac: String): Boolean {
        return withTimeoutOrNull(Config.PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val settled = AtomicBoolean(false)
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        val connected = runCatching {
                            proxy.connectedDevices.any { it.address.equals(mac, true) }
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
        // Deux clients GATT vers la meme enceinte ne serviraient a rien.
        WarmLink.release()
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
    private suspend fun awaitAudio(
        adapter: BluetoothAdapter,
        mac: String,
        retry: () -> Unit
    ): Boolean {
        val deadline = System.currentTimeMillis() + Config.AUDIO_TIMEOUT_MS
        val halfway = System.currentTimeMillis() + Config.AUDIO_TIMEOUT_MS / 2
        var retried = false
        while (System.currentTimeMillis() < deadline) {
            if (isAudioConnected(adapter, mac)) return true
            if (!retried && System.currentTimeMillis() > halfway) {
                retried = true
                retry()
            }
            delay(1_000)
        }
        // Dernier controle : la liaison s'etablit parfois juste apres l'echeance,
        // et annoncer un echec alors que le son sort serait deroutant.
        delay(2_000)
        return isAudioConnected(adapter, mac)
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
