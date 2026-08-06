package fr.boitedefete

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/**
 * Ouvre l'application musicale et tente de lancer la lecture.
 *
 * Aucune interface publique ne permet de demander a une application musicale de
 * jouer un morceau precis. On procede donc en deux temps : ouvrir la playlist
 * si une adresse est connue, puis simuler la touche « lecture » d'un casque, ce
 * que la plupart des lecteurs honorent en reprenant la derniere ecoute.
 */
object MusicLauncher {

    /** Ouvre l'application, et sa playlist si une adresse est enregistree. */
    fun open(context: Context, settings: Settings): Boolean {
        val packageName = settings.musicApp ?: return false
        val url = settings.musicUrl

        val intent = if (url.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        } ?: return false

        return runCatching { context.startActivity(intent) }
            .recoverCatching {
                // L'adresse n'est pas gérée par l'application : on l'ouvre simplement.
                val fallback = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } ?: return false
                context.startActivity(fallback)
            }
            .isSuccess
    }

    /**
     * Simule un appui sur la touche « lecture ».
     *
     * Fonctionne sans ouvrir de fenetre, ce qui la rend utilisable depuis une
     * alarme ou une routine, quand l'application ne peut rien afficher.
     */
    fun play(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            runCatching {
                audio.dispatchMediaKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
        }
    }
}
