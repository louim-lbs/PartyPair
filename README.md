# Party Pair — Boîte de Fête

Réveille deux enceintes JBL PartyBox et les met en paire stéréo sans fil, d'un seul geste.

L'application suit la langue du téléphone : elle s'appelle **Boîte de Fête** en français, **Party Pair** partout ailleurs.

L'application JBL officielle demande une dizaine de manipulations pour rétablir la liaison stéréo, qui ne survit pas à l'extinction des enceintes. Celle-ci le fait en un appui, et se laisse déclencher par une routine, une alarme ou Home Assistant.

## Comment ça marche

Les PartyBox exposent un service BLE propriétaire — trames `AA <commande> <longueur> <payload>` — qui n'était documenté nulle part. Il a été reconstitué en croisant des captures HCI Bluetooth et la décompilation de l'application officielle. La commande d'appairage stéréo tient en sept octets.

Tout est écrit dans **[`docs/PROTOCOL.md`](docs/PROTOCOL.md)** : table des opcodes, champs TLV, séquences vérifiées. C'est réutilisable pour n'importe quel client, Android ou non.

> **Matériel vérifié : PartyBox 710 uniquement.** Le protocole devrait valoir pour toute la gamme, mais rien d'autre n'a été testé. Si vous l'essayez sur un autre modèle, [ouvrez une issue](https://github.com/louim-lbs/PartyPair/issues) pour dire ce qui marche — c'est ce qui fera avancer le sujet.

## Ce que fait l'application

1. Se connecte en BLE à l'enceinte secondaire — la connexion suffit à la sortir de veille
2. Attend qu'elle réponde vraiment : une enceinte endormie accepte la connexion bien avant d'être capable de traiter une commande
3. Fait de même avec l'enceinte principale, et lui indique l'adresse Bluetooth du téléphone
4. Envoie la commande de liaison stéréo aux deux, à 250 ms d'intervalle
5. Vérifie que l'enceinte principale a bien rejoint le téléphone en audio, et relance l'invitation sinon
6. Ferme les connexions

Un second appui sur le bouton abaisse le volume en fondu, puis remet les deux enceintes en veille.

Si la paire stéréo tient déjà, l'application le détecte et ne la refait pas.

La liaison stéréo survit à la fermeture des connexions BLE : l'application n'a rien à maintenir ensuite.

## Configuration

Rien à modifier dans le code. Au premier lancement, l'application :

1. demande l'accès Bluetooth ;
2. propose la liste de vos appareils déjà appairés — vous désignez l'enceinte qui reçoit le son, puis celle qui la rejoint ;
3. tente de détecter l'adresse Bluetooth du téléphone, et vous la demande sinon.

Ces réglages sont conservés dans les préférences de l'application : ils **survivent aux mises à jour** et sont repris par la sauvegarde Android en cas de changement de téléphone. Seule une désinstallation les efface.

Pour les changer ensuite, appui long sur le nom des enceintes en bas de l'écran. Un appui simple permet de choisir l'application musicale ouverte depuis le raccourci de l'écran principal.

L'adresse du téléphone est transmise à l'enceinte principale pour qu'elle vienne s'y connecter en A2DP. Android ne permet plus toujours de la lire par programme ; si la détection échoue, elle se trouve dans **Réglages → À propos du téléphone → État → Adresse Bluetooth**. La laisser vide est possible : l'application n'enverra simplement pas cette commande.

L'attribution des canaux gauche et droite est stockée dans les enceintes elles-mêmes. Faites l'appairage initial une fois avec l'application JBL officielle ; ensuite celle-ci se contente de rétablir la liaison.

## Signature

Le dépôt contient une clé de débogage (`debug.keystore`) volontairement versionnée. Sans clé fixe, chaque compilation produirait une signature différente et Android refuserait d'installer la mise à jour par-dessus la précédente, obligeant à désinstaller — et donc à perdre les réglages.

Cette clé n'a aucune valeur secrète. Pour une diffusion large, générez votre propre clé de publication et passez-la par les secrets GitHub.

