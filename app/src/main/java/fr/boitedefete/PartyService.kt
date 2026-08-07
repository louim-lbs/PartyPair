package fr.boitedefete

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
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
                state.value = UiState(step, warning = controller.warning)
                if (step == Step.READY && action in PROMPTING_ACTIONS) musicPrompt.value = true
                if (step == Step.IDLE) musicPrompt.value = false
                notify(getString(step.label))
                PartyWidget.refresh(applicationContext)
            }
            try {
                when (action) {
                    ACTION_TOGGLE -> controller.toggle(report)
                    ACTION_APPLY_SOUND -> {
                        // Reglage a la volee : on ne touche pas a l'etat affiche.
                        controller.applySound()
                    }
                    ACTION_SWAP_CHANNELS -> {
                        report(Step.LINKING)
                        controller.swapChannels()
                        report(Step.READY)
                    }
                    ACTION_WAKE -> {
                        // Declenche par l'alarme : aucune fenetre ne peut s'ouvrir,
                        // on lance donc la musique directement.
                        controller.run(report)
                        MusicLauncher.open(applicationContext, Settings(applicationContext))
                        kotlinx.coroutines.delay(3_000)
                        MusicLauncher.play(applicationContext)
                    }
                    ACTION_POWER_OFF -> controller.powerOff(report)
                    ACTION_UNLINK -> {
                        report(Step.LINKING)
                        controller.unlink()
                        report(Step.IDLE)
                    }
                    else -> controller.run(report)
                }
            } catch (e: Exception) {
                val message = e.message ?: getString(R.string.error_unknown)
                state.value = UiState(Step.FAILED, message)
                PartyWidget.refresh(applicationContext)
                // Declenchee par une alarme, un widget ou une routine, la
                // sequence n'a aucun ecran ou se plaindre : on previent ici.
                notifyFailure(message)
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

    /** Notification persistante decrivant l'echec, avec l'enceinte en cause. */
    private fun notifyFailure(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.failure_title))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_driver)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(FAILURE_NOTIFICATION_ID, notification) }
    }

    private fun notify(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun ensureChannel(manager: NotificationManager) {
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

    private fun buildNotification(text: String): Notification {
        ensureChannel(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
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
        const val ACTION_WAKE = "fr.boitedefete.action.WAKE"
        const val ACTION_TOGGLE = "fr.boitedefete.action.TOGGLE"
        const val ACTION_SWAP_CHANNELS = "fr.boitedefete.action.SWAP_CHANNELS"
        const val ACTION_APPLY_SOUND = "fr.boitedefete.action.APPLY_SOUND"

        /** Actions issues d'un geste de l'utilisateur, qui meritent la proposition. */
        private val PROMPTING_ACTIONS = setOf(ACTION_START, ACTION_TOGGLE)

        private const val CHANNEL_ID = "party"
        private const val NOTIFICATION_ID = 1
        private const val FAILURE_NOTIFICATION_ID = 2

        /** Etat partage avec l'interface. */
        val state = MutableStateFlow(UiState(Step.IDLE))

        /**
         * Vrai quand une sequence vient d'aboutir et que la musique n'a pas
         * encore ete proposee. Vit ici plutot que dans l'ecran, sans quoi un
         * aller-retour dans les reglages relancerait le decompte.
         */
        val musicPrompt = MutableStateFlow(false)

        /**
         * Remet l'affichage en phase avec la realite.
         *
         * L'etat vit en memoire : si Android arrete le processus, l'application
         * rouvre en croyant les enceintes eteintes alors qu'elles jouent encore.
         */
        suspend fun refreshState(context: Context) {
            if (state.value.step != Step.IDLE) return
            val on = runCatching { PartyController(context).isAlreadyOn() }.getOrDefault(false)
            if (on && state.value.step == Step.IDLE) {
                // Etat retrouve, non provoque : pas de proposition musicale.
                state.value = UiState(Step.READY)
                PartyWidget.refresh(context)
            }
        }

        fun start(context: Context, action: String = ACTION_TOGGLE) {
            val intent = Intent(context, PartyService::class.java).setAction(action)
            runCatching { context.startForegroundService(intent) }
        }
    }
}

data class UiState(
    val step: Step,
    val error: String? = null,
    /** Precision affichee a cote de l'etat quand la sequence a abouti partiellement. */
    val warning: String? = null
)
