package fr.boitedefete

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

    private const val CHANNEL_ID = "music"
    private const val NOTIFICATION_ID = 5

    /**
     * Invite a lancer la musique d'un geste.
     *
     * Toucher une notification autorise l'ouverture d'une application, la ou
     * l'arriere-plan seul ne le permet pas.
     */
    private fun notifyManualStart(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_music),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, TriggerActivity::class.java)
                .setAction(TriggerActivity.ACTION_PLAY_MUSIC)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.music_manual_title))
            .setContentText(context.getString(R.string.music_manual_text))
            .setSmallIcon(R.drawable.ic_driver)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setFullScreenIntent(open, true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

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
    /**
     * Vrai si l'application peut ouvrir une fenetre.
     *
     * Depuis Android 10, une application en arriere-plan n'a pas le droit de
     * lancer une activite : declenchee par une alarme, la sequence ne peut donc
     * pas ouvrir le lecteur, et doit se contenter de la touche « lecture ».
     */
    private var foreground = false

    /** A appeler quand une fenetre de l'application est visible, ou ne l'est plus. */
    fun setForeground(visible: Boolean) {
        foreground = visible
    }

    suspend fun openAndPlay(context: Context, settings: Settings) {
        if (isPlaying(context)) return
        val packageName = settings.musicApp ?: return

        if (!foreground) {
            // Sans fenetre visible, aucune application ne peut etre ouverte.
            // La touche « lecture » reste la seule voie, et elle suffit si le
            // lecteur etait deja pose sur la bonne playlist.
            play(context)
            delay(2_500)
            if (!isPlaying(context)) notifyManualStart(context)
            return
        }

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
