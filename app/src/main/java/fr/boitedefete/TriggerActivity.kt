package fr.boitedefete

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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
            PartyService.ACTION_WAKE -> wakeThenPlay()
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
     * Reveil : la sequence est confiee au service, mais la musique est lancee
     * d'ici. L'activite reste en vie jusque-la, sans quoi le systeme
     * refuserait a nouveau l'ouverture du lecteur.
     */
    private fun wakeThenPlay() {
        scope.launch {
            val controller = PartyController(applicationContext)
            // Le mode sommeil coupe les radios : elles peuvent mettre un moment
            // a revenir apres l'extinction de l'alarme.
            controller.awaitBluetooth()
            // Parametre nomme : run() accepte aussi une connexion deja ouverte,
            // et une lambda finale serait prise pour celle-ci.
            runCatching {
                controller.run(onStep = { step ->
                    PartyService.state.value = UiState(step, subject = controller.subject)
                })
            }
            MusicLauncher.openAndPlay(applicationContext, Settings(this@TriggerActivity))
            finish()
        }
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