## Obtenir l'APK

Le dépôt contient un workflow GitHub Actions : à chaque publication, GitHub compile l'application et met l'APK à disposition. Aucun outil de développement n'est nécessaire sur votre machine.

Pas à pas depuis Windows avec VS Code : **[docs/WINDOWS.md](docs/WINDOWS.md)**.

Pour compiler localement, ouvrez le projet dans Android Studio et lancez *Run*. En ligne de commande :

```bash
gradle wrapper          # une seule fois, pour générer le wrapper
./gradlew assembleDebug
```

Le projet vise l'API 35 et fonctionne à partir d'Android 8.

## Réveil

Dans les réglages, l'option *Réveiller les enceintes avec mon alarme* utilise la **prochaine alarme programmée sur le téléphone**, quelle que soit l'application qui l'a posée — la question des alarmes multiples se règle donc d'elle-même. Les enceintes s'allument une minute avant, la musique se lance, et l'application se reprogramme pour la fois suivante.

Android demandera l'autorisation de programmer des alarmes exactes. Pensez aussi à exempter l'application de l'optimisation de batterie, sans quoi le système peut retarder le déclenchement.

**Gardez votre alarme habituelle en secours.** Une enceinte débranchée ou hors de portée ne doit pas vous faire dormir trop longtemps.

## Minuterie

Les réglages proposent une mise en veille différée, de 15 minutes à 2 heures. Le volume descend en fondu avant l'extinction, ce qui la rend supportable si vous vous endormez.

## Renforcement des graves

Trois états : arrêt, profond, percutant — appliqués à chaque réveil des enceintes.

## Volume

Le volume appliqué à chaque réveil se règle dans les réglages, sur l'échelle 0 à 32 de l'enceinte. C'est ce qui évite qu'une soirée à plein volume ne devienne un réveil brutal.

## Automatisation

L'activité `TriggerActivity` lance la séquence sans afficher d'interface.

**adb**

```bash
adb shell am start -n fr.boitedefete/.TriggerActivity
```

**Tasker** — action *Système → Envoyer un intent* :
- Action : `fr.boitedefete.action.START`
- Package : `fr.boitedefete`
- Classe : `fr.boitedefete.TriggerActivity`
- Cible : Activité

**Routines Samsung** — les routines ne savent que « ouvrir une application ». L'application installe donc deux raccourcis supplémentaires dans le lanceur, qui apparaissent dans leur liste :

- **Démarrer** — réveille les enceintes et établit la paire stéréo
- **Veille** — remet les deux enceintes en veille

Dans *Paramètres → Modes et routines → Routines → +*, choisissez votre déclencheur sous **Si**, puis sous **Alors** l'action *Ouvrir une application* et sélectionnez le raccourci voulu. Aucune interface ne s'affiche : la séquence s'exécute puis rend la main.

**Réglages rapides** — une tuile est disponible dans le volet des notifications, à ajouter depuis le bouton d'édition. Elle reprend la bascule du bouton principal.

**Home Assistant** — l'application compagnon Android envoie une diffusion, que l'application reçoit sans rien afficher :

```yaml
action: notify.mobile_app_votre_telephone
data:
  message: command_broadcast_intent
  data:
    intent_package_name: fr.boitedefete
    intent_action: fr.boitedefete.action.POWER_OFF
```

Actions disponibles : `START` pour réveiller et apparier, `POWER_OFF` pour la mise en veille avec fondu, `TOGGLE` pour basculer selon l'état réel, `UNLINK` pour rompre la paire sans éteindre.

Préférez `command_broadcast_intent` à `command_activity` : ce dernier réclame l'autorisation « Superposition à d'autres applications » et fait brièvement basculer l'écran.

Le téléphone doit être à portée des enceintes, puisque c'est lui qui leur parle. Pour une routine déclenchée en partant de chez vous, prévoyez que la commande arrive pendant que le téléphone est encore là.

