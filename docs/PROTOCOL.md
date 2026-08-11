# JBL PartyBox BLE protocol

Reconstructed by cross-referencing Bluetooth HCI captures from an Android phone with a decompilation of the official JBL PartyBox app (3.14.1). Verified against two PartyBox 710.

To the best of our knowledge this was not documented publicly anywhere. It is reusable from any BLE client.

*[Version française](fr/PROTOCOLE.md)*

---

## 1. GATT transport

| | |
|---|---|
| Service | `65786365-6C70-6F69-6E74-2E636F6D0000` |
| TX, write | `65786365-6C70-6F69-6E74-2E636F6D0002` |
| RX, notifications | `65786365-6C70-6F69-6E74-2E636F6D0001` |
| Requested MTU | 512 |
| Write mode | Write Without Response |

The service UUID reads as `excelpoint.com` in ASCII — the supplier of the Bluetooth audio module, whose reference firmware leaked its own identifier into the product.

**The notify characteristic has no CCCD descriptor.** The speaker pushes notifications unprompted; there is nothing to subscribe to. On Android, calling `setCharacteristicNotification()` is enough. Tools that insist on writing a descriptor — nRF Connect among them — will never show you these notifications.

## 2. Frame format

```
AA <command> <payload length> <payload...>
```

One identifier byte, fixed at `0xAA`. One length byte. No frame-level checksum, though a CRC16 field exists at the TLV level.

**Opcode convention** — the low nibble carries the operation:

| Low nibble | Meaning |
|---|---|
| `1` | request |
| `2` | response |
| `3` | set |
| `4` | notification |

Responses are a status byte followed by tag/length/value triplets.

## 3. Opcodes

| Hex | Name |
|---|---|
| `03` | device action — payload `05` powers on, `04` powers off |
| `11` / `12` / `13` | request / response / set device info |
| `16` | role info |
| `31` / `32` / `33` | request / response / set light info |
| `34` / `35` | active light pattern / set custom pattern |
| `41` / `42` / `43` | request / response / set player info |
| `51` / `52` / `53` / `54` | DJ effect status |
| `61` / `62` / `63` | bass boost |
| `6A` / `6B` | set / return link mode |
| `71` / `72` / `73` / `74` | karaoke status |
| `81` / `82` | request / return phone MAC |
| `83` / `84` | request from device / send to device |
| `85` | **secondary speaker address** — received only, never sent by the app |
| `91` / `92` / `93` | simple EQ |
| `9D` / `9E` | battery status |
| `A1` / `A2` | device feature info |
| `B1` | analytics request |
| `EA` | keepalive, sent every 5 s by the official app |
| `F4` | identify device |

## 4. TLV tags

| Tag | Meaning |
|---|---|
| `35` | active channel |
| `36` | audio source |
| `37` | own MAC address |
| `38` | role |
| `39` | party connect mode |
| `3A` | stereo group name, up to 16 bytes UTF-8 |
| `3B` | stereo group ID, up to 8 bytes |
| `40` | model identifier |
| `41` / `42` / `43` | player play / volume / mute |
| `44` | CRC16 |
| `46` / `47` | primary / secondary speaker volume |

**Audio channel**: `00` none, `01` left, `02` right.
**Party connect**: `00` off, `01` connecting, `02` connected.
**Volume**: 0 to 32. The official app caps its slider there.

## 5. Waking a speaker

```
AA 03 01 05
```

In practice **opening a BLE connection is enough** to bring a speaker out of standby — the controller stays powered and starts the amplifier on the first link. The ten commands the official app fires on connection are state synchronisation, not a wake-up call.

Expect a `AA 00` acknowledgement carrying the command and a status byte.

## 6. Stereo pairing

```
AA 13 04 00 39 01 01
```

Byte by byte: identifier, `13` set device info, payload length 4, device index `00`, then the TLV `39 01 01` — party connect mode set to *connecting*.

**No group ID, no channel.** The command builder in the app can send those fields, but only does so during initial pairing. To *re-establish* a link between speakers already paired, the connect-mode tag alone is enough: channel and group are held in the speakers' own flash.

### Observed order

| t (s) | Target | Frame |
|---|---|---|
| 63.9 | — | the app opens a BLE connection to the secondary speaker |
| 67.3 | secondary | `AA 13 04 00 39 01 01` |
| 67.5 | secondary | ack `AA 00 02 13 00` |
| 67.6 | main | `AA 13 04 00 39 01 01` |
| 67.8 | main | ack `AA 00 02 13 00` |
| 71.6 | main | `AA 12 04 00 39 01 02` → **connected** |

The secondary speaker receives the command 258 ms before the main one. The link settles about four seconds later.

To unlink, send the same frame with `39 01 00`.

## 7. Other useful commands

| Purpose | Frame |
|---|---|
| Connect to a classic Bluetooth address | `AA 84 06 <6 bytes>` |
| Set volume, 0–32 | `AA 43 04 00 42 01 <level>` |
| Volume of the main speaker | `AA 43 04 00 46 01 <level>` |
| Volume of the secondary speaker | `AA 43 04 00 47 01 <level>` |
| Bass boost, 0 off / 1 / 2 | `AA 63 01 <level>` |
| Assign a channel, 1 left / 2 right | `AA 13 04 00 35 01 <channel>` |
| Request device info | `AA 11 00` |
| Request player info | `AA 41 00` |

`AA 84 06` takes the address of **whoever is asking**. From a phone it holds the phone's own classic Bluetooth address; from a Raspberry Pi, the dongle's. This is what makes the speaker come to you for audio, and it is what allows the protocol to be driven from anything.

## 8. Reading the state

Sending `AA 11 00` yields several `AA 12` frames: one long full state dump, then short partial updates. **Reading only the first is a coin toss** — walk the responses until one actually carries the tag you asked for.

Useful readings:

- tag `39` = `02` — the stereo pair is up
- tag `35` — which channel this speaker holds
- tag `37` — its own address
- tag `40` — its model identifier

The speaker also announces `AA 85 06 <address>` unprompted on connection: the partner it remembers. `FF:FF:FF:FF:FF:FF` means no pair has ever been formed — the initial pairing has to go through the official app.

## 9. Practical notes

**No pairing is needed.** No security exchange appears anywhere in the captures: the BLE control channel works without bonding. A speaker that only ever receives commands need not be paired with the phone at all — better if it isn't, since it then stops occupying a Bluetooth connection.

**Discovery**: PartyBox speakers advertise service data under the 16-bit UUID `0xFDDF`. Across a capture holding 64 advertising devices, only the two speakers carried it. It makes a clean filter.

**No keepalive is required.** The official app sends `AA EA 00` every five seconds, but the stereo link holds without it — and survives the BLE connection closing entirely. A few seconds of contact is enough to set everything up.

**Absolute volume**: once A2DP is connected, Android mirrors its own media volume onto the speaker, overwriting anything set earlier over BLE. Apply volume *after* the audio link, not before.

## 10. Method

Three sources, cross-checked:

1. **Bluetooth HCI captures** from Android developer options. Note that the always-on in-memory log only records events, never payloads: the full log has to be enabled explicitly, and the Bluetooth adapter restarted for it to take effect.
2. **Decompiling the official app** with jadx. It is not obfuscated, and the SDK carries readable names: `PacketFormat`, `StereoFlowCommand`, `TWSControlImpl`.
3. **Verification on real hardware**, replaying each command and observing the result.

Where the two sources disagreed, the capture won.
