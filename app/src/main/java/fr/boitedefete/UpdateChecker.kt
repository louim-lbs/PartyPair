package fr.boitedefete

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Signale une nouvelle version publiee sur le depot.
 *
 * L'application etant installee a la main, personne ne la met a jour tout seul :
 * une notification discrete avec le lien vers la publication suffit.
 */
object UpdateChecker {

    private const val RELEASES_API =
        "https://api.github.com/repos/louim-lbs/PartyPair/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/louim-lbs/PartyPair/releases/latest"

    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 3

    private val INTERVAL_MS = TimeUnit.DAYS.toMillis(1)

    /** Interroge le depot au plus une fois par jour, en silence en cas d'echec. */
    suspend fun checkQuietly(context: Context) {
        val settings = Settings(context)
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < INTERVAL_MS) return
        settings.lastUpdateCheck = now

        val latest = withContext(Dispatchers.IO) { fetchLatestVersion() } ?: return
        if (isNewer(latest, BuildConfig.VERSION_NAME)) {
            notify(context, latest)
        }
    }

    private fun fetchLatestVersion(): String? = runCatching {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("tag_name").trimStart('v', 'V').takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** Compare deux numeros de version composante par composante : 1.10 suit bien 1.9. */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").mapNotNull { it.trim().toIntOrNull() }
        val b = current.split(".").mapNotNull { it.trim().toIntOrNull() }
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun notify(context: Context, version: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_updates),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.update_title, version))
            .setContentText(context.getString(R.string.update_body))
            .setSmallIcon(R.drawable.ic_driver)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
