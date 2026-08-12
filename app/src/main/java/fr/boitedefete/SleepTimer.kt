package fr.boitedefete

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

/**
 * Mise en veille differee.
 *
 * Le fondu sonore etant deja en place, il suffit de programmer l'echeance :
 * la musique s'eteindra d'elle-meme, sans coupure brutale.
 *
 * Une notification persistante affiche le decompte et permet d'annuler sans
 * rouvrir l'application — le geste le plus probable quand la soiree se prolonge.
 */
object SleepTimer {

    private const val REQUEST_CODE = 4202
    private const val CANCEL_REQUEST_CODE = 4203
    private const val TICK_REQUEST_CODE = 4204
    private const val STOP_NOW_REQUEST_CODE = 4205
    private const val CHANNEL_ID = "sleep"
    private const val NOTIFICATION_ID = 4

    /** Durees proposees, en minutes. */
    val CHOICES = listOf(15, 30, 60, 120)

    /**
     * Echeance courante, ou 0 s'il n'y en a pas.
     *
     * Exposee sous forme observable : annuler depuis la notification doit se
     * voir immediatement sur l'ecran d'accueil, sans avoir a le rouvrir.
     */
    val deadline = MutableStateFlow(0L)

    /** A appeler une fois au demarrage pour retrouver une echeance survivante. */
    fun restore(context: Context) {
        deadline.value = Settings(context).sleepAt
    }

    fun schedule(context: Context, minutes: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())

        cancel(context)
        Settings(context).sleepAt = triggerAt
        deadline.value = triggerAt

        runCatching {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, endIntent(context))
        }.onFailure {
            // Sans autorisation d'alarme exacte, une echeance approchee suffit
            // largement pour un arret de courtoisie.
            runCatching { manager.set(AlarmManager.RTC_WAKEUP, triggerAt, endIntent(context)) }
        }

        refresh(context)
    }

    fun cancel(context: Context) {
        Settings(context).sleepAt = 0L
        deadline.value = 0L
        dismiss(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(endIntent(context)) }
        runCatching { manager.cancel(tickIntent(context)) }
    }

    /** Interrompt le decompte sans toucher a la notification en cours. */
    fun stopTicking(context: Context) {
        Settings(context).sleepAt = 0L
        deadline.value = 0L
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(tickIntent(context)) }
    }

    /** Minutes restantes, arrondies a la minute superieure, ou null s'il n'y a rien. */
    fun remainingMinutes(context: Context): Int? = remainingMinutes(Settings(context).sleepAt)

    fun remainingMinutes(at: Long): Int? {
        if (at <= 0L) return null
        val left = at - System.currentTimeMillis()
        if (left <= 0L) return null
        // Arrondi au superieur : « 1 min » doit rester affiche jusqu'a l'echeance,
        // et l'ecran comme la notification lisent la meme valeur au meme instant.
        return Math.ceil(left / 60_000.0).toInt().coerceAtLeast(1)
    }

    /**
     * Reecrit la notification et programme la mise a jour suivante.
     *
     * Le systeme sait afficher un chronometre, mais son rendu varie d'un
     * telephone a l'autre : un texte redige chaque minute est plus previsible.
     */
    fun refresh(context: Context) {
        val at = Settings(context).sleepAt
        val minutes = remainingMinutes(at)
        if (minutes == null) {
            cancel(context)
            return
        }
        deadline.value = at
        notify(context, minutes)
        scheduleTick(context, at)
    }

    /**
     * Signale que l'echeance est atteinte mais qu'on laisse finir le morceau.
     *
     * Sans ce message, l'attente passerait pour un blocage. Un bouton permet de
     * ne pas patienter.
     */
    fun showWaitingForTrack(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        ensureChannel(context, manager)

        val stopNow = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_driver),
            context.getString(R.string.sleep_stop_now),
            broadcast(context, STOP_NOW_REQUEST_CODE, AlarmReceiver.ACTION_STOP_NOW)
        ).build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.sleep_notification_title))
            .setContentText(context.getString(R.string.sleep_after_track))
            .setSmallIcon(R.drawable.ic_driver)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .addAction(stopNow)
            .addAction(cancelAction(context))
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun dismiss(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    /** Vrai si le systeme laisse l'application afficher des notifications. */
    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return false
        return runCatching { manager.areNotificationsEnabled() }.getOrDefault(false)
    }

    /**
     * Programme le rafraichissement suivant sur le changement de minute.
     *
     * Se caler sur l'echeance plutot que sur l'instant courant evite que le
     * texte de la notification derive de celui affiche dans l'application.
     */
    private fun scheduleTick(context: Context, deadlineAt: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val left = deadlineAt - System.currentTimeMillis()
        if (left <= 0) return
        val untilNextMinute = left % 60_000L
        val next = System.currentTimeMillis() +
            if (untilNextMinute > 1_000L) untilNextMinute else 60_000L
        // Echeance approchee : quelques secondes de retard sont sans consequence,
        // et le telephone n'a pas a se reveiller pour si peu.
        runCatching { manager.set(AlarmManager.RTC, next, tickIntent(context)) }
    }

    private fun notify(context: Context, minutes: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        ensureChannel(context, manager)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.sleep_notification_title))
            .setContentText(context.getString(R.string.sleep_in, minutes))
            .setSmallIcon(R.drawable.ic_driver)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .addAction(cancelAction(context))
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_sleep),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun cancelAction(context: Context): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_driver),
            context.getString(R.string.sleep_cancel),
            broadcast(context, CANCEL_REQUEST_CODE, AlarmReceiver.ACTION_CANCEL_SLEEP)
        ).build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun endIntent(context: Context) =
        broadcast(context, REQUEST_CODE, AlarmReceiver.ACTION_SLEEP)

    private fun tickIntent(context: Context) =
        broadcast(context, TICK_REQUEST_CODE, AlarmReceiver.ACTION_SLEEP_TICK)

    private fun broadcast(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
