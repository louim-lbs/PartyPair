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
    private val notifications: MutableSharedFlow<ByteArray>
) {

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
     * Envoie une requete et attend la reponse portant la commande voulue.
     * Renvoie null si l'enceinte ne repond pas a temps.
     */
    suspend fun query(request: ByteArray, expected: Byte, timeoutMs: Long = 2_500L): ByteArray? =
        withTimeoutOrNull(timeoutMs) {
            var answer: ByteArray? = null
            while (answer == null) {
                write(request)
                answer = withTimeoutOrNull(PROBE_INTERVAL_MS) {
                    notifications.first { JblProtocol.commandOf(it) == expected }
                }
            }
            answer
        }

    /** Volume courant, ou null si l'enceinte ne le communique pas. */
    suspend fun readVolume(): Int? {
        val answer = query(JblProtocol.REQ_PLAYER_INFO, JblProtocol.RESP_PLAYER_INFO) ?: return null
        val raw = JblProtocol.parseFields(answer)[JblProtocol.TAG_VOLUME] ?: return null
        return raw.firstOrNull()?.toInt()?.and(0xFF)
    }

    /** Canal attribue a cette enceinte, ou null s'il n'est pas communique. */
    suspend fun readChannel(): Byte? {
        val answer = query(JblProtocol.REQ_DEVICE_INFO, JblProtocol.RESP_DEV_INFO) ?: return null
        return JblProtocol.parseFields(answer)[JblProtocol.TAG_CHANNEL]?.firstOrNull()
    }

    /** Vrai si la paire stereo est deja etablie. */
    suspend fun isStereoLinked(): Boolean {
        val answer = query(JblProtocol.REQ_DEVICE_INFO, JblProtocol.RESP_DEV_INFO) ?: return false
        val raw = JblProtocol.parseFields(answer)[JblProtocol.TAG_PARTY_CONNECT] ?: return false
        return raw.firstOrNull() == JblProtocol.PARTY_CONNECTED
    }

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

                val notifications = MutableSharedFlow<ByteArray>(
                    extraBufferCapacity = 16,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )

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
                        cont.resume(SpeakerLink(g, txChar, notifications))
                    }

                    @Deprecated("Conserve pour Android 12 et anterieurs")
                    override fun onCharacteristicChanged(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                    ) {
                        @Suppress("DEPRECATION")
                        characteristic.value?.let { notifications.tryEmit(it) }
                    }

                    override fun onCharacteristicChanged(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                    ) {
                        notifications.tryEmit(value)
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

class SpeakerException(message: String) : Exception(message)
