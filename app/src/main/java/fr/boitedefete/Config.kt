package fr.boitedefete

/**
 * Reglages techniques, communs a toutes les installations.
 *
 * Les adresses des enceintes ne sont pas ici : elles sont demandees au premier
 * lancement et conservees sur le telephone (voir Settings.kt).
 */
object Config {

    /** Delai entre l'ecriture sur la secondaire et sur la principale. */
    const val INTER_WRITE_DELAY_MS = 250L

    /** Temps laisse aux enceintes pour etablir la liaison stereo. */
    const val LINK_SETTLE_MS = 4_000L

    /** Delai maximal pour ouvrir une connexion BLE a une enceinte. */
    const val CONNECT_TIMEOUT_MS = 15_000L
}
