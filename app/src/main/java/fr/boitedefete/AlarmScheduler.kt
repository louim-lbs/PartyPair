package fr.boitedefete

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.TimeUnit

/**
 * Declenche les enceintes juste avant l'alarme du telephone.
 *
 * Android expose la prochaine alarme programmee, quelle que soit l'application
 * qui l'a posee : la question des alarmes multiples se resout d'elle-meme,
 * puisque seule la suivante compte. Un changement d'alarme est signale, ce qui
 * permet de se reprogrammer.
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 4201

    fun reschedule(context: Context) {
        val settings = Settings(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        cancel(context, manager)
        if (!settings.alarmEnabled || !settings.isConfigured) return
        if (!canScheduleExact(manager)) return

        val nextAlarm = manager.nextAlarmClock ?: return
        val lead = TimeUnit.MINUTES.toMillis(settings.alarmLeadMinutes.toLong())
        val triggerAt = nextAlarm.triggerTime - lead
        if (triggerAt <= System.currentTimeMillis()) return

        runCatching {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context)
            )
        }
    }

    fun cancel(context: Context, manager: AlarmManager? = null) {
        val am = manager
            ?: context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        runCatching { am.cancel(pendingIntent(context)) }
    }

    /** Heure de la prochaine alarme du telephone, ou null s'il n'y en a pas. */
    fun nextAlarmTime(context: Context): Long? {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return manager?.nextAlarmClock?.triggerTime
    }

    fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return canScheduleExact(manager)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WAKE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Recoit le declenchement, ainsi que les evenements qui obligent a
 * reprogrammer : changement d'alarme du telephone et redemarrage.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WAKE -> {
                PartyService.start(context, PartyService.ACTION_WAKE)
                // Preparer la suivante des maintenant.
                AlarmScheduler.reschedule(context)
            }
            else -> AlarmScheduler.reschedule(context)
        }
    }

    companion object {
        const val ACTION_WAKE = "fr.boitedefete.action.ALARM_WAKE"
    }
}
