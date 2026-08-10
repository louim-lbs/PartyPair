package fr.boitedefete

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/**
 * Ouvre l'application musicale et tente de lancer la lecture.
 *
 * Aucune interface publique ne permet de demander a une application musicale de
 * jouer un morceau precis. On procede donc en deux temps : ouvrir la playlist si
 * une adresse est connue, puis simuler la touche « lecture » d'un casque, ce que
 * la plupart des lecteurs honorent en reprenant la derniere ecoute.
 */
object MusicLauncher {

    /** Ouvre l'application, et sa playlist si une adresse est enregistree. */
    fun open(context: Context, settings: Settings): Boolean {
        val packageName = settings.musicApp ?: return false
        val url = settings.musicUrl

        if (url.isNotBlank()) {
            val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(deepLink) }.isSuccess) return true
            // L'application ne gere pas cette adresse : on l'ouvre simplement.
        }

        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: return false
        return runCatching { context.startActivity(launch) }.isSuccess
    }

    /**
     * Ouvre l'application puis lance la lecture.
     *
     * Le lecteur a besoin d'un instant pour charger la playlist avant d'accepter
     * la commande ; on lui laisse ce temps, et on insiste une seconde fois au
     * cas ou la premiere sollicitation arriverait trop tot.
     */
    suspend fun openAndPlay(context: Context, settings: Settings) {
        if (!open(context, settings)) return
        delay(3_500)
        play(context)
        delay(2_000)
        play(context)
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
