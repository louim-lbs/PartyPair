# Protocole BLE JBL PartyBox

Source : décompilation de `base.apk` (JBL PartyBox **3.14.1**, build 2026-05-11), non obfusquée.
Classes clés : `com.harman.sdk.utils.PacketFormat`, `com.harman.sdk.command.BaseCommand`, `com.harman.sdk.command.StereoFlowCommand`, `com.harman.sdk.impl.DeviceStatusControlImpl`.

---

## 1. Transport GATT

| Élément | Valeur |
|---|---|
| Service | `65786365-6C70-6F69-6E74-2E636F6D0000` |
| TX (écriture, app → enceinte) | `65786365-6C70-6F69-6E74-2E636F6D0002` |
| RX (notifications, enceinte → app) | `65786365-6C70-6F69-6E74-2E636F6D0001` |
| MTU demandé | 512 |
| Mode d'écriture | Write Without Response |

Source : `AppConfig.rxUUID` / `txUUID` / `bleRxTxUUID`.

## 2. Format de trame

```
AA <commande> <longueur_payload> <payload...>
│
└─ identifier, fixé à (byte) -86 = 0xAA dans BaseCommand
```

Longueur sur **1 octet**. Pas de checksum au niveau trame (un tag CRC16 `0x44` existe au niveau TLV).

**Convention des opcodes** — quartet bas :
`1` = requête · `2` = réponse · `3` = set · `4` = notification/push

## 3. Table des opcodes (extraite de PacketFormat)

| Hex | Constante |
|---|---|
| `03` | REQ_DISCONNECT_DEVICE (avec payload `05` = **power on**) |
| `11` / `12` / `13` | REQ / RESP / SET_DEV_INFO |
| `16` | ROLE_INFO |
| `31` / `32` / `33` | REQ / RESP / SET_LIGHT_INFO |
| `34` / `35` | RESP_ACTIVE_LIGHT_PATTERN / SET_CUSTOM_LIGHT_PATTERN |
| `41` / `42` / `43` | REQ / RESP / SET_PLAYER_INFO |
| `51` / `52` / `53` / `54` | REQ / RET / SET / NOTIFY_DJEFFECT_STATUS |
| `61` / `62` / `63` | REQ / RET / SET_BASS_BOOST |
| `6A` / `6B` | SET / RET_LINK_MODE |
| `71` / `72` / `73` / `74` | REQ / RET / SET / NOTIFY_KARAOKE_STATUS |
| `81` / `82` | REQ / RET_PHONE_MAC_ADDRESS |
| `83` / `84` | REQ_PHONE_MAC_ADDRESS_FROM_DEVICE / RET_PHONE_MAC_ADDRESS_TO_DEVICE |
| **`85`** | **SET_SECONDARY_SPEAKER_ADDRESS** (reçu uniquement — l'app ne l'envoie jamais) |
| `91` / `92` / `93` | REQ / RET / SET_SIMPLE_EQ |
| `9D` / `9E` | REQ / RET_BATTERY_STATUS |
| `A1` / `A2` | REQ_DEV_FEATURE_INFO / RES_DEVICE_FEATURE_INFO |
| `F4` | IDENTIFY_DEVICE |

## 4. Tags TLV (dans les payloads)

| Tag | Constante |
|---|---|
| `35` | ACTIVE_CHANNEL_TOKEN_ID |
| `36` | AUDIO_SOURCE_TOKEN_ID |
| `38` | ROLE_TOKEN_ID |
| `39` | PARTY_CONNECT_MODE_TOKEN_ID |
| `3A` | TWS_STEREO_GROUP_NAME (16 octets max, UTF-8) |
| `3B` | TWS_STEREO_GROUP_ID (8 octets max) |
| `41` / `42` / `43` | PLAYER_PLAY / VOL / MUTE |
| `44` | CRC16_TOKEN_ID |

**AudioChannel** : `00` = aucun · `01` = GAUCHE · `02` = DROITE
**PartyConnectStatus** : `00` = OFF · `01` = WIRELESS_CONNECTING · `02` = WIRELESS_CONNECTED · `03` = WIRED

