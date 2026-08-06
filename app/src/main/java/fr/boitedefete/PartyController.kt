package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
    READY(R.string.step_ready),
    FAILED(R.string.step_failed)
}

/**
 * Enchaine le reveil des deux enceintes puis leur mise en paire stereo.
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
            onStep(Step.WAKING_PRIMARY)
            val primary = withTimeout(Config.CONNECT_TIMEOUT_MS) {
                SpeakerLink.open(context, adapter, primaryMac)
            }

            try {
                val phoneMac = settings.phoneMac
                if (phoneMac.isNotBlank()) {
                    primary.write(JblProtocol.connectTo(phoneMac))
                    delay(200)
                }

                onStep(Step.LINKING)
                // L'application JBL ecrit d'abord sur la secondaire, puis sur la
                // principale environ 250 ms plus tard.
                secondary.write(JblProtocol.TWS_LINK)
                delay(Config.INTER_WRITE_DELAY_MS)
                primary.write(JblProtocol.TWS_LINK)

                delay(Config.LINK_SETTLE_MS)
                onStep(Step.READY)
            } finally {
                primary.close()
            }
        } finally {
            secondary.close()
        }
    }

    /** Rompt la paire stereo. */
    suspend fun unlink() {
        val adapter = adapter()
        val macs = listOfNotNull(settings.secondary?.mac, settings.primary?.mac)
        if (macs.isEmpty()) throw SpeakerException(context.getString(R.string.error_not_configured))

        macs.forEach { mac ->
            val link = withTimeout(Config.CONNECT_TIMEOUT_MS) {
                SpeakerLink.open(context, adapter, mac)
            }
            try {
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
