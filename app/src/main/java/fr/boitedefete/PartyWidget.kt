package fr.boitedefete

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Bouton d'ecran d'accueil : un appui allume et apparie, le suivant met en veille.
 *
 * La bascule est decidee par le controleur, qui verifie l'etat reel des
 * enceintes : le widget n'a donc rien a memoriser.
 */
class PartyWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {

        /** Redessine tous les widgets poses sur l'ecran d'accueil. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, PartyWidget::class.java))
            }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> runCatching { manager.updateAppWidget(id, views) } }
        }

        private fun buildViews(context: Context): RemoteViews {
            val step = PartyService.state.value.step
            val views = RemoteViews(context.packageName, R.layout.widget_party)

            // Trois etats plutot que deux : un coeur allume des l'appui dit que
            // la demande est prise en compte, sans attendre que tout soit pret.
            val busy = step !in setOf(Step.IDLE, Step.READY, Step.FAILED)
            val icon = when {
                busy -> R.drawable.ic_widget_busy
                step == Step.READY -> R.drawable.ic_widget_on
                else -> R.drawable.ic_widget_off
            }
            views.setImageViewResource(R.id.widget_icon, icon)
            views.setContentDescription(
                R.id.widget_icon,
                context.getString(
                    when {
                        busy -> R.string.notification_working
                        step == Step.READY -> R.string.widget_on
                        else -> R.string.widget_off
                    }
                )
            )
            views.setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            return views
        }

        private fun togglePendingIntent(context: Context): PendingIntent {
            // Quand l'etat est connu, viser directement l'action evite au
            // controleur d'interroger l'enceinte avant de decider : la mise en
            // veille demarre alors sans ce detour.
            val action = if (PartyService.state.value.step == Step.READY) {
                PartyService.ACTION_POWER_OFF
            } else {
                PartyService.ACTION_TOGGLE
            }
            val intent = Intent(context, PartyService::class.java).setAction(action)
            return PendingIntent.getForegroundService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
