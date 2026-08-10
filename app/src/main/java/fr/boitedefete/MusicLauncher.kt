package fr.boitedefete

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import kotlinx.coroutines.delay

/**
 * Ouvre l'application musicale et lance la lecture.
 *
 * Trois voies, de la plus fidele a la plus approximative :
 *
 *  1. la recherche lancee (`MEDIA_PLAY_FROM_SEARCH`), seule interface publique
 *     qui demande vraiment de jouer quelque chose de precis ;
 *  2. le lien de la playlist, qui l'affiche sans forcement la demarrer ;
 *  3. l'ouverture simple, completee par la touche « lecture » d'un casque.
 *
 * La touche « lecture » reprend la derniere ecoute : elle ne convient que si
 * aucune playlist precise n'a ete demandee, sans quoi elle rejouerait le
 * morceau precedent au lieu du reveil attendu.
 */
object MusicLauncher {

    /** Vrai si un son est deja en cours de lecture sur le telephone. */
    fun isPlaying(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audio?.isMusicActive == true
    }

    /**
     * Enchaine l'ouverture et la lecture.
     *
     * Ne fait rien si une musique tourne deja : interrompre une ecoute en cours
     * pour imposer la playlist du reveil serait la derniere chose a faire.
     */
    suspend fun openAndPlay(context: Context, settings: Settings) {
        if (isPlaying(context)) return
        val packageName = settings.musicApp ?: return

        // Une application peut accepter l'intention sans rien jouer : on juge au
        // resultat plutot qu'a la reponse du systeme.
        if (playFromSearch(context, packageName, settings.playlistName)) {
            delay(5_000)
            if (isPlaying(context)) return
        }

        if (settings.musicUrl.isNotBlank() && openUrl(context, packageName, settings.musicUrl)) {
            delay(4_000)
            if (isPlaying(context)) return
            // La playlist est affichee mais silencieuse : la touche « lecture »
            // demarre ce qui est a l'ecran dans la plupart des lecteurs.
            play(context)
            delay(2_500)
            if (isPlaying(context)) return
        }

        if (!launch(context, packageName)) return
        delay(3_000)
        play(context)
    }

    /**
     * Ouvre simplement l'application musicale.
     *
     * Un appui sur le bouton veut dire « ouvre Deezer », pas « impose-moi la
     * playlist du reveil » : celle-ci n'a de sens qu'au declenchement automatique.
     */
    fun open(context: Context, settings: Settings): Boolean {
        val packageName = settings.musicApp ?: return false
        return launch(context, packageName)
    }

    /**
     * Demande a l'application de jouer une playlist par son nom.
     * C'est la seule voie qui demarre reellement la bonne musique.
     */
    private fun playFromSearch(context: Context, packageName: String, query: String): Boolean {
        if (query.isBlank()) return false
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE)
            putExtra(SearchManager.QUERY, query)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun openUrl(context: Context, packageName: String, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * Simule un appui sur la touche « lecture ».
     * Fonctionne sans ouvrir de fenetre, donc depuis une alarme ou une routine.
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
