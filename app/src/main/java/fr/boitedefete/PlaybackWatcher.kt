package fr.boitedefete

import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Observe la lecture en cours pour differer une extinction.
 *
 * Couper au milieu d'un morceau presque fini est desagreable : quand il ne
 * reste que quelques instants, mieux vaut le laisser s'achever. Au-dela, la
 * musique est encore bien engagee et l'extinction n'a pas a attendre.
 *
 * Tout repose sur les sessions media, qu'Android ne laisse lire qu'aux services
 * d'ecoute de notifications. Sans cette autorisation, on ne sait rien et on
 * eteint sans attendre.
 */
object PlaybackWatcher {

    /** En deca, on laisse le morceau finir. Au-dela, on coupe tout de suite. */
    private val GRACE_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(3)

    /** Attente maximale quand la duree du morceau reste inconnue. */
    private val BLIND_WAIT_MS = TimeUnit.MINUTES.toMillis(3)

    /** Repit accorde a une pause : un appel telephonique n'est pas une fin d'ecoute. */
    private val PAUSE_GRACE_MS = TimeUnit.SECONDS.toMillis(30)

    private const val POLL_MS = 1_000L

    /** Ce que l'appelant doit faire de l'echeance. */
    enum class Verdict {
        /** Rien a attendre : couper maintenant. */
        STOP_NOW,
        /** Le morceau touche a sa fin : il vaut la peine d'etre laisse finir. */
        WAIT_FOR_TRACK
    }

    /** Decide, a l'echeance, s'il faut patienter ou couper. */
    fun verdict(context: Context): Verdict {
        val state = controller(context)?.playbackState ?: return Verdict.STOP_NOW
        if (state.state != PlaybackState.STATE_PLAYING) return Verdict.STOP_NOW

        val remaining = remainingMs(context) ?: return Verdict.STOP_NOW
        return if (remaining < GRACE_THRESHOLD_MS) Verdict.WAIT_FOR_TRACK else Verdict.STOP_NOW
    }

    /**
     * Attend la fin du morceau en cours.
     *
     * Changer de titre pendant l'attente relance le compte a rebours sur le
     * nouveau, quelle que soit sa duree : c'est un geste delibere, l'envie d'un
     * dernier morceau avant d'arreter. Le garde-fou ne s'applique donc qu'a
     * l'ignorance — quand la duree reste inconnue.
     *
     * @param onTrackChanged appele lorsque le morceau change, pour rafraichir
     *   l'affichage.
     */
    suspend fun awaitTrackEnd(context: Context, onTrackChanged: () -> Unit = {}) {
        var currentTrack = trackId(context)
        var blindDeadline = System.currentTimeMillis() + BLIND_WAIT_MS
        var pausedSince: Long? = null

        while (true) {
            val controller = controller(context) ?: return
            val state = controller.playbackState ?: return

            val track = trackId(context)
            if (track != currentTrack) {
                // Nouveau morceau : on repart sur sa propre echeance.
                currentTrack = track
                blindDeadline = System.currentTimeMillis() + BLIND_WAIT_MS
                pausedSince = null
                onTrackChanged()
            }

            when (state.state) {
                PlaybackState.STATE_PLAYING -> {
                    pausedSince = null
                    val remaining = remainingMs(context)
                    if (remaining != null) {
                        if (remaining <= POLL_MS) return
                        // La duree est connue : le garde-fou n'a plus lieu d'etre.
                        blindDeadline = System.currentTimeMillis() + remaining + POLL_MS
                    } else if (System.currentTimeMillis() > blindDeadline) {
                        return
                    }
                }

                PlaybackState.STATE_PAUSED -> {
                    // Une pause subie ne doit pas eteindre : on laisse le temps
                    // de reprendre avant de conclure.
                    val since = pausedSince ?: System.currentTimeMillis().also { pausedSince = it }
                    if (System.currentTimeMillis() - since > PAUSE_GRACE_MS) return
                }

                else -> return
            }

            delay(POLL_MS)
        }
    }

    /** Temps restant sur le morceau en cours, ou null s'il n'est pas connu. */
    private fun remainingMs(context: Context): Long? {
        val controller = controller(context) ?: return null
        val duration = controller.metadata
            ?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
            ?.takeIf { it > 0 } ?: return null
        val position = controller.playbackState?.position?.takeIf { it >= 0 } ?: return null
        return (duration - position).takeIf { it >= 0 }
    }

    /** Identifie le morceau, pour reperer un changement de titre. */
    private fun trackId(context: Context): String? {
        val metadata = controller(context)?.metadata ?: return null
        val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
        return "$title|$artist|$duration".takeIf { it != "||0" }
    }

    /**
     * Lecteur a surveiller.
     *
     * Celui qui joue en priorite ; a defaut celui que l'utilisateur a choisi,
     * qui vient peut-etre d'etre mis en pause.
     */
    private fun controller(context: Context): MediaController? =
        MediaSessions.playingController(context)
            ?: Settings(context).musicApp?.let { MediaSessions.controllerFor(context, it) }
}
