package fr.boitedefete

import android.service.notification.NotificationListenerService

/**
 * Point d'ancrage pour lire les sessions media en cours.
 *
 * Android n'accorde cette lecture qu'aux services d'ecoute de notifications.
 * Ce service ne traite aucune notification : il sert uniquement de reference
 * pour demander la liste des lecteurs actifs, et savoir si la touche
 * « lecture » atteindrait bien l'application choisie.
 *
 * Tant que l'autorisation n'est pas accordee, la verification repond
 * « inconnu » et l'application se contente de ne rien affirmer.
 */
class NotificationListener : NotificationListenerService()
