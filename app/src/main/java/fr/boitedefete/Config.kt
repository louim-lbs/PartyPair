package fr.boitedefete

/**
 * Reglages techniques, communs a toutes les installations.
 *
 * Les adresses des enceintes sont demandees au premier lancement et conservees
 * sur le telephone (voir Settings.kt).
 */
object Config {

    /** Delai entre l'ecriture sur la secondaire et sur la principale. */
    const val INTER_WRITE_DELAY_MS = 250L

    /** Temps laisse aux enceintes pour etablir la liaison stereo. */
    const val LINK_SETTLE_MS = 4_000L

    /** Delai maximal pour ouvrir une connexion BLE a une enceinte. */
    const val CONNECT_TIMEOUT_MS = 15_000L

    /**
     * Delai maximal pour qu'une enceinte reponde apres sa sortie de veille.
     * Une enceinte endormie accepte la connexion bien avant d'etre operationnelle.
     */
    const val READY_TIMEOUT_MS = 20_000L

    /** Delai d'attente de la connexion audio de l'enceinte principale. */
    const val AUDIO_TIMEOUT_MS = 25_000L

    /** Tentatives de connexion avant d'abandonner. */
    const val CONNECT_ATTEMPTS = 2

    /** Delai maximal pour relever le volume avant le fondu. */
    const val VOLUME_READ_MS = 1_500L

    /** Paliers du fondu sonore avant la mise en veille. */
    const val FADE_STEPS = 12
    const val FADE_STEP_MS = 180L

    /** Delai pour interroger Android sur ses connexions audio. */
    const val PROBE_TIMEOUT_MS = 4_000L

    /** Duree maximale d'attente du retour du Bluetooth apres le mode sommeil. */
    const val BLUETOOTH_WAIT_MS = 180_000L

    /** Repit laisse a la pile Bluetooth une fois rallumee. */
    const val BLUETOOTH_SETTLE_MS = 8_000L

    /** Decompte avant l'ouverture de l'application musicale, en secondes. */
    const val MUSIC_COUNTDOWN_SECONDS = 5
}
