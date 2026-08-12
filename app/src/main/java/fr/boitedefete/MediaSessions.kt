package fr.boitedefete

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.provider.Settings as AndroidSettings

/**
 * Acces aux lecteurs en cours.
 *
 * Android ne laisse lire les sessions media qu'aux services d'ecoute de
 * notifications. C'est la seule voie pour savoir qui joue quoi, et surtout pour
 * s'adresser a un lecteur precis plutot que d'envoyer une touche media dans le
 * vide, qui atterrit chez le dernier ayant eu le focus.
 */
object MediaSessions {

    /** Vrai si le systeme nous laisse lire les sessions. */
    fun isAllowed(context: Context): Boolean {
        val enabled = AndroidSettings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        return enabled.contains(context.packageName)
    }

    /** Sessions actives, ou liste vide si l'autorisation manque. */
    private fun sessions(context: Context): List<MediaController> {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return emptyList()
        return runCatching {
            manager.getActiveSessions(ComponentName(context, NotificationListener::class.java))
        }.getOrDefault(emptyList())
    }

    /**
     * Session du lecteur demande.
     *
     * Prendre la premiere de la liste revenait a tirer au sort : plusieurs
     * applications gardent une session ouverte apres une pause, et l'ordre ne
     * dit rien de la derniere utilisee.
     *
     * Un navigateur peut porter la session d'une video : l'identifiant du
     * paquet est celui du navigateur, pas du site. Comparer sur le paquet suffit
     * donc a les distinguer.
     */
    fun controllerFor(context: Context, packageName: String): MediaController? =
        sessions(context).firstOrNull { it.packageName == packageName }

    /**
     * Met en pause tout lecteur autre que celui choisi.
     *
     * Sans cela, un navigateur laisse en pause sur une video reprend a la
     * moindre touche media, et c'est lui qu'on entend au lieu de la musique.
     */
    fun pauseOthers(context: Context, packageName: String) {
        sessions(context)
            .filter { it.packageName != packageName }
            .forEach { runCatching { it.transportControls.pause() } }
    }

    /** Paquets detenant une session, pour diagnostic. */
    fun activePackages(context: Context): List<String> =
        sessions(context).map { it.packageName }

    /** Session en cours de lecture, quel que soit le lecteur. */
    fun playingController(context: Context): MediaController? =
        sessions(context).firstOrNull {
            it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        }

    /**
     * Demande la lecture au lecteur voulu, sans passer par la touche media.
     *
     * @return vrai si la commande a pu etre adressee.
     */
    fun play(context: Context, packageName: String): Boolean {
        val controller = controllerFor(context, packageName) ?: return false
        return runCatching { controller.transportControls.play() }.isSuccess
    }
}
