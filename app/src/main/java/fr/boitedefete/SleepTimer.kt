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

    /** Minutes restantes, arrondies a la minute superieure, ou null s'il n'y a rien. */
    fun remainingMinutes(context: Context): Int? = remainingMinutes(Settings(context).sleepAt)

    fun remainingMinutes(at: Long): Int? {
        if (at <= 0L) return null
        val left = at - System.currentTimeMillis()
        if (left <= 0L) return null
        return (TimeUnit.MILLISECONDS.toMinutes(left) + 1).toInt()
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
        scheduleTick(context)
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

    private fun scheduleTick(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nextMinute = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)
        // Echeance approchee : le decompte peut retarder de quelques secondes
        // sans consequence, et le telephone n'a pas a se reveiller pour si peu.
        runCatching { manager.set(AlarmManager.RTC, nextMinute, tickIntent(context)) }
    }

    private fun notify(context: Context, minutes: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_sleep),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val cancelAction = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_driver),
            context.getString(R.string.sleep_cancel),
            broadcast(context, CANCEL_REQUEST_CODE, AlarmReceiver.ACTION_CANCEL_SLEEP)
        ).build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.sleep_notification_title))
            .setContentText(context.getString(R.string.sleep_in, minutes))
            .setSmallIcon(R.drawable.ic_driver)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(cancelAction)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

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
