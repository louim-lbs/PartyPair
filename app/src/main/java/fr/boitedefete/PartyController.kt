package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Etapes de la sequence. Le libelle suit la langue du telephone. */
enum class Step(@StringRes val label: Int) {
    IDLE(R.string.step_idle),
    WAKING_SECONDARY(R.string.step_waking_secondary),
    WAKING_PRIMARY(R.string.step_waking_primary),
    LINKING(R.string.step_linking),
    POWERING_OFF(R.string.step_powering_off),
    CONNECTING_AUDIO(R.string.step_connecting_audio),
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
        val primaryMac = settings.primary?.mac
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val secondaryMac = settings.secondary?.mac
            ?: throw SpeakerException(context.getString(R.string.error_not_configured))
        val adapter = adapter()

        onStep(Step.WAKING_SECONDARY)
        val secondary = withTimeout(Config.CONNECT_TIMEOUT_MS) {
            SpeakerLink.open(context, adapter, secondaryMac)
        }

        try {
            // Attendre que l'enceinte reponde vraiment : sinon la commande de
            // liaison arrive avant qu'elle ne soit capable de la traiter.
            secondary.awaitReady(Config.READY_TIMEOUT_MS)

            onStep(Step.WAKING_PRIMARY)
            val primary = withTimeout(Config.CONNECT_TIMEOUT_MS) {
                SpeakerLink.open(context, adapter, primaryMac)
            }

            try {
                primary.awaitReady(Config.READY_TIMEOUT_MS)

                val phoneMac = settings.phoneMac
                if (phoneMac.isNotBlank()) {
                    primary.write(JblProtocol.connectTo(phoneMac))
                    delay(300)
                }

                onStep(Step.LINKING)
                // L'application JBL ecrit d'abord sur la secondaire, puis sur la
                // principale environ 250 ms plus tard.
                secondary.write(JblProtocol.TWS_LINK)
                delay(Config.INTER_WRITE_DELAY_MS)
                primary.write(JblProtocol.TWS_LINK)
                delay(Config.LINK_SETTLE_MS)

                if (phoneMac.isNotBlank()) {
                    onStep(Step.CONNECTING_AUDIO)
                    awaitAudio(adapter, primaryMac) {
                        // Relancer l'invitation si l'enceinte n'est pas venue.
                        primary.write(JblProtocol.connectTo(phoneMac))
                    }
                }

                onStep(Step.READY)
            } finally {
                primary.close()
            }
        } finally {
            secondary.close()
        }
    }

    /**
     * Attend que l'enceinte principale rejoigne le telephone en audio,
     * en relancant l'invitation a mi-parcours si rien ne vient.
     */
    private suspend fun awaitAudio(
        adapter: BluetoothAdapter,
        mac: String,
        retry: () -> Unit
    ) {
        val deadline = System.currentTimeMillis() + Config.AUDIO_TIMEOUT_MS
        var retried = false
        while (System.currentTimeMillis() < deadline) {
            if (adapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
                BluetoothProfile.STATE_CONNECTED
            ) return
            if (!retried && System.currentTimeMillis() > deadline - Config.AUDIO_TIMEOUT_MS / 2) {
                retried = true
                retry()
            }
            delay(800)
        }
    }

    /**
     * Remet les deux enceintes en veille.
     *
     * La commande d'extinction coupe l'amplificateur ; le controleur BLE reste
     * alimente, ce qui permettra de les reveiller par une simple connexion.
     */
    suspend fun powerOff(onStep: (Step) -> Unit) {
        val adapter = adapter()
        val macs = listOfNotNull(settings.secondary?.mac, settings.primary?.mac)
        if (macs.isEmpty()) {
            throw SpeakerException(context.getString(R.string.error_not_configured))
        }

        onStep(Step.POWERING_OFF)
        macs.forEach { mac ->
            runCatching {
                val link = withTimeout(Config.CONNECT_TIMEOUT_MS) {
                    SpeakerLink.open(context, adapter, mac)
                }
                try {
                    link.write(JblProtocol.POWER_OFF)
                    delay(Config.INTER_WRITE_DELAY_MS)
                } finally {
                    link.close()
                }
            }
        }
        onStep(Step.IDLE)
    }

    /** Rompt la paire stereo. */
    suspend fun unlink() {
        val adapter = adapter()
        val macs = listOfNotNull(settings.secondary?.mac, settings.primary?.mac)
        if (macs.isEmpty()) {
            throw SpeakerException(context.getString(R.string.error_not_configured))
        }

        macs.forEach { mac ->
            val link = withTimeout(Config.CONNECT_TIMEOUT_MS) {
                SpeakerLink.open(context, adapter, mac)
            }
            try {
                link.awaitReady(Config.READY_TIMEOUT_MS)
                link.write(JblProtocol.TWS_UNLINK)
                delay(Config.INTER_WRITE_DELAY_MS)
            } finally {
                link.close()
            }
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
