package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

/** Une enceinte reperee par le scan, appairee ou non. */
data class ScannedSpeaker(
    val name: String,
    val mac: String,
    val rssi: Int,
    val isPartyBox: Boolean,
    val paired: Boolean
)

/**
 * Recherche des enceintes autour du telephone.
 *
 * Les PartyBox diffusent un identifiant de service 16 bits 0xFDDF dans leurs
 * annonces. Sur une capture reelle comportant 64 appareils environnants, seules
 * les deux enceintes le presentaient : c'est un filtre fiable.
 *
 * L'enceinte secondaire n'a pas besoin d'etre appairee. La liaison stereo passe
 * uniquement par le Bluetooth basse consommation, sans appairage (aucun echange
 * de securite dans les captures du protocole).
 */
@SuppressLint("MissingPermission")
class SpeakerScanner(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var callback: ScanCallback? = null
    private val results = linkedMapOf<String, ScannedSpeaker>()

    /**
     * Lance une recherche.
     *
     * @param includeEverything remonte aussi les appareils qui ne presentent pas
     *   l'identifiant PartyBox, au cas ou un modele l'annoncerait differemment.
     */
    fun start(
        includeEverything: Boolean,
        onUpdate: (List<ScannedSpeaker>) -> Unit,
        onFinished: () -> Unit
    ) {
        stop()
        results.clear()
        onUpdate(emptyList())

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = manager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            onFinished()
            return
        }

        val paired = Settings.pairedDevices(context).associateBy { it.mac.uppercase() }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                val isPartyBox = record?.serviceData?.containsKey(PARTYBOX_SERVICE) == true
                if (!isPartyBox && !includeEverything) return

                val mac = result.device.address.uppercase()
                val name = record?.deviceName
                    ?: runCatching { result.device.name }.getOrNull()
                    ?: paired[mac]?.name
                    ?: return // sans nom, impossible a identifier : on l'ignore

                results[mac] = ScannedSpeaker(
                    name = name,
                    mac = mac,
                    rssi = result.rssi,
                    isPartyBox = isPartyBox,
                    paired = paired.containsKey(mac)
                )
                onUpdate(results.values.sortedWith(
                    compareByDescending<ScannedSpeaker> { it.isPartyBox }.thenByDescending { it.rssi }
                ))
            }

            override fun onScanFailed(errorCode: Int) {
                onFinished()
            }
        }

        callback = cb
        runCatching {
            scanner.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                cb
            )
        }.onFailure { onFinished(); return }

        handler.postDelayed({ stop(); onFinished() }, SCAN_DURATION_MS)
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        handler.removeCallbacksAndMessages(null)
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        runCatching { manager?.adapter?.bluetoothLeScanner?.stopScan(cb) }
    }

    companion object {
        /** Identifiant de service diffuse par les enceintes PartyBox. */
        val PARTYBOX_SERVICE: ParcelUuid =
            ParcelUuid(UUID.fromString("0000fddf-0000-1000-8000-00805f9b34fb"))

        const val SCAN_DURATION_MS = 12_000L
    }
}
