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

    /** Page a ouvrir quand le telechargement automatique n'est pas possible. */
    fun releasesPage(): String = RELEASES_PAGE

    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 3

    private val INTERVAL_MS = TimeUnit.DAYS.toMillis(1)

    /** Version publiee, avec l'adresse de son APK quand il y en a un. */
    data class Release(val version: String, val apkUrl: String?)

    /** Interroge le depot au plus une fois par jour, en silence en cas d'echec. */
    suspend fun checkQuietly(context: Context) {
        val settings = Settings(context)
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < INTERVAL_MS) return
        settings.lastUpdateCheck = now

        val release = withContext(Dispatchers.IO) { fetchLatest() } ?: return
        if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
            notify(context, release.version)
        }
    }

    /** Issue d'une verification demandee explicitement. */
    sealed interface Outcome {
        /** Une version plus recente est publiee. */
        data class Available(val release: Release) : Outcome
        /** Le depot repond, rien de nouveau. */
        data object UpToDate : Outcome
        /**
         * Le depot repond mais ne publie aucune version.
         * Cas normal tant que rien n'a ete publie : ce n'est pas une panne.
         */
        data object NoRelease : Outcome
        /**
         * Le depot n'a pas repondu.
         * Distinguer ce cas evite d'annoncer « a jour » alors qu'on ne sait rien.
         */
        data object Unreachable : Outcome
    }

    /**
     * Verification demandee explicitement : ignore le delai d'un jour et rend
     * compte du resultat, y compris quand tout est deja a jour.
     */
    suspend fun checkNow(context: Context): Outcome {
        Settings(context).lastUpdateCheck = System.currentTimeMillis()
        val fetched = withContext(Dispatchers.IO) { fetchLatestOrStatus() }
        val release = fetched.getOrNull()
            ?: return if (fetched.exceptionOrNull() is NoReleaseException) {
                Outcome.NoRelease
            } else {
                Outcome.Unreachable
            }
        return if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
            Outcome.Available(release)
        } else {
            Outcome.UpToDate
        }
    }

    /** Le depot ne publie encore aucune version. */
    private class NoReleaseException : Exception()

    /**
     * Comme [fetchLatest], mais distingue l'absence de publication d'une panne :
     * l'API repond 404 tant qu'aucune version n'existe.
     */
    private fun fetchLatestOrStatus(): Result<Release> = runCatching {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw NoReleaseException()
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            parse(connection.inputStream.bufferedReader().use { it.readText() })
                ?: throw NoReleaseException()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Telecharge l'APK et ouvre l'installateur du systeme.
     *
     * Android n'autorise pas une application a en installer une autre sans
     * l'accord explicite de l'utilisateur : on prepare le fichier, il confirme.
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = java.io.File(context.getExternalFilesDir(null), "party-pair.apk")
                (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                }.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.updates", target
                )
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(install)
                true
            }.getOrDefault(false)
        }

    private fun fetchLatest(): Release? = fetchLatestOrStatus().getOrNull()

    /** Extrait la version et l'adresse de l'APK d'une reponse de l'API. */
    private fun parse(body: String): Release? {
        val json = JSONObject(body)
        val version = json.optString("tag_name").trimStart('v', 'V')
        if (version.isBlank()) return null

        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
        }
        return Release(version, apkUrl)
    }

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
