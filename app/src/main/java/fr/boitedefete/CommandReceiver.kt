package fr.boitedefete

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Commandes venues de l'exterieur, par diffusion.
 *
 * Home Assistant, Tasker ou adb peuvent s'en servir sans ouvrir d'ecran et,
 * contrairement au lancement d'activite, sans autorisation particuliere.
 *
 * Seules des actions inoffensives sont acceptees : allumer, eteindre, apparier.
 */
class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ALLOWED) return
        if (!Settings(context).isConfigured) return

        // Depuis Android 12, une application en arriere-plan n'a pas toujours le
        // droit de demarrer un service de premier plan. Si le systeme refuse, on
        // passe par l'activite de declenchement, qui elle en a le droit.
        if (!PartyService.start(context, action)) {
            runCatching {
                context.startActivity(
                    Intent(context, TriggerActivity::class.java)
                        .setAction(action)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private companion object {
        val ALLOWED = setOf(
            PartyService.ACTION_START,
            PartyService.ACTION_TOGGLE,
            PartyService.ACTION_POWER_OFF,
            PartyService.ACTION_UNLINK
        )
    }
}
