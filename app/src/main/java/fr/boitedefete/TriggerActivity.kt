package fr.boitedefete

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Point d'entree pour l'automatisation. Ne montre aucune interface :
 * lance la sequence puis se ferme immediatement.
 *
 *   adb shell am start -n fr.boitedefete/.TriggerActivity
 */
class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings(this).isConfigured) {
            Toast.makeText(this, R.string.not_configured, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val action = intent?.action?.takeIf { it == PartyService.ACTION_UNLINK }
            ?: PartyService.ACTION_START
        PartyService.start(this, action)
        finish()
    }
}
