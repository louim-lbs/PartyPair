package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Connexion BLE ouverte vers une enceinte, prete a recevoir des commandes. */
@SuppressLint("MissingPermission")
class SpeakerLink private constructor(
    private val gatt: BluetoothGatt,
    private val tx: BluetoothGattCharacteristic,
    private val inbox: Inbox
) {

    private val notifications get() = inbox.flow

    /**
     * Adresse de l'enceinte partenaire memorisee, relevee au vol.
     *
     * L'enceinte annonce spontanement cette valeur a la connexion, sans qu'on
     * la demande. `FF:FF:FF:FF:FF:FF` signifie qu'aucune paire n'a jamais ete
     * formee : l'appairage initial doit alors passer par l'application JBL.
     */
    val partnerMac: String? get() = inbox.partnerMac

    fun write(value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                tx, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
        } else {
            @Suppress("DEPRECATION")
            tx.value = value
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(tx)
        }
    }

    /**
     * Attend que l'enceinte reponde.
     *
     * Une enceinte qui sort de veille accepte la connexion avant d'etre capable
     * de traiter les commandes. On l'interroge donc jusqu'a obtenir une reponse,
     * plutot que de patienter un temps fixe au hasard.
     *
     * @return vrai si l'enceinte a repondu dans le delai imparti.
     */
    suspend fun awaitReady(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
        var answered = false
        while (!answered) {
            write(JblProtocol.REQ_DEVICE_INFO)
            val reply = withTimeoutOrNull(PROBE_INTERVAL_MS) {
                notifications.first { it.isNotEmpty() }
            }
            answered = reply != null
        }
        true
    } ?: false

    /**
     * Lit un champ precis dans les reponses de l'enceinte.
     *
     * Une meme requete provoque plusieurs notifications portant le meme opcode :
     * un long etat complet, puis de courtes mises a jour partielles. Se contenter
     * de la premiere revient a tirer au sort. On parcourt donc les reponses
     * jusqu'a en trouver une qui contienne vraiment le champ demande.
     */
    private suspend fun queryField(
        request: ByteArray,
        expected: Byte,
        tag: Int,
        timeoutMs: Long = 4_000L
    ): ByteArray? = withTimeoutOrNull(timeoutMs) {
        var found: ByteArray? = null
        while (found == null) {
            write(request)
            withTimeoutOrNull(PROBE_INTERVAL_MS) {
                notifications
                    .first { frame ->
                        JblProtocol.commandOf(frame) == expected &&
                            JblProtocol.parseFields(frame).containsKey(tag)
                    }
                    .let { found = JblProtocol.parseFields(it)[tag] }
            }
        }
        found
    }

    /** Volume courant, ou null si l'enceinte ne le communique pas. */
    suspend fun readVolume(timeoutMs: Long = 4_000L): Int? =
        queryField(
            JblProtocol.REQ_PLAYER_INFO,
            JblProtocol.RESP_PLAYER_INFO,
            JblProtocol.TAG_VOLUME,
            timeoutMs
        )?.firstOrNull()?.toInt()?.and(0xFF)

    /** Canal attribue a cette enceinte, ou null s'il n'est pas communique. */
    suspend fun readChannel(): Byte? =
        queryField(JblProtocol.REQ_DEVICE_INFO, JblProtocol.RESP_DEV_INFO, JblProtocol.TAG_CHANNEL)
            ?.firstOrNull()

    /** Vrai si la paire stereo est deja etablie. */
    suspend fun isStereoLinked(): Boolean =
        queryField(
            JblProtocol.REQ_DEVICE_INFO,
            JblProtocol.RESP_DEV_INFO,
            JblProtocol.TAG_PARTY_CONNECT
        )?.firstOrNull() == JblProtocol.PARTY_CONNECTED

    fun close() {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    companion object {

        internal const val PROBE_INTERVAL_MS = 1_200L

        /**
         * Ouvre une connexion et attend la decouverte des services.
         * La connexion elle-meme suffit a sortir l'enceinte de veille.
         */
        suspend fun open(context: Context, adapter: BluetoothAdapter, mac: String): SpeakerLink =
            suspendCancellableCoroutine { cont ->
                val device = adapter.getRemoteDevice(mac)
                val settled = AtomicBoolean(false)
                var gattRef: BluetoothGatt? = null

                val inbox = Inbox()

                val callback = object : BluetoothGattCallback() {

                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                            BluetoothProfile.STATE_DISCONNECTED ->
                                if (settled.compareAndSet(false, true)) {
                                    g.close()
                                    cont.resumeWithException(
                                        SpeakerException(
                                            context.getString(R.string.error_connection_lost, mac)
                                        )
                                    )
                                }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (!settled.compareAndSet(false, true)) return
                        val service = g.getService(JblProtocol.SERVICE)
                        val txChar = service?.getCharacteristic(JblProtocol.TX)
                        if (txChar == null) {
                            g.disconnect(); g.close()
                            cont.resumeWithException(
                                SpeakerException(context.getString(R.string.error_no_service, mac))
                            )
                            return
                        }
                        // L'enceinte pousse ses notifications sans qu'on s'abonne
                        // cote peripherique : aucun descripteur a ecrire, il suffit
                        // de demander a Android de nous les remonter.
                        service.getCharacteristic(JblProtocol.RX)?.let {
                            runCatching { g.setCharacteristicNotification(it, true) }
                        }
                        cont.resume(SpeakerLink(g, txChar, inbox))
                    }

                    @Deprecated("Conserve pour Android 12 et anterieurs")
                    override fun onCharacteristicChanged(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                    ) {
                        @Suppress("DEPRECATION")
                        characteristic.value?.let { inbox.accept(it) }
                    }

                    override fun onCharacteristicChanged(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                    ) {
                        inbox.accept(value)
                    }
                }

                gattRef = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

                cont.invokeOnCancellation {
                    runCatching { gattRef?.disconnect() }
                    runCatching { gattRef?.close() }
                }
            }
    }
}

/**
 * Recueille les notifications de l'enceinte.
 *
 * Certaines arrivent avant qu'on ne pense a les ecouter : on retient donc au
 * passage celles qui nous serviront plus tard, plutot que de compter sur un
 * abonnement pose a temps.
 */
internal class Inbox {

    val flow = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Volatile
    var partnerMac: String? = null
        private set

    fun accept(frame: ByteArray) {
        if (JblProtocol.commandOf(frame) == JblProtocol.NOTIFY_SECONDARY_ADDRESS &&
            frame.size >= 9
        ) {
            partnerMac = frame.copyOfRange(3, 9)
                .joinToString(":") { "%02X".format(it) }
        }
        flow.tryEmit(frame)
    }
}

class SpeakerException(message: String) : Exception(message)
