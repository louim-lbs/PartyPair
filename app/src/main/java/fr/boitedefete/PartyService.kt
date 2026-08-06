package fr.boitedefete

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Execute la sequence en arriere-plan pour qu'elle survive a la fermeture de l'ecran.
 * C'est aussi ce service que declenche l'automatisation.
 */
class PartyService : Service() {

    private val scope = MainScope()
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(getString(Step.WAKING_SECONDARY.label)))

        if (job?.isActive == true) return START_NOT_STICKY

        val action = intent?.action ?: ACTION_START

        job = scope.launch(Dispatchers.IO) {
            val controller = PartyController(applicationContext)
            val report: (Step) -> Unit = { step ->
                state.value = UiState(step)
                notify(getString(step.label))
            }
            try {
                when (action) {
                    ACTION_POWER_OFF -> controller.powerOff(report)
                    ACTION_UNLINK -> {
                        report(Step.LINKING)
                        controller.unlink()
                        report(Step.IDLE)
                    }
                    else -> controller.run(report)
                }
            } catch (e: Exception) {
                state.value = UiState(Step.FAILED, e.message ?: getString(R.string.error_unknown))
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_driver)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "fr.boitedefete.action.START"
        const val ACTION_UNLINK = "fr.boitedefete.action.UNLINK"
        const val ACTION_POWER_OFF = "fr.boitedefete.action.POWER_OFF"

        private const val CHANNEL_ID = "party"
        private const val NOTIFICATION_ID = 1

        /** Etat partage avec l'interface. */
        val state = MutableStateFlow(UiState(Step.IDLE))

        fun start(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, PartyService::class.java).setAction(action)
            context.startForegroundService(intent)
        }
    }
}

data class UiState(val step: Step, val error: String? = null)