---

## 5. Réveil (version propre)

```
AA 03 01 05
```

C'est `ReqPowerOnCommand` : `setCommand((byte) 3)`, `setPayload({5})`. Remplace avantageusement la séquence empirique `AA-11-00` / `AA-61-00` / `AA-84-06-<MAC>`.

Réponse attendue : commande `00` (DEV_ACK) avec payload `03 00` (`00` = succès).

---

## 6. Appairage stéréo TWS — la commande recherchée

Construite par `StereoFlowCommand.init()`, envoyée via `setStereoFlow()` :

```
commande = 0x13 (SET_DEV_INFO)
payload  = 00
         + 3B <len> <groupID>          ← identique sur les deux enceintes
         + 39 01 <PartyConnectStatus>
         + 35 01 <AudioChannel>
         + 3A <len> <nom du groupe>    ← optionnel
```

**Le groupID** est généré par `geneGroupID()` : MD5 de `(millis + aléa)`, tronqué à 8 caractères hex (= 4 octets), avec la règle que les 2 premiers caractères ne doivent pas être `00`. Autrement dit : **n'importe quelle valeur convient**, du moment qu'elle est identique sur les deux enceintes et ne commence pas par `00`.

### Commandes à envoyer

Avec le groupe `A1B2C3D4` :

**enceinte principale → canal GAUCHE**
```
AA-13-0D-00-3B-04-A1-B2-C3-D4-39-01-01-35-01-01
```

**enceinte secondaire → canal DROITE**
```
AA-13-0D-00-3B-04-A1-B2-C3-D4-39-01-01-35-01-02
```

Décomposition : `00` · `3B 04 A1B2C3D4` (groupe) · `39 01 01` (WIRELESS_CONNECTING) · `35 01 01|02` (canal). Payload = 13 octets = `0x0D`.

Variante avec nom de groupe « Stereo » (canal gauche) :
```
AA-13-15-00-3B-04-A1-B2-C3-D4-39-01-01-35-01-01-3A-06-53-74-65-72-65-6F
```

**Point crucial** : `StereoGroupingState.startGrouping(mainDevice, mainChannel, coDevice, coChannel, groupID, groupName)` envoie la commande **aux deux enceintes**. Il faut donc se connecter successivement à chacune — ce qui confirme la connexion BLE vers enceinte secondaire qu'on voyait dans le journal HCI.

Réponse attendue : `getResponseCommands().add((byte) 0)` → notification commençant par `AA 00`.

---

## 7. Procédure de test

1. Réveiller les deux enceintes (`AA-03-01-05`, ou connexion simple pour enceinte secondaire).
2. Connecter nRF Connect à **enceinte principale** → écrire la trame canal gauche sur `...0002`.
3. Se déconnecter, connecter à **enceinte secondaire** → écrire la trame canal droite.
4. Vérifier : reconnecter à enceinte principale, envoyer `AA-11-00` et lire les TLV du dump `AA 12` — le tag `39` doit être passé de `00` à `02` (WIRELESS_CONNECTED), et les tags `3A`/`3B` doivent apparaître.

Pour **dégrouper** : renvoyer la même trame avec `39 01 00` (PARTY_CONNECT_OFF).

---

## 8. Vérifications croisées avec les captures

Le décodage colle avec la toute première capture HCI :

| Observé | Interprétation confirmée |
|---|---|
| `AA 11 00` | REQ_DEV_INFO |
| `AA 12 3C 00 …` | RESP_DEV_INFO + TLV |
| `AA 84 06 <MAC tel>` | RET_PHONE_MAC_ADDRESS_TO_DEVICE |
| `AA 85 06 <MAC enceinte secondaire>` | SET_SECONDARY_SPEAKER_ADDRESS |
| `AA 82 06 FF×6` | RET_PHONE_MAC_ADDRESS, emplacement vide |
| TLV `39 01 00` | PARTY_CONNECT_OFF — cohérent avec un TWS inactif |
| TLV `35 01 01` | enceinte principale déjà configurée en canal GAUCHE |
| TLV `37 06 <MAC>` | adresse propre de l'enceinte |
# TWS JBL PartyBox — séquence confirmée

