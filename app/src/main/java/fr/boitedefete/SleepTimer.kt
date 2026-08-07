package fr.boitedefete

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit

/**
 * Mise en veille differee.
 *
 * Le fondu sonore etant deja en place, il suffit de programmer l'echeance :
 * la musique s'eteindra d'elle-meme, sans coupure brutale.
 */
object SleepTimer {

    private const val REQUEST_CODE = 4202

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
    }

    fun cancel(context: Context) {
        Settings(context).sleepAt = 0L
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

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SLEEP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
