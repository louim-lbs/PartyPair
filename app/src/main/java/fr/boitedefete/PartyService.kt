package fr.boitedefete

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Execute la sequence en arriere-plan pour qu'elle survive a la fermeture de l'ecran.
 * C'est aussi ce service que declenche l'automatisation.
 */
class PartyService : Service() {

    private val scope = MainScope()
    private var job: Job? = null
    private val running = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.app_name)))

        val action = intent?.action ?: ACTION_START

        // Les reglages rapides sont courts et sans effet de bord : les ecarter
        // parce qu'une autre tache tourne revenait a perdre l'appui. Ils sont
        // donc lances en parallele, et se serialisent d'eux-memes sur la
        // liaison qu'ils partagent.
        if (action in QUICK_ACTIONS) {
            launchTask(quiet = true) { controller ->
                when (action) {
                    ACTION_WARM_UP -> controller.warmUp()
                    ACTION_APPLY_BASS -> controller.applyBassOnly()
                }
            }
            return START_NOT_STICKY
        }

        // Une sequence complete, en revanche, ne doit pas en croiser une autre.
        if (job?.isActive == true) return START_NOT_STICKY

        job = launchTask { controller ->
            val report: (Step) -> Unit = { step ->
                state.value = UiState(step, warning = controller.warning, subject = controller.subject)
                if (step == Step.READY && action in PROMPTING_ACTIONS) musicPrompt.value = true
                if (step == Step.IDLE) musicPrompt.value = false
                // Le libelle porte un parametre : sans lui, le motif reste brut.
                notify(getString(step.label, controller.subject.orEmpty()))
                PartyWidget.refresh(applicationContext)
            }
            when (action) {
                ACTION_TOGGLE -> controller.toggle(report)
                ACTION_SWAP_CHANNELS -> {
                    report(Step.LINKING)
                    controller.swapChannels()
                    report(Step.READY)
                }
                ACTION_WAKE -> {
                    // Declenche par l'alarme : aucune fenetre ne peut s'ouvrir,
                    // on lance donc la musique directement.
                    awaitBluetooth()
                    controller.run(report)
                    MusicLauncher.openAndPlay(applicationContext, Settings(applicationContext))
                }
                ACTION_POWER_OFF -> controller.powerOff(report)
                ACTION_SLEEP_DUE -> {
                    // Couper au milieu d'un morceau presque fini est desagreable :
                    // on le laisse s'achever quand il ne reste que quelques instants.
                    if (PlaybackWatcher.verdict(applicationContext) ==
                        PlaybackWatcher.Verdict.WAIT_FOR_TRACK
                    ) {
                        report(Step.WAITING_TRACK)
                        SleepTimer.showWaitingForTrack(applicationContext)
                        PlaybackWatcher.awaitTrackEnd(applicationContext) {
                            SleepTimer.showWaitingForTrack(applicationContext)
                        }
                    }
                    SleepTimer.dismiss(applicationContext)
                    controller.powerOff(report)
                }
                ACTION_UNLINK -> {
                    report(Step.LINKING)
                    controller.unlink()
                    report(Step.IDLE)
                }
                else -> controller.run(report)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Lance une tache et arrete le service quand plus rien ne tourne.
     *
     * Le compte des taches en cours evite qu'un reglage rapide n'arrete le
     * service sous les pieds d'une sequence encore en route.
     */
    private fun launchTask(
        /** Un reglage rapide qui echoue ne merite pas d'alarmer l'utilisateur. */
        quiet: Boolean = false,
        block: suspend (PartyController) -> Unit
    ): Job {
        running.incrementAndGet()
        return scope.launch(Dispatchers.IO) {
            val controller = PartyController(applicationContext)
            try {
                block(controller)
            } catch (e: Exception) {
                if (!quiet) {
                    val message = e.message ?: getString(R.string.error_unknown)
                    state.value = UiState(Step.FAILED, message)
                    PartyWidget.refresh(applicationContext)
                    // Declenchee par une alarme, un widget ou une routine, la
                    // sequence n'a aucun ecran ou se plaindre : on previent ici.
                    notifyFailure(message)
                }
            } finally {
                if (running.decrementAndGet() <= 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Patiente le temps que le Bluetooth revienne.
     *
     * Le mode sommeil coupe les radios et ne les retablit qu'a son extinction :
     * un reveil declenche trop tot trouverait le Bluetooth encore eteint.
     */
    private suspend fun awaitBluetooth() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val deadline = System.currentTimeMillis() + Config.BLUETOOTH_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (manager.adapter?.isEnabled == true) {
                // Laisser la pile finir de s'initialiser avant de solliciter les enceintes.
                kotlinx.coroutines.delay(Config.BLUETOOTH_SETTLE_MS)
                return
            }
            kotlinx.coroutines.delay(2_000)
        }
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
        const val ACTION_APPLY_BASS = "fr.boitedefete.action.APPLY_BASS"
        const val ACTION_WARM_UP = "fr.boitedefete.action.WARM_UP"
        const val ACTION_SLEEP_DUE = "fr.boitedefete.action.SLEEP_DUE"

        /** Reglages courts, qui ne doivent jamais etre ecartes. */
        private val QUICK_ACTIONS = setOf(ACTION_WARM_UP, ACTION_APPLY_BASS)

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

        fun start(context: Context, action: String = ACTION_TOGGLE): Boolean {
            // L'ecran doit repondre au doigt, pas au demarrage du service :
            // celui-ci met un instant a s'installer, puis la premiere connexion
            // BLE prend plusieurs secondes.
            when (action) {
                ACTION_POWER_OFF -> state.value = UiState(Step.FADING_OUT)
                // L'issue depend de la lecture en cours : rien a annoncer encore.
                ACTION_SLEEP_DUE -> Unit
                ACTION_WARM_UP -> Unit
                // La bascule ignore encore ce qu'elle va faire : rien a annoncer.
                ACTION_TOGGLE -> state.value = UiState(Step.CONNECTING)
                ACTION_START, ACTION_WAKE -> state.value = UiState(
                    Step.WAKING_PRIMARY,
                    // Nommer l'enceinte des le premier instant : c'est bien elle
                    // que la connexion va reveiller.
                    subject = Settings(context).primary?.name?.let { Elision.subject(it) }
                )
            }
            val intent = Intent(context, PartyService::class.java).setAction(action)
            // Le systeme peut refuser un service de premier plan lance depuis
            // l'arriere-plan : on le signale sans lever d'exception, pour que
            // l'appelant puisse se rabattre sur une autre voie.
            return runCatching { context.startForegroundService(intent) }.isSuccess
        }
    }
}

data class UiState(
    val step: Step,
    val error: String? = null,
    /** Precision affichee a cote de l'etat quand la sequence a abouti partiellement. */
    val warning: String? = null,
    /** Nom de l'enceinte concernee, insere dans le libelle de l'etape. */
    val subject: String? = null
)
