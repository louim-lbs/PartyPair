package fr.boitedefete

import java.util.UUID

/**
 * Protocole BLE proprietaire des enceintes JBL PartyBox.
 *
 * Trame : AA <commande> <longueur du payload> <payload...>
 * Les reponses arrivent en notification sur la caracteristique RX.
 *
 * Voir docs/PROTOCOL.md pour le detail.
 */
object JblProtocol {

    val SERVICE: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0000")

    /** Caracteristique d'ecriture (Write Without Response). */
    val TX: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0002")

    /** Caracteristique de notification. Sans descripteur CCCD : l'enceinte pousse d'elle-meme. */
    val RX: UUID = UUID.fromString("65786365-6c70-6f69-6e74-2e636f6d0001")

    private const val IDENTIFIER = 0xAA.toByte()

    private const val CMD_DEVICE_ACTION = 0x03.toByte()
    private const val CMD_REQ_DEV_INFO = 0x11.toByte()
    private const val CMD_SET_DEV_INFO = 0x13.toByte()
    private const val CMD_SET_PHONE_MAC = 0x84.toByte()

    private const val TAG_PARTY_CONNECT_MODE = 0x39.toByte()

    private const val PARTY_CONNECT_OFF = 0x00.toByte()
    private const val PARTY_CONNECT_CONNECTING = 0x01.toByte()

    private fun frame(command: Byte, payload: ByteArray): ByteArray =
        byteArrayOf(IDENTIFIER, command, payload.size.toByte()) + payload

    /** Interroge l'enceinte. Sert de sonde : une reponse signifie qu'elle est prete. */
    val REQ_DEVICE_INFO: ByteArray = frame(CMD_REQ_DEV_INFO, byteArrayOf())

    /** Allume l'enceinte. En pratique la connexion BLE suffit deja a la reveiller. */
    val POWER_ON: ByteArray = frame(CMD_DEVICE_ACTION, byteArrayOf(0x05))

    /** Remet l'enceinte en veille. */
    val POWER_OFF: ByteArray = frame(CMD_DEVICE_ACTION, byteArrayOf(0x04))

    /** Demande l'etablissement de la liaison stereo. A envoyer aux deux enceintes. */
    val TWS_LINK: ByteArray = frame(
        CMD_SET_DEV_INFO,
        byteArrayOf(0x00, TAG_PARTY_CONNECT_MODE, 0x01, PARTY_CONNECT_CONNECTING)
    )

    /** Rompt la liaison stereo. A envoyer aux deux enceintes. */
    val TWS_UNLINK: ByteArray = frame(
        CMD_SET_DEV_INFO,
        byteArrayOf(0x00, TAG_PARTY_CONNECT_MODE, 0x01, PARTY_CONNECT_OFF)
    )

    /**
     * Indique a l'enceinte l'adresse Bluetooth classique a laquelle se connecter.
     * C'est ce qui lui fait rejoindre le telephone (ou le dongle) pour l'audio.
     */
    fun connectTo(macAddress: String): ByteArray {
        val bytes = macAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
        require(bytes.size == 6) { "Adresse Bluetooth invalide : $macAddress" }
        return frame(CMD_SET_PHONE_MAC, bytes)
    }
}
