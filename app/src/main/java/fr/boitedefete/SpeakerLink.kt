package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Connexion BLE ouverte vers une enceinte, prete a recevoir des commandes. */
@SuppressLint("MissingPermission")
class SpeakerLink private constructor(
    private val gatt: BluetoothGatt,
    private val tx: BluetoothGattCharacteristic
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

    fun close() {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    companion object {

        /**
         * Ouvre une connexion et attend la decouverte des services.
         * La connexion elle-meme suffit a sortir l'enceinte de veille.
         */
        suspend fun open(context: Context, adapter: BluetoothAdapter, mac: String): SpeakerLink =
            suspendCancellableCoroutine { cont ->
                val device = adapter.getRemoteDevice(mac)
                val settled = AtomicBoolean(false)
                var gattRef: BluetoothGatt? = null

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
                        val characteristic = g.getService(JblProtocol.SERVICE)
                            ?.getCharacteristic(JblProtocol.TX)
                        if (characteristic == null) {
                            g.disconnect(); g.close()
                            cont.resumeWithException(
                                SpeakerException(
                                    context.getString(R.string.error_no_service, mac)
                                )
                            )
                        } else {
                            cont.resume(SpeakerLink(g, characteristic))
                        }
                    }
                }

                gattRef = device.connectGatt(
                    context, false, callback, BluetoothDeviceTransport.LE
                )

                cont.invokeOnCancellation {
                    runCatching { gattRef?.disconnect() }
                    runCatching { gattRef?.close() }
                }
            }
    }
}

/** BluetoothDevice.TRANSPORT_LE, isole pour rester lisible. */
private object BluetoothDeviceTransport {
    const val LE = 2
}

class SpeakerException(message: String) : Exception(message)
