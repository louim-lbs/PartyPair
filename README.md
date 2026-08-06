# Party Pair — Boîte de Fête

Réveille deux enceintes JBL PartyBox et les met en paire stéréo sans fil, d'un seul geste.

L'application suit la langue du téléphone : elle s'appelle **Boîte de Fête** en français, **Party Pair** partout ailleurs.

L'application JBL officielle demande une dizaine de manipulations pour rétablir la liaison stéréo, qui ne survit pas à l'extinction des enceintes. Celle-ci le fait en un appui, et se laisse déclencher par Tasker ou Home Assistant.

Le protocole BLE utilisé n'était pas documenté publiquement. Il a été reconstitué par analyse de captures HCI et décompilation de l'application officielle : voir [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

## Ce que fait l'application

1. Se connecte en BLE à l'enceinte secondaire — la connexion suffit à la sortir de veille
2. Se connecte à l'enceinte principale, et lui indique l'adresse Bluetooth du téléphone pour l'audio
3. Envoie la commande de liaison stéréo aux deux, à 250 ms d'intervalle
4. Ferme les connexions

La liaison stéréo survit à la fermeture des connexions BLE : l'application n'a rien à maintenir ensuite.

## Configuration

Rien à modifier dans le code. Au premier lancement, l'application :

1. demande l'accès Bluetooth ;
2. propose la liste de vos appareils déjà appairés — vous désignez l'enceinte qui reçoit le son, puis celle qui la rejoint ;
3. tente de détecter l'adresse Bluetooth du téléphone, et vous la demande sinon.

Ces réglages sont conservés dans les préférences de l'application : ils **survivent aux mises à jour** et sont repris par la sauvegarde Android en cas de changement de téléphone. Seule une désinstallation les efface.

Pour les changer ensuite, appui long sur le nom des enceintes en bas de l'écran.

L'adresse du téléphone est transmise à l'enceinte principale pour qu'elle vienne s'y connecter en A2DP. Android ne permet plus toujours de la lire par programme ; si la détection échoue, elle se trouve dans **Réglages → À propos du téléphone → État → Adresse Bluetooth**. La laisser vide est possible : l'application n'enverra simplement pas cette commande.

L'attribution des canaux gauche et droite est stockée dans les enceintes elles-mêmes. Faites l'appairage initial une fois avec l'application JBL officielle ; ensuite celle-ci se contente de rétablir la liaison.

## Obtenir l'APK

Le dépôt contient un workflow GitHub Actions : à chaque publication, GitHub compile l'application et met l'APK à disposition. Aucun outil de développement n'est nécessaire sur votre machine.

Pas à pas depuis Windows avec VS Code : **[docs/WINDOWS.md](docs/WINDOWS.md)**.

Pour compiler localement, ouvrez le projet dans Android Studio et lancez *Run*. En ligne de commande :

```bash
gradle wrapper          # une seule fois, pour générer le wrapper
./gradlew assembleDebug
```

Le projet vise l'API 35 et fonctionne à partir d'Android 8.

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

**Home Assistant**, via l'intégration Android compagnon et une commande à distance, ou depuis un Tasker déclenché par MQTT.

Pour rompre la paire stéréo, utilisez l'action `fr.boitedefete.action.UNLINK`.

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

## Matériel testé

Deux JBL PartyBox 710, Samsung Galaxy S20 FE sous Android 13, application JBL PartyBox 3.14.1.

Le protocole est commun à la gamme PartyBox et devrait fonctionner sur d'autres modèles. Les retours sont bienvenus.

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
