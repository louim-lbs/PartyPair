package fr.boitedefete

/**
 * Mise en forme des adresses Bluetooth.
 *
 * Accepte tout ce qu'on peut coller : avec ou sans deux-points, en tirets, en
 * minuscules, avec des espaces. Seuls les chiffres hexadecimaux sont retenus,
 * puis regroupes par paires.
 */
object MacFormat {

    private const val HEX_DIGITS = 12

    /** `1ce61deda220`, `1c-e6-1d-ed-a2-20` ou `1C E6 1D…` donnent tous `1C:E6:1D:ED:A2:20`. */
    fun normalize(input: String): String = input
        .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        .uppercase()
        .take(HEX_DIGITS)
        .chunked(2)
        .joinToString(":")

    /** Vrai si l'adresse comporte bien six octets. */
    fun isComplete(value: String): Boolean =
        value.count { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' } == HEX_DIGITS
}
