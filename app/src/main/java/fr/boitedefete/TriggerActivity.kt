package fr.boitedefete

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Point d'entree pour l'automatisation, sans interface.
 *
 * Trois facons de l'atteindre :
 *  - par intent nomme, pour Tasker, Home Assistant ou adb ;
 *  - par les raccourcis « Démarrer » et « Veille » visibles dans le lanceur,
 *    que les routines Samsung savent ouvrir ;
 *  - par l'alarme du reveil.
 *
 * Son role depasse le simple relais : depuis Android 10, une application en
 * arriere-plan n'a pas le droit d'en ouvrir une autre. Une activite, elle, en a
 * le droit — meme invisible. C'est ce qui permet au reveil d'ouvrir le lecteur.
 */
class TriggerActivity : Activity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings(this).isConfigured) {
            Toast.makeText(this, R.string.not_configured, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Tant que cette fenetre existe, l'ouverture d'une application est permise.
        MusicLauncher.setForeground(true)

        when (val action = resolveAction()) {
            ACTION_PLAY_MUSIC -> playThenFinish()
            // Allumer sans musique n'a guere d'interet : on enchaine.
            in PLAYING_ACTIONS -> runThenPlay(action)
            else -> {
                PartyService.start(this, action)
                finish()
            }
        }
    }

    /** Lancee depuis la notification du reveil : ouvrir le lecteur, rien d'autre. */
    private fun playThenFinish() {
        scope.launch {
            MusicLauncher.openAndPlay(applicationContext, Settings(this@TriggerActivity))
            finish()
        }
    }

    /**
     * Lance la sequence puis la musique.
     *
     * La sequence revient au service, qui survivra a l'ecran eteint. Mais lui
     * ne peut pas ouvrir le lecteur : depuis Android 10, seule une activite en
     * a le droit. Celle-ci reste donc en vie jusqu'a ce que les enceintes
     * soient pretes, ouvre la musique, et s'efface.
     */
    private fun runThenPlay(action: String) {
        PartyService.start(this, action)
        scope.launch {
            val ready = awaitReady()
            if (ready) {
                MusicLauncher.openAndPlay(applicationContext, Settings(this@TriggerActivity))
            }
            finish()
        }
    }

    /** Attend que la sequence aboutisse. Faux si elle echoue ou traine trop. */
    private suspend fun awaitReady(): Boolean {
        val deadline = System.currentTimeMillis() + SEQUENCE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            when (PartyService.state.value.step) {
                Step.READY -> return true
                Step.FAILED -> return false
                else -> delay(400)
            }
        }
        return false
    }

    override fun onDestroy() {
        MusicLauncher.setForeground(false)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Les routines Samsung ne savent qu'« ouvrir une application ». On distingue
     * donc l'intention par le raccourci utilise, a defaut d'action explicite.
     */
    private fun resolveAction(): String {
        intent?.action?.let {
            if (it in KNOWN_ACTIONS) return it
        }
        val launchedAs = intent?.component?.className.orEmpty()
        return if (launchedAs.endsWith(STANDBY_ALIAS)) {
            PartyService.ACTION_POWER_OFF
        } else {
            PartyService.ACTION_START
        }
    }

    companion object {
        /** Ouvre le lecteur depuis la notification du reveil. */
        const val ACTION_PLAY_MUSIC = "fr.boitedefete.action.PLAY_MUSIC"

        private const val STANDBY_ALIAS = "StandbyShortcut"

        /** Actions apres lesquelles la musique doit demarrer. */
        private val PLAYING_ACTIONS = setOf(
            PartyService.ACTION_START,
            PartyService.ACTION_TOGGLE,
            PartyService.ACTION_WAKE
        )

        /** Au-dela, on renonce plutot que de laisser une fenetre invisible ouverte. */
        private const val SEQUENCE_TIMEOUT_MS = 4 * 60_000L

        private val KNOWN_ACTIONS = setOf(
            PartyService.ACTION_START,
            PartyService.ACTION_UNLINK,
            PartyService.ACTION_POWER_OFF,
            PartyService.ACTION_TOGGLE,
            PartyService.ACTION_WAKE,
            ACTION_PLAY_MUSIC
        )
    }
}
