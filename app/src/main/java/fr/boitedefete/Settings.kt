package fr.boitedefete

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.provider.Settings as AndroidSettings

/** Une enceinte telle que l'utilisateur l'a designee. */
data class Speaker(val name: String, val mac: String)

/** Une application musicale installee sur le telephone. */
data class MusicApp(val name: String, val packageName: String)

/**
 * Reglages propres a l'installation, saisis au premier lancement.
 *
 * Ils sont stockes dans les preferences de l'application : ils survivent aux
 * mises a jour, et sont repris par la sauvegarde Android en cas de changement
 * de telephone. Seule une desinstallation les efface.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("party-pair", Context.MODE_PRIVATE)

    var primary: Speaker?
        get() = read(KEY_PRIMARY_MAC, KEY_PRIMARY_NAME)
        set(value) = write(KEY_PRIMARY_MAC, KEY_PRIMARY_NAME, value)

    var secondary: Speaker?
        get() = read(KEY_SECONDARY_MAC, KEY_SECONDARY_NAME)
        set(value) = write(KEY_SECONDARY_MAC, KEY_SECONDARY_NAME, value)

    /** Adresse Bluetooth du telephone, transmise a l'enceinte principale. */
    var phoneMac: String
        get() = prefs.getString(KEY_PHONE_MAC, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PHONE_MAC, value.uppercase()).apply()

    /** Application musicale ouverte depuis l'ecran principal. */
    var musicApp: String?
        get() = prefs.getString(KEY_MUSIC_APP, null)
        set(value) = prefs.edit().putString(KEY_MUSIC_APP, value).apply()

    val isConfigured: Boolean
        get() = primary != null && secondary != null

    fun clear() = prefs.edit().clear().apply()

    private fun read(macKey: String, nameKey: String): Speaker? {
        val mac = prefs.getString(macKey, null) ?: return null
        return Speaker(prefs.getString(nameKey, mac).orEmpty(), mac)
    }

    private fun write(macKey: String, nameKey: String, speaker: Speaker?) {
        val editor = prefs.edit()
        if (speaker == null) {
            editor.remove(macKey)
            editor.remove(nameKey)
        } else {
            editor.putString(macKey, speaker.mac.uppercase())
            editor.putString(nameKey, speaker.name)
        }
        editor.apply()
    }

    companion object {
        private const val KEY_PRIMARY_MAC = "primary_mac"
        private const val KEY_PRIMARY_NAME = "primary_name"
        private const val KEY_SECONDARY_MAC = "secondary_mac"
        private const val KEY_SECONDARY_NAME = "secondary_name"
        private const val KEY_PHONE_MAC = "phone_mac"
        private const val KEY_MUSIC_APP = "music_app"

        /** Applications musicales installees, pour le bouton de lecture. */
        fun musicApps(context: Context): List<MusicApp> {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_APP_MUSIC)
            return runCatching {
                pm.queryIntentActivities(intent, 0)
                    .map {
                        MusicApp(
                            it.loadLabel(pm).toString(),
                            it.activityInfo.packageName
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.name.lowercase() }
            }.getOrDefault(emptyList())
        }

        /** Enceintes deja appairees, proposees au choix lors de la configuration. */
        @SuppressLint("MissingPermission")
        fun pairedDevices(context: Context): List<Speaker> {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: return emptyList()
            return runCatching {
                adapter.bondedDevices
                    .map { Speaker(it.name ?: it.address, it.address) }
                    .sortedBy { it.name.lowercase() }
            }.getOrDefault(emptyList())
        }

        /**
         * Tente de lire l'adresse Bluetooth du telephone.
         *
         * Android ne l'expose plus officiellement depuis la version 6. Cette
         * lecture fonctionne encore sur une partie des appareils ; sinon il faut
         * la saisir a la main.
         */
        fun detectPhoneMac(context: Context): String? = runCatching {
            AndroidSettings.Secure.getString(context.contentResolver, "bluetooth_address")
                ?.takeIf { it.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) }
                ?.uppercase()
                ?.takeIf { it != "02:00:00:00:00:00" }
        }.getOrNull()
    }
}
