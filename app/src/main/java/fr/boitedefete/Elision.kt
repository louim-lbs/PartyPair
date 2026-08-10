package fr.boitedefete

/**
 * Accord des libelles avec le nom des enceintes.
 *
 * « Réveil de Cécile » mais « Réveil d'Anna » : le francais elide la voyelle
 * devant une autre voyelle. Les noms propres d'origine germanique prennent
 * traditionnellement un h aspire, qui interdit l'elision — « de Hambourg » —
 * mais l'usage courant elide volontiers, et c'est ce qui sonne le plus naturel
 * a l'oreille. Modifier [ELIDE_BEFORE] pour revenir a la regle stricte.
 */
object Elision {

    /**
     * Prefixe le nom si la langue courante l'exige.
     * Les autres langues recoivent le nom nu : « Waking Hildegarde ».
     */
    fun subject(name: String): String =
        if (java.util.Locale.getDefault().language == "fr") of(name) else name.trim()

    /** Retirer 'h' de cette chaine pour appliquer la regle du h aspire. */
    private const val ELIDE_BEFORE = "aeiouyàâäéèêëîïôöùûüh"

    /** Renvoie « de Cécile » ou « d'Hildegarde » selon l'initiale. */
    fun of(name: String): String {
        val clean = name.trim()
        val first = clean.firstOrNull()?.lowercaseChar() ?: return clean
        return if (first in ELIDE_BEFORE) "d'$clean" else "de $clean"
    }
}
