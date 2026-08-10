package fr.boitedefete

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * Choix de la langue de l'application, independamment de celle du telephone.
 *
 * Android 13 a introduit un reglage par application ; avant cette version, la
 * langue suit obligatoirement celle du systeme et le choix n'est pas propose.
 */
object AppLanguage {

    /** Langues proposees. Une etiquette vide signifie « suivre le systeme ». */
    val CHOICES = listOf("", "fr", "en")

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Code de la langue retenue, ou chaine vide si l'application suit le systeme. */
    fun current(context: Context): String {
        if (!isSupported) return ""
        val manager = context.getSystemService(LocaleManager::class.java) ?: return ""
        return runCatching {
            manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()
        }.getOrDefault("")
    }

    /** Applique la langue. Le systeme recree l'ecran dans la foulee. */
    fun set(context: Context, language: String) {
        if (!isSupported) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        runCatching {
            manager.applicationLocales = if (language.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language)
            }
        }
    }
}
