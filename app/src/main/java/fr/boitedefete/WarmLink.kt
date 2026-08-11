package fr.boitedefete

import android.bluetooth.BluetoothAdapter
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Liaison BLE gardee ouverte pendant que l'application est a l'ecran.
 *
 * Etablir une connexion prend une a deux secondes. C'est negligeable pour une
 * sequence complete, mais c'est tout le delai ressenti quand on touche un
 * reglage rapide : le premier appui semblait lent, les suivants immediats.
 * Garder la liaison sous la main pendant que l'utilisateur regarde l'ecran
 * supprime cette asymetrie.
 *
 * Elle est relachee des que l'ecran est quitte, et avant toute sequence
 * complete : deux clients GATT vers la meme enceinte ne serviraient a rien.
 */
object WarmLink {

    private val mutex = Mutex()
    private var link: SpeakerLink? = null
    private var mac: String? = null

    /**
     * Execute [block] avec une liaison ouverte vers [device].
     * La liaison est conservee ensuite, prete pour l'appel suivant.
     */
    suspend fun <T> use(
        context: Context,
        adapter: BluetoothAdapter,
        device: Speaker,
        block: suspend (SpeakerLink) -> T
    ): T = mutex.withLock {
        val existing = link?.takeIf { mac == device.mac }
        val active = existing ?: withTimeout(Config.CONNECT_TIMEOUT_MS) {
            SpeakerLink.open(context, adapter, device.mac)
        }.also {
            link = it
            mac = device.mac
        }

        runCatching { block(active) }.getOrElse { error ->
            // Une liaison qui a echoue ne sera pas meilleure la prochaine fois.
            discard()
            throw error
        }
    }

    /** Ouvre la liaison a l'avance, en silence si l'enceinte ne repond pas. */
    suspend fun warm(context: Context, adapter: BluetoothAdapter, device: Speaker) {
        runCatching { use(context, adapter, device) { } }
    }

    /** Ferme la liaison. A appeler en quittant l'ecran ou avant une sequence. */
    suspend fun release() = mutex.withLock { discard() }

    private fun discard() {
        link?.close()
        link = null
        mac = null
    }
}
