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
        // Normalisee aussi a la lecture : une adresse enregistree avant la mise
        // en forme automatique, avec un separateur errone, se repare d'elle-meme.
        get() = MacFormat.normalize(prefs.getString(KEY_PHONE_MAC, "").orEmpty())
        // commit() plutot que apply() : la configuration ne doit pas se perdre
        // si le systeme arrete le processus juste apres la saisie.
        set(value) {
            prefs.edit().putString(KEY_PHONE_MAC, MacFormat.normalize(value)).commit()
        }

    /** Application musicale ouverte depuis l'ecran principal. */
    var musicApp: String?
        get() = prefs.getString(KEY_MUSIC_APP, null)
        set(value) {
            prefs.edit().putString(KEY_MUSIC_APP, value).commit()
        }

    /** Adresse de la playlist ouverte avec l'application musicale. Facultative. */
    var musicUrl: String
        get() = prefs.getString(KEY_MUSIC_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_MUSIC_URL, value.trim()).apply()

    /** Volume applique au reveil, de 0 a 32. Negatif signifie « ne pas toucher ». */
    var wakeVolume: Int
        get() = prefs.getInt(KEY_WAKE_VOLUME, DEFAULT_WAKE_VOLUME)
        set(value) = prefs.edit().putInt(KEY_WAKE_VOLUME, value).apply()

    /** Volume releve avant la mise en veille, restitue au reveil suivant. */
    var lastVolume: Int
        get() = prefs.getInt(KEY_LAST_VOLUME, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_VOLUME, value).apply()

    /**
     * Equilibre entre les deux enceintes, de -8 (tout sur la principale) a +8
     * (tout sur la secondaire). Zero laisse les deux au meme niveau.
     */
    var balance: Int
        get() = prefs.getInt(KEY_BALANCE, 0)
        set(value) = prefs.edit().putInt(KEY_BALANCE, value.coerceIn(-MAX_BALANCE, MAX_BALANCE)).apply()

    /** Canal releve sur l'enceinte principale : 1 gauche, 2 droite, 0 inconnu. */
    var primaryChannel: Int
        get() = prefs.getInt(KEY_PRIMARY_CHANNEL, 0)
        set(value) = prefs.edit().putInt(KEY_PRIMARY_CHANNEL, value).apply()

    /** Canal releve sur l'enceinte secondaire. */
    var secondaryChannel: Int
        get() = prefs.getInt(KEY_SECONDARY_CHANNEL, 0)
        set(value) = prefs.edit().putInt(KEY_SECONDARY_CHANNEL, value).apply()

    /** Renforcement des graves : 0 arret, 1 profond, 2 percutant. */
    var bassBoost: Int
        get() = prefs.getInt(KEY_BASS_BOOST, 0)
        set(value) = prefs.edit().putInt(KEY_BASS_BOOST, value.coerceIn(0, 2)).apply()

    /** Date du dernier controle de mise a jour, pour ne pas interroger le depot a chaque ouverture. */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    /** Confirmation avant la mise en veille, pour parer un appui accidentel. */
    var confirmStandby: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_STANDBY, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_STANDBY, value).apply()

    /** Echeance de mise en veille automatique, ou 0 si aucune n'est posee. */
    var sleepAt: Long
        get() = prefs.getLong(KEY_SLEEP_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_SLEEP_AT, value).apply()

    /** Declenchement automatique avant l'alarme du telephone. */
    var alarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALARM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_ENABLED, value).apply()

    /**
     * Bornes horaires du reveil, en heures pleines.
     *
     * Android ne distingue pas l'alarme du mode sommeil des autres : rien dans
     * l'information fournie ne dit d'ou vient l'alarme. On se rabat donc sur
     * une plage horaire, qui ecarte les minuteries de cuisine et les rappels
     * de la journee.
     */
    var alarmFromHour: Int
        get() = prefs.getInt(KEY_ALARM_FROM, 4)
        set(value) = prefs.edit().putInt(KEY_ALARM_FROM, value.coerceIn(0, 23)).apply()

    var alarmToHour: Int
        get() = prefs.getInt(KEY_ALARM_TO, 11)
        set(value) = prefs.edit().putInt(KEY_ALARM_TO, value.coerceIn(0, 23)).apply()

    /** Avance, en minutes, sur l'alarme du telephone. */
    var alarmLeadMinutes: Int
        get() = prefs.getInt(KEY_ALARM_LEAD, 1)
        set(value) = prefs.edit().putInt(KEY_ALARM_LEAD, value.coerceIn(0, 30)).apply()

    val isConfigured: Boolean
        get() = primary != null && secondary != null

    /**
     * Configuration sous forme de texte, a copier ailleurs.
     * Ne contient que ce qui serait penible a ressaisir.
     */
    fun export(): String = buildList {
        primary?.let { add("principale=${it.name}|${it.mac}") }
        secondary?.let { add("secondaire=${it.name}|${it.mac}") }
        if (phoneMac.isNotBlank()) add("telephone=$phoneMac")
        musicApp?.let { add("musique=$it") }
        if (musicUrl.isNotBlank()) add("playlist=$musicUrl")
        add("volume=$wakeVolume")
        add("equilibre=$balance")
        add("graves=$bassBoost")
    }.joinToString("\n")

    /**
     * Restaure une configuration exportee. Les lignes inconnues sont ignorees,
     * et une entree absente laisse la valeur en place.
     *
     * @return vrai si au moins une enceinte a ete reconnue.
     */
    fun restore(text: String): Boolean {
        var recognized = false
        text.lineSequence().forEach { line ->
            val (key, value) = line.split("=", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            when (key.trim()) {
                "principale" -> parseSpeaker(value)?.let { primary = it; recognized = true }
                "secondaire" -> parseSpeaker(value)?.let { secondary = it; recognized = true }
                "telephone" -> phoneMac = value
                "musique" -> musicApp = value.trim()
                "playlist" -> musicUrl = value
                "volume" -> value.trim().toIntOrNull()?.let { wakeVolume = it }
                "equilibre" -> value.trim().toIntOrNull()?.let { balance = it }
                "graves" -> value.trim().toIntOrNull()?.let { bassBoost = it }
            }
        }
        return recognized
    }

    private fun parseSpeaker(value: String): Speaker? {
        val parts = value.split("|", limit = 2)
        if (parts.size != 2) return null
        val mac = MacFormat.normalize(parts[1])
        if (!MacFormat.isComplete(mac)) return null
        return Speaker(parts[0].trim(), mac)
    }

    /** Efface toute la configuration. Reserve a une action explicite. */
    fun clear() {
        prefs.edit().clear().commit()
    }

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
        // Ecriture synchrone : perdre l'identite des enceintes obligerait a tout
        // reconfigurer, ce qui est bien plus penalisant qu'une milliseconde d'attente.
        editor.commit()
    }

    companion object {
        private const val KEY_PRIMARY_MAC = "primary_mac"
        private const val KEY_PRIMARY_NAME = "primary_name"
        private const val KEY_SECONDARY_MAC = "secondary_mac"
        private const val KEY_SECONDARY_NAME = "secondary_name"
        private const val KEY_PHONE_MAC = "phone_mac"
        private const val KEY_MUSIC_APP = "music_app"
        private const val KEY_MUSIC_URL = "music_url"
        private const val KEY_WAKE_VOLUME = "wake_volume"
        private const val KEY_LAST_VOLUME = "last_volume"
        private const val KEY_ALARM_ENABLED = "alarm_enabled"
        private const val KEY_ALARM_LEAD = "alarm_lead"
        private const val KEY_ALARM_FROM = "alarm_from"
        private const val KEY_ALARM_TO = "alarm_to"
        private const val KEY_BALANCE = "balance"
        private const val KEY_BASS_BOOST = "bass_boost"
        private const val KEY_SLEEP_AT = "sleep_at"
        private const val KEY_CONFIRM_STANDBY = "confirm_standby"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_PRIMARY_CHANNEL = "primary_channel"
        private const val KEY_SECONDARY_CHANNEL = "secondary_channel"

        /** Ecart maximal entre les deux enceintes, sur l'echelle 0-32. */
        const val MAX_BALANCE = 8

        /** Un tiers du maximum : audible sans faire sursauter. */
        const val DEFAULT_WAKE_VOLUME = 10

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
