# Security review

An audit of everything Party Pair exposes to the outside world, what it asks of the system, and what it does with your data.

Last reviewed for version 1.0.4.

---

## Data handling

**Nothing leaves the phone.** The app makes exactly one network request: a check against the GitHub releases API. No analytics, no crash reporting, no telemetry, no advertising SDK.

**What is stored**, in the app's private preferences:

| | |
|---|---|
| Speaker names and Bluetooth addresses | needed to reach them |
| The phone's own Bluetooth address | handed to the speaker so it knows where to connect |
| Volume, balance, bass, playlist name and link | your preferences |

None of it is sensitive beyond the ordinary, but a Bluetooth address does identify a device. Two consequences worth knowing:

- **System backup** is enabled, so preferences follow you to a new phone. The downloaded update file is explicitly excluded from backups.
- **The clipboard export** puts your configuration in plain text. That is the point of the feature, but the clipboard is readable by other apps on some Android versions — paste it somewhere private and clear it afterwards.

---

## Exposed components

Four entry points are reachable by other apps, which is what makes automation possible.

| Component | What it accepts |
|---|---|
| `MainActivity` | launcher entry |
| `TriggerActivity` | start, standby, toggle, unlink, play music |
| `StartShortcut` / `StandbyShortcut` | launcher entries for routines |
| `CommandReceiver` | the same actions, by broadcast |

**None is protected by a permission.** Any app on the phone could therefore turn your speakers on or off.

This is deliberate: requiring a custom permission would break Home Assistant, Tasker and Samsung routines, which cannot hold it. The trade-off seemed right given what is at stake — the worst an attacker achieves is playing with your speakers, and they must already be running code on your phone.

Everything else — the foreground service, the alarm receiver, the widget provider, the file provider — is not exported.

---

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` | talk to the speakers |
| `BLUETOOTH_SCAN` | find them during setup, flagged `neverForLocation` so no location data is involved |
| `ACCESS_FINE_LOCATION` | Android 11 and below only, where BLE scanning required it |
| `INTERNET` | the update check, nothing else |
| `POST_NOTIFICATIONS` | timer countdown, failure alerts |
| `SCHEDULE_EXACT_ALARM` | wake before your alarm |
| `RECEIVE_BOOT_COMPLETED` | reschedule that alarm after a restart |
| `FOREGROUND_SERVICE` | keep the sequence alive with the screen off |
| `USE_FULL_SCREEN_INTENT` | offer to start the music on a locked screen at wake-up |
| `REQUEST_INSTALL_PACKAGES` | see below |

An optional **notification listener** service is also declared. It reads no notifications: Android only grants access to the list of active media sessions through such a service, and the app uses it solely to check whether the play key would reach your chosen music app rather than some other player. The permission is off unless you enable it in system settings, and everything works without it.

No camera, no microphone, no contacts, no storage.

---

## Updates: the sharpest edge

`REQUEST_INSTALL_PACKAGES` lets the app download an APK and hand it to the system installer. That is real power, and it deserves scrutiny.

**What protects you:**

- The download must be HTTPS **and** served by `github.com` or its release hosts. Redirects are followed one at a time and each hop is checked, rather than trusting the HTTP stack to land somewhere sane.
- Android installs nothing without your explicit confirmation.
- **Android refuses to replace an app with one signed by a different key.** Even a substituted APK could not overwrite Party Pair — it would be rejected outright.

**What remains:** you are trusting the GitHub repository. If it were compromised, a malicious release could be offered to you. The signature check above limits the damage, but it is worth knowing.

If that trade-off does not suit you, delete `UpdateChecker.kt`, drop the `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions, and update by hand. The app loses nothing else.

---

## Bluetooth

The app writes to a proprietary characteristic on speakers **you** designated during setup. It never scans and connects on its own initiative, never pairs, and never touches any other device.

Discovery filters on the service identifier the PartyBox range advertises, so the setup list shows your speakers rather than every device in the building.

The link carries no personal data — only volume, channel and pairing commands.

---

## Signing

Released APKs are signed with a debug key committed to the repository. This is intentional: without a fixed key, every build would produce a different signature and Android would refuse to install an update over the previous one.

**This key is public.** It proves nothing about who built the APK. If you are distributing widely, generate your own release key and pass it through GitHub secrets.

---

## Reporting a problem

Something looks wrong? [Open an issue](https://github.com/louim-lbs/PartyPair/issues). The whole source is here — reading it is the best audit there is.