Pour piloter les enceintes **sans le téléphone**, il faut une machine avec Bluetooth — un Raspberry Pi, ou le NAS. Le script `bleak` du paragraphe précédent, exposé en `shell_command` ou via MQTT, suffit : les enceintes n'ont besoin que de quelques secondes de contact BLE, sans maintien de connexion.

Pour rompre la paire stéréo sans éteindre, utilisez l'action `fr.boitedefete.action.UNLINK`. Pour la mise en veille, `fr.boitedefete.action.POWER_OFF`.

Quelques idées de déclencheurs : à l'ouverture d'une application de musique, à l'arrivée sur le réseau Wi-Fi de la maison, ou sur une alarme du matin.

## Portage vers Linux

La séquence tient en quelques lignes avec [`bleak`](https://github.com/hbldh/bleak) — c'est la voie pour piloter les enceintes depuis un Raspberry Pi ou un NAS :

```python
import asyncio
from bleak import BleakClient

TX = "65786365-6c70-6f69-6e74-2e636f6d0002"
TWS_LINK = bytes.fromhex("aa130400390101")

async def link(mac):
    async with BleakClient(mac) as client:
        await client.write_gatt_char(TX, TWS_LINK, response=False)

async def main():
    await link("AA:BB:CC:DD:EE:FF")  # enceinte secondaire
    await asyncio.sleep(0.25)
    await link("11:22:33:44:55:66")  # enceinte principale

asyncio.run(main())
```

Pour que les enceintes se connectent au dongle Bluetooth plutôt qu'au téléphone, envoyez `AA 84 06` suivi des six octets de l'adresse du dongle avant la commande de liaison.

## Sauvegarde

Les réglages proposent de copier la configuration dans le presse-papiers, et de la restaurer par collage. Utile avant de changer de téléphone, ou après une désinstallation.

## Limites connues

Si l'enceinte secondaire ne répond pas, la séquence se poursuit avec la principale seule et le signale, plutôt que d'échouer entièrement. Un échec complet donne lieu à une notification nommant l'enceinte en cause, utile quand le déclenchement vient d'une alarme ou d'une routine.

L'application officielle JBL et celle-ci ne peuvent pas parler à une enceinte en même temps : fermez l'une avant d'utiliser l'autre.

Aucune interface publique ne permet de demander à une application musicale de jouer un morceau précis. L'application ouvre la playlist si un lien est renseigné, puis simule la touche « lecture » d'un casque — ce que la plupart des lecteurs honorent. Le résultat dépend de l'application musicale.

## Matériel testé

Deux JBL PartyBox 710, Samsung Galaxy S20 FE sous Android 13, application JBL PartyBox 3.14.1.

## Langues

L'interface est traduite en français et en anglais, et se règle sur la langue du téléphone. Le nom affiché sur l'écran d'accueil suit la même règle.

Le français couvre toutes les variantes — France, Suisse, Belgique, Canada. Pour le réserver à certaines d'entre elles, renommez `app/src/main/res/values-fr` en `values-fr-rFR`, et dupliquez-le en `values-fr-rCH` pour la Suisse.

Sous Android 13 et plus, la langue peut aussi être forcée par application dans **Réglages → Applications → Party Pair → Langue**.

Pour ajouter une traduction, copiez `app/src/main/res/values/strings.xml` dans un dossier `values-xx` (`values-de`, `values-es`…), traduisez les valeurs, et ajoutez la locale à `app/src/main/res/xml/locales_config.xml`. Les contributions sont bienvenues.

## Avertissement

Projet indépendant, sans lien avec Harman ou JBL. « JBL » et « PartyBox » sont des marques de Harman International Industries. Aucun élément graphique de la marque n'est repris ici : l'identité visuelle de l'application s'inspire de l'objet lui-même, pas de son logo.

Le protocole a été reconstitué à des fins d'interopérabilité, sur du matériel dont l'auteur est propriétaire.

## Licence

MIT — voir [LICENSE](LICENSE).
