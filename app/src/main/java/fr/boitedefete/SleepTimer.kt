package fr.boitedefete

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
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
    private const val CHANNEL_ID = "sleep"
    private const val NOTIFICATION_ID = 4

    /** Durees proposees dans les reglages, en minutes. */
    val CHOICES = listOf(15, 30, 60, 120)

    fun schedule(context: Context, minutes: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val settings = Settings(context)
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())

        cancel(context)
        settings.sleepAt = triggerAt

        runCatching {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context)
            )
        }.onFailure {
            // Sans autorisation d'alarme exacte, une echeance approchee suffit
            // largement pour un arret de courtoisie.
            runCatching {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
            }
        }

        notify(context, triggerAt)
    }

    fun cancel(context: Context) {
        Settings(context).sleepAt = 0L
        dismiss(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pendingIntent(context)) }
    }

    /** Minutes restantes, ou null si aucune echeance n'est posee. */
    fun remainingMinutes(context: Context): Int? {
        val at = Settings(context).sleepAt
        if (at <= 0L) return null
        val left = at - System.currentTimeMillis()
        if (left <= 0L) return null
        return (TimeUnit.MILLISECONDS.toMinutes(left) + 1).toInt()
    }

    /** Retire la notification, sans toucher a l'echeance elle-meme. */
    fun dismiss(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    /**
     * Notification a decompte.
     *
     * Le chronometre du systeme se met a jour tout seul : inutile de reveiller
     * l'application chaque minute pour reecrire un texte.
     */
    private fun notify(context: Context, triggerAt: Long) {
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
            PendingIntent.getBroadcast(
                context,
                CANCEL_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CANCEL_SLEEP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).build()

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.sleep_notification_title))
            .setContentText(context.getString(R.string.sleep_notification_text))
            .setSmallIcon(R.drawable.ic_driver)
            .setWhen(triggerAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(cancelAction)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SLEEP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
