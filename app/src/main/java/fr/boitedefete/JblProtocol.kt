package fr.boitedefete

import java.util.UUID

/**
 * Protocole BLE proprietaire des enceintes JBL PartyBox.
 *
 * Trame : AA <commande> <longueur du payload> <payload...>
 * Les reponses arrivent en notification sur la caracteristique RX, sous la
 * forme : AA <commande> <longueur> <statut> <tag><longueur><valeur>...
 *
 * Voir docs/PROTOCOL.md pour le detail.
 */
object JblProtocol {

    val SERVICE: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0000")

    /** Caracteristique d'ecriture (Write Without Response). */
    val TX: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0002")

    /** Notifications. Sans descripteur CCCD : l'enceinte pousse d'elle-meme. */
    val RX: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0001")

    const val IDENTIFIER = 0xAA.toByte()

    /** Volume maximal accepte par l'enceinte (l'application officielle borne a 32). */
    const val MAX_VOLUME = 32

    // Commandes
    private const val CMD_DEVICE_ACTION = 0x03.toByte()
    private const val CMD_REQ_DEV_INFO = 0x11.toByte()
    private const val CMD_SET_DEV_INFO = 0x13.toByte()
    private const val CMD_REQ_PLAYER_INFO = 0x41.toByte()
    private const val CMD_SET_PLAYER_INFO = 0x43.toByte()
    private const val CMD_SET_BASS_BOOST = 0x63.toByte()
    private const val CMD_SET_PHONE_MAC = 0x84.toByte()

    /** Reponses attendues. */
    const val RESP_DEV_INFO = 0x12.toByte()
    const val RESP_PLAYER_INFO = 0x42.toByte()

    /** Adresse de l'enceinte partenaire, annoncee spontanement a la connexion. */
    const val NOTIFY_SECONDARY_ADDRESS = 0x85.toByte()

    /** Valeur renvoyee quand aucune enceinte partenaire n'est memorisee. */
    const val NO_PARTNER = "FF:FF:FF:FF:FF:FF"

    // Tags TLV
    const val TAG_CHANNEL = 0x35
    const val TAG_PARTY_CONNECT = 0x39
    const val TAG_VOLUME = 0x42
    private const val TAG_VOLUME_PRIMARY = 0x46
    private const val TAG_VOLUME_SECONDARY = 0x47

    // Valeurs de PartyConnectStatus
    const val PARTY_OFF = 0x00.toByte()
    private const val PARTY_CONNECTING = 0x01.toByte()
    const val PARTY_CONNECTED = 0x02.toByte()

    private fun frame(command: Byte, payload: ByteArray): ByteArray =
        byteArrayOf(IDENTIFIER, command, payload.size.toByte()) + payload

    /** Interroge l'enceinte. Sert de sonde : une reponse signifie qu'elle est prete. */
    val REQ_DEVICE_INFO: ByteArray = frame(CMD_REQ_DEV_INFO, byteArrayOf())

    /** Demande l'etat du lecteur, dont le volume courant. */
    val REQ_PLAYER_INFO: ByteArray = frame(CMD_REQ_PLAYER_INFO, byteArrayOf())

    /** Allume l'enceinte. En pratique la connexion BLE suffit deja a la reveiller. */
    val POWER_ON: ByteArray = frame(CMD_DEVICE_ACTION, byteArrayOf(0x05))

    /** Remet l'enceinte en veille. */
    val POWER_OFF: ByteArray = frame(CMD_DEVICE_ACTION, byteArrayOf(0x04))

    /** Demande l'etablissement de la liaison stereo. A envoyer aux deux enceintes. */
    val TWS_LINK: ByteArray = frame(
        CMD_SET_DEV_INFO,
        byteArrayOf(0x00, TAG_PARTY_CONNECT.toByte(), 0x01, PARTY_CONNECTING)
    )

    /** Rompt la liaison stereo. A envoyer aux deux enceintes. */
    val TWS_UNLINK: ByteArray = frame(
        CMD_SET_DEV_INFO,
        byteArrayOf(0x00, TAG_PARTY_CONNECT.toByte(), 0x01, PARTY_OFF)
    )

    /** Canaux d'une paire stereo. */
    const val CHANNEL_NONE = 0x00.toByte()
    const val CHANNEL_LEFT = 0x01.toByte()
    const val CHANNEL_RIGHT = 0x02.toByte()

    /** Attribue un canal a une enceinte. */
    fun setChannel(channel: Byte): ByteArray = frame(
        CMD_SET_DEV_INFO,
        byteArrayOf(0x00, TAG_CHANNEL.toByte(), 0x01, channel)
    )

    /**
     * Renforcement des graves.
     *
     * L'application officielle expose trois etats, transmis par leur rang :
     * arret, puis deux intensites.
     */
    fun setBassBoost(level: Int): ByteArray =
        frame(CMD_SET_BASS_BOOST, byteArrayOf(level.coerceIn(0, 2).toByte()))

    /** Regle le volume general, de 0 a [MAX_VOLUME]. */
    fun setVolume(level: Int): ByteArray = frame(
        CMD_SET_PLAYER_INFO,
        byteArrayOf(0x00, TAG_VOLUME.toByte(), 0x01, clamp(level))
    )

    /** Volume de l'enceinte principale d'une paire stereo. */
    fun setPrimaryVolume(level: Int): ByteArray = frame(
        CMD_SET_PLAYER_INFO,
        byteArrayOf(0x00, TAG_VOLUME_PRIMARY.toByte(), 0x01, clamp(level))
    )

    /** Volume de l'enceinte secondaire d'une paire stereo. */
    fun setSecondaryVolume(level: Int): ByteArray = frame(
        CMD_SET_PLAYER_INFO,
        byteArrayOf(0x00, TAG_VOLUME_SECONDARY.toByte(), 0x01, clamp(level))
    )

    private fun clamp(level: Int): Byte = level.coerceIn(0, MAX_VOLUME).toByte()

    /**
     * Indique a l'enceinte l'adresse Bluetooth classique a laquelle se connecter.
     * C'est ce qui lui fait rejoindre le telephone (ou le dongle) pour l'audio.
     */
    fun connectTo(macAddress: String): ByteArray {
        // On ne se fie pas aux separateurs : seuls les chiffres hexadecimaux
        // comptent. Une adresse saisie avec un point-virgule, un tiret ou une
        // espace reste ainsi exploitable.
        val hex = macAddress.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        require(hex.length == 12) { "Adresse Bluetooth invalide : $macAddress" }
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return frame(CMD_SET_PHONE_MAC, bytes)
    }

    /**
     * Extrait les champs TLV d'une reponse.
     *
     * Le premier octet du payload est un statut, les suivants une suite de
     * triplets tag / longueur / valeur.
     */
    fun parseFields(frame: ByteArray): Map<Int, ByteArray> {
        if (frame.size < 4 || frame[0] != IDENTIFIER) return emptyMap()
        val fields = mutableMapOf<Int, ByteArray>()
        var i = 4 // AA, commande, longueur, statut
        while (i + 1 < frame.size) {
            val tag = frame[i].toInt() and 0xFF
            val length = frame[i + 1].toInt() and 0xFF
            if (i + 2 + length > frame.size) break
            fields[tag] = frame.copyOfRange(i + 2, i + 2 + length)
            i += 2 + length
        }
        return fields
    }

    /** Numero de commande d'une trame recue, ou null si elle est mal formee. */
    fun commandOf(frame: ByteArray): Byte? =
        if (frame.size >= 2 && frame[0] == IDENTIFIER) frame[1] else null
}
