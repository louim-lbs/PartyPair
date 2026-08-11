# Party Pair

Réveille deux enceintes JBL PartyBox et les met en paire stéréo, d'un seul appui.

![Party Pair](docs/screenshot.png)

*[English version](README.md)*

L'application officielle demande une dizaine de manipulations pour rétablir la liaison stéréo, qui ne survit pas à l'extinction des enceintes. Celle-ci le fait en un geste, et sait aussi se déclencher toute seule.

L'interface suit la langue du téléphone : **Boîte de Fête** en français, **Party Pair** ailleurs.

## Ce qu'elle sait faire

- **Un bouton** : réveille les deux enceintes, rétablit la paire stéréo, connecte l'audio. Un second appui les rendort, avec un fondu sonore.
- **Réveil** : les enceintes s'allument avant votre alarme et lancent votre playlist.
- **Minuterie** : extinction différée, avec décompte dans les notifications.
- **Graves, volume, équilibre** entre les deux enceintes, canaux gauche et droite.
- **Widget, tuile de réglages rapides, raccourcis** pour les routines Samsung et Home Assistant.

## Installation

Téléchargez l'APK depuis la [page des versions](https://github.com/louim-lbs/PartyPair/releases/latest), ouvrez-le sur le téléphone, et autorisez l'installation depuis cette source.

> **Bientôt sur F-Droid.** Une fois disponible, tenez-vous à une seule source : les APK GitHub et F-Droid portent des signatures différentes, et Android refuse de remplacer l'un par l'autre. Changer de canal impose une désinstallation — pensez à exporter votre configuration depuis les réglages avant.

Vous préférez compiler vous-même ? Voir [docs/fr/COMPILATION.md](docs/fr/COMPILATION.md).

## Premier lancement

1. **Appairez vos enceintes en stéréo une fois avec l'application JBL officielle.** C'est la seule étape que Party Pair ne peut pas faire à votre place ; ensuite elle rétablit la liaison toute seule.
2. Ouvrez Party Pair et accordez l'accès Bluetooth.
3. Choisissez l'enceinte qui reçoit le son, puis celle qui la rejoint. La seconde n'a pas besoin d'être appairée au téléphone — mieux vaut qu'elle ne le soit pas.
4. Confirmez l'adresse Bluetooth du téléphone, détectée automatiquement si possible.

C'est tout. Les fois suivantes, un appui suffit.

## Matériel

Vérifié sur deux **JBL PartyBox 710**, sous Android 13.

Le protocole devrait valoir pour toute la gamme PartyBox, mais rien d'autre n'a été testé. Si vous l'essayez sur un autre modèle, [dites-nous ce que ça donne](https://github.com/louim-lbs/PartyPair/issues) — c'est ce qui fera avancer le sujet.

Android 8 minimum. Le réglage de langue et le choix par application demandent Android 13.

## Pour aller plus loin

| | |
|---|---|
| [Automatisation](docs/fr/AUTOMATISATION.md) | Routines Samsung, Home Assistant, Tasker, réveil, pilotage depuis un Raspberry Pi |
| [Compiler l'application](docs/fr/COMPILATION.md) | Depuis Windows avec VS Code, ou en local |
| [Le protocole](docs/fr/PROTOCOLE.md) | La rétro-ingénierie complète du protocole BLE des PartyBox |
| [Publier sur F-Droid](docs/fr/FDROID.md) | Ce qu'il resterait à faire |
| [Sécurité](docs/SECURITY.md) | Ce que l'application expose, demande et conserve |

## Comment ça marche

Les PartyBox exposent un service BLE propriétaire — des trames `AA <commande> <longueur> <données>` — qui n'était documenté nulle part. Il a été reconstitué en croisant des captures Bluetooth HCI et la décompilation de l'application officielle. La commande d'appairage stéréo tient en sept octets :

```
AA 13 04 00 39 01 01
```

Tout est écrit dans [docs/fr/PROTOCOLE.md](docs/fr/PROTOCOLE.md) : table des opcodes, champs, séquences vérifiées. C'est réutilisable par n'importe quel client, Android ou non.

## Licence et marques

MIT — voir [LICENSE](LICENSE).

Projet indépendant, sans lien avec Harman ou JBL. « JBL » et « PartyBox » sont des marques de Harman International Industries ; aucun élément graphique de la marque n'est repris ici. Le protocole a été reconstitué à des fins d'interopérabilité, sur du matériel dont l'auteur est propriétaire.
