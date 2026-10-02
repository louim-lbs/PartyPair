package fr.boitedefete

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Suit la liaison audio avec l'enceinte principale.
 *
 * Android annonce chaque connexion et deconnexion audio. S'y abonner garde le
 * widget et l'ecran fideles a la realite, meme quand l'enceinte s'eteint
 * d'elle-meme ou depuis son propre bouton — sans jamais lui emettre quoi que
 * ce soit, donc sans risque de la reveiller.
 */
class AudioLinkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_A2DP_STATE) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val primaryMac = Settings(context).primary?.mac ?: return
        if (device?.address?.equals(primaryMac, ignoreCase = true) != true) return

        when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
            BluetoothProfile.STATE_CONNECTED -> PartyService.applyRealState(context, on = true)
            BluetoothProfile.STATE_DISCONNECTED -> PartyService.applyRealState(context, on = false)
        }
    }

    private companion object {
        /** Valeur de BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED. */
        const val ACTION_A2DP_STATE =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
    }
}
