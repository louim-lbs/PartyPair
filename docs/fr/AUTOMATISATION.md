# Automatisation

Party Pair se déclenche depuis l'extérieur : un widget, une routine, une alarme, ou Home Assistant.

---

## Widget et tuile

Le **widget** occupe une case de l'écran d'accueil et bascule d'un appui : allumage, ou mise en veille si les enceintes jouent. L'icône passe à l'orange quand la paire tient.

Une **tuile** est aussi disponible dans le volet des réglages rapides, à ajouter depuis le bouton d'édition du volet.

## Routines Samsung

Les routines ne savent que lancer une application. Party Pair installe donc deux raccourcis supplémentaires dans le lanceur, qui apparaissent dans leur liste :

- **Démarrer** — réveille les enceintes et rétablit la paire stéréo
- **Veille** — fondu sonore puis extinction

Dans *Paramètres → Modes et routines → Routines → +*, choisissez votre déclencheur sous **Si**, puis sous **Alors** l'action *Ouvrir une application* et sélectionnez le raccourci voulu. Rien ne s'affiche : la séquence s'exécute puis rend la main.

## Home Assistant

Avec l'application compagnon Android :

```yaml
action: notify.mobile_app_votre_telephone
data:
  message: command_activity
  data:
    intent_package_name: fr.boitedefete
    intent_class_name: fr.boitedefete.TriggerActivity
    intent_action: fr.boitedefete.action.POWER_OFF
```

Actions disponibles :

| Action | Effet |
|---|---|
| `fr.boitedefete.action.START` | réveille et apparie |
| `fr.boitedefete.action.POWER_OFF` | fondu sonore puis extinction |
| `fr.boitedefete.action.TOGGLE` | bascule selon l'état réel des enceintes |
| `fr.boitedefete.action.UNLINK` | rompt la paire sans éteindre |

Au premier envoi, l'application compagnon demandera l'autorisation « Superposition à d'autres applications ». Elle est nécessaire : depuis Android 12, une application en arrière-plan ne peut pas lancer de service, et c'est cette autorisation qui lève la restriction.

`command_broadcast_intent` fonctionne également, mais seulement lorsque Party Pair a été ouverte récemment. Préférez `command_activity`.

**Le téléphone doit être à portée des enceintes**, puisque c'est lui qui leur parle. Pour une routine déclenchée en quittant la maison, prévoyez que la commande arrive pendant que le téléphone est encore là — sinon, voyez le pilotage depuis un Raspberry Pi, plus bas.

## Tasker

Action *Système → Envoyer un intent* :

- Action : `fr.boitedefete.action.TOGGLE`
- Package : `fr.boitedefete`
- Classe : `fr.boitedefete.TriggerActivity`
- Cible : Activité

## adb

```bash
adb shell am start -n fr.boitedefete/.TriggerActivity \
  -a fr.boitedefete.action.TOGGLE
```

---

## Réveil

Dans les réglages, l'option *Réveiller les enceintes avec mon alarme* utilise la **prochaine alarme programmée sur le téléphone**, quelle que soit l'application qui l'a posée. Les enceintes s'allument un peu avant, la musique se lance, et l'application se reprogramme pour la fois suivante.

Android ne dit pas quelle alarme vient du mode sommeil : rien dans l'information fournie n'indique son origine. Une **plage horaire** réglable (4h–11h par défaut) écarte donc les minuteries de cuisine et les rappels de la journée.

Le mode sommeil coupe les radios. L'application attend le retour du Bluetooth jusqu'à trois minutes, puis laisse à la pile le temps de s'initialiser.

Il faudra accorder l'autorisation d'alarme exacte, et exempter l'application de l'optimisation de batterie.

> **Gardez votre alarme habituelle en secours.** Une enceinte débranchée ou hors de portée ne doit pas vous faire dormir trop longtemps.

### Lancer la bonne playlist

Renseignez le **nom de la playlist** dans les réglages. Il est transmis à l'application musicale comme une demande de lecture — la seule interface publique Android qui démarre vraiment quelque chose de précis.

Un **lien de playlist** peut aussi être collé : il ouvre la page, sans garantie de lancer le son. L'application essaie les deux et vérifie à chaque fois qu'un son sort réellement avant de passer à la méthode suivante.

Rien n'oblige une application musicale à honorer ces demandes. Le résultat dépend d'elle.

---

## Volume, graves, équilibre

Le volume appliqué à chaque réveil se règle sur l'échelle 0–32 de l'enceinte, ce qui évite qu'une soirée à plein volume ne devienne un réveil brutal.

L'**équilibre** baisse l'enceinte la plus proche plutôt que de monter l'autre, pour ne jamais dépasser le niveau demandé.

Le **renforcement des graves** propose trois états, appliqués à chaque réveil.

---

## Piloter les enceintes sans téléphone

Le protocole n'a rien de spécifique à Android. Depuis un Raspberry Pi ou un NAS avec Bluetooth, quelques lignes suffisent :

```python
import asyncio
from bleak import BleakClient

TX = "65786365-6c70-6f69-6e74-2e636f6d0002"
TWS_LINK = bytes.fromhex("aa130400390101")

async def link(mac):
    async with BleakClient(mac) as client:
        await client.write_gatt_char(TX, TWS_LINK, response=False)

async def main():
    await link("AA:BB:CC:DD:EE:FF")   # enceinte secondaire
    await asyncio.sleep(0.25)
    await link("11:22:33:44:55:66")   # enceinte principale

asyncio.run(main())
```

Deux propriétés rendent l'approche confortable : aucun maintien de connexion n'est nécessaire, et la liaison stéréo survit à la déconnexion Bluetooth. Quelques secondes de contact suffisent.

Pour que les enceintes se connectent au dongle plutôt qu'au téléphone, envoyez `AA 84 06` suivi des six octets de son adresse avant la commande de liaison.

Exposez le script en `shell_command` ou via MQTT, et Home Assistant pilote les enceintes sans dépendre d'un téléphone.

---

## Minuterie

À l'échéance, l'application regarde ce qui est en cours de lecture. S'il reste **moins de trois minutes** sur le morceau, elle le laisse finir avant le fondu — couper un titre presque terminé est inutilement brutal. Au-delà, ou si le temps restant n'est pas lisible, elle éteint tout de suite.

Pendant l'attente, la notification l'indique et propose un bouton **Éteindre**.

Lancer un autre morceau pendant cette attente relance l'attente sur le nouveau, quelle que soit sa longueur : choisir un titre à ce moment-là, c'est vouloir un dernier morceau. Une pause bénéficie de trente secondes avant de conclure — un appel téléphonique n'est pas une fin d'écoute.

La lecture de la progression demande l'autorisation d'accès aux notifications. Sans elle, la minuterie éteint simplement à l'échéance.

## Sauvegarde

Les réglages proposent de copier la configuration dans le presse-papiers et de la restaurer par collage. Utile avant de changer de téléphone.

## Limites connues

L'application officielle JBL et celle-ci ne peuvent pas parler à une enceinte en même temps : fermez l'une avant d'utiliser l'autre.

Si l'enceinte secondaire ne répond pas, la séquence se poursuit avec la principale seule et le signale. Un échec complet donne lieu à une notification nommant l'enceinte en cause — utile quand le déclenchement vient d'une alarme ou d'une routine.
