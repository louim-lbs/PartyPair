package fr.boitedefete

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Suit la liaison avec l'enceinte principale.
 *
 * Garde le widget et l'ecran fideles a la realite, meme quand l'enceinte
 * s'eteint ou s'allume depuis son propre bouton — sans jamais lui emettre quoi
 * que ce soit, donc sans risque de la reveiller.
 *
 * Depuis Android 8, l'annonce audio (A2DP) n'atteint plus un recepteur declare
 * dans le manifeste : seules les annonces de liaison bas niveau (ACL) passent
 * encore. Celles-ci couvrent aussi nos propres connexions BLE ; on ne se fie
 * donc pas a l'evenement lui-meme, on verifie ensuite l'etat audio reel.
 */
class AudioLinkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            ACTION_A2DP_STATE -> when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                BluetoothProfile.STATE_CONNECTED -> true
                BluetoothProfile.STATE_DISCONNECTED -> false
                else -> return
            }
            else -> return
        }

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val primaryMac = Settings(context).primary?.mac ?: return
        if (device?.address?.equals(primaryMac, ignoreCase = true) != true) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(MAX_WAIT_MS) {
                    // L'audio suit la liaison de quelques secondes a l'allumage,
                    // et la precede de peu a l'extinction : on laisse retomber.
                    repeat(ATTEMPTS) {
                        delay(STEP_MS)
                        val on = PartyController(app).isAlreadyOn()
                        PartyService.applyRealState(app, on)
                        if (on == connected) return@withTimeoutOrNull
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Valeur de BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED. */
        const val ACTION_A2DP_STATE =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"

        const val STEP_MS = 1_500L
        const val ATTEMPTS = 5

        /** Un recepteur asynchrone dispose d'une dizaine de secondes. */
        const val MAX_WAIT_MS = 9_000L
    }
}
