package fr.boitedefete

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.launch
import android.widget.Toast

/**
 * Point d'entree pour l'automatisation, sans interface.
 *
 * Trois facons de l'atteindre :
 *  - par intent nomme, pour Tasker ou adb ;
 *  - par les raccourcis « Démarrer » et « Veille » visibles dans le lanceur,
 *    que les routines Samsung savent ouvrir ;
 *  - depuis l'application elle-meme.
 */
class TriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings(this).isConfigured) {
            Toast.makeText(this, R.string.not_configured, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (intent?.action == ACTION_PLAY_MUSIC) {
            // Lancee depuis la notification du reveil : cette fenetre, meme
            // invisible, autorise l'ouverture du lecteur.
            MusicLauncher.setForeground(true)
            kotlinx.coroutines.MainScope().launch {
                MusicLauncher.openAndPlay(applicationContext, Settings(this@TriggerActivity))
            }
            finish()
            return
        }

        PartyService.start(this, resolveAction())
        finish()
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
            PartyService.ACTION_TOGGLE
        )
    }
}
