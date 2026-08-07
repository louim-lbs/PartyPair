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
            val ready = PartyService.state.value.step == Step.READY
            val views = RemoteViews(context.packageName, R.layout.widget_party)

            // Icone seule : le widget tient dans une case et se pose sur
            // n'importe quel fond d'ecran.
            views.setImageViewResource(
                R.id.widget_icon,
                if (ready) R.drawable.ic_widget_on else R.drawable.ic_widget_off
            )
            views.setContentDescription(
                R.id.widget_icon,
                context.getString(if (ready) R.string.widget_on else R.string.widget_off)
            )
            views.setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            return views
        }

        private fun togglePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PartyService::class.java)
                .setAction(PartyService.ACTION_TOGGLE)
            return PendingIntent.getForegroundService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