Capture `btsnoop_hci.log` du 2026-08-06 09:45, 6882 paquets, 170 trames ATT.
Appairage TWS réalisé par l'app JBL PartyBox et observé de bout en bout.

---

## La commande

```
AA-13-04-00-39-01-01
```

Décomposition :
| Octet | Rôle |
|---|---|
| `AA` | identifier |
| `13` | SET_DEV_INFO |
| `04` | longueur du payload |
| `00` | device index |
| `39 01 01` | TLV : PARTY_CONNECT_MODE = `01` (WIRELESS_CONNECTING) |

**Ni group ID, ni canal.** Ma reconstruction précédente (`AA-13-0D-00-3B-04-…-35-01-01`) était trop complète : `StereoFlowCommand` *sait* envoyer ces champs, mais l'app ne les utilise que lors de l'appairage initial. Pour **relier** deux enceintes déjà appairées, seul le tag `39` est envoyé — canal et groupe sont déjà en flash.

## L'ordre exact observé

| t (s) | Cible | Trame |
|---|---|---|
| 63.935 | — | l'app ouvre une connexion BLE vers enceinte secondaire (handle `0x000b`) |
| 67.335 | **enceinte secondaire** | `AA-13-04-00-39-01-01` |
| 67.503 | enceinte secondaire | ack `AA 00 02 13 00` |
| 67.593 | **enceinte principale** | `AA-13-04-00-39-01-01` |
| 67.756 | enceinte principale | ack `AA 00 02 13 00` |
| 71.643 | enceinte principale | `AA 12 04 00 39 01 02` → **CONNECTED** |

**enceinte secondaire (secondaire) reçoit la commande en premier**, 258 ms avant enceinte principale. Liaison établie ~4 s après.

## États lus dans les notifications

| Notification | Lecture |
|---|---|
| `AA 00 02 13 00` | accusé de réception, statut `00` = succès |
| `AA 12 04 00 39 01 01` | party connect = WIRELESS_CONNECTING |
| `AA 12 04 00 39 01 02` | party connect = **WIRELESS_CONNECTED** |
| `AA 12 04 00 35 01 01` | enceinte principale = canal GAUCHE (déjà en flash) |
| `… 35 01 02 … 37 06 BBBBBBBBBBBB … 40 10 TL1250-XXXXXXXXXX` | enceinte secondaire = canal DROITE, sa MAC, son modèle |
| `AA 85 06 AA AA AA AA AA AA` | enceinte secondaire déclare enceinte principale comme enceinte secondaire |
| `AA 12 07 00 36 01 00 38 01 02` | tag `38` (ROLE) passe de `00` à `02` |

## Opcodes complémentaires observés

| Opcode | Rôle |
|---|---|
| `EA` | keepalive — l'app l'envoie toutes les 5 s sur la connexion active |
| `B1` | SEND_DEVICE_ANALYTICS_REQUEST |
| `6A` / `6B` | SET / RET_LINK_MODE (non utilisés dans ce flux) |

## Procédure à reproduire

1. Réveiller les deux enceintes — la simple connexion BLE suffit.
2. Se connecter à **enceinte secondaire**, écrire `AA-13-04-00-39-01-01` sur `65786365-6C70-6F69-6E74-2E636F6D0002` (Write Without Response).
3. ~250 ms plus tard, se connecter à **enceinte principale** et écrire la même trame.
4. Attendre ~4 s. La liaison s'établit.

Pour **dégrouper** : même trame avec `39 01 00` (PARTY_CONNECT_OFF).

Vérification sans notifications : le son doit sortir des deux enceintes.

## Note pour le portage sur NAS

L'app maintient un keepalive `AA-EA-00` toutes les 5 s sur la connexion BLE. À vérifier lors du portage : si la liaison BLE tombe faute de keepalive, l'enceinte pourrait interrompre la liaison TWS. À tester avant de conclure.
