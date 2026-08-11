# Automation

Party Pair can be triggered from outside: a widget, a routine, an alarm, or Home Assistant.

---

## Widget and tile

The **widget** takes one home-screen cell and toggles on tap: switch on, or fade out and sleep if the speakers are playing. The icon turns to your accent colour while the pair holds.

A **quick-settings tile** is also available, added from the edit button in the notification shade.

## Samsung routines

Routines can only launch an app, so Party Pair installs two extra launcher shortcuts that appear in their list:

- **Start** — wakes the speakers and rebuilds the stereo link
- **Standby** — fades the sound out, then switches both off

In *Settings → Modes and Routines → Routines → +*, pick your trigger under **If**, then under **Then** choose *Open app* and select the shortcut. Nothing is displayed: the sequence runs and hands control back.

## Home Assistant

Using the Android companion app:

```yaml
action: notify.mobile_app_your_phone
data:
  message: command_activity
  data:
    intent_package_name: fr.boitedefete
    intent_class_name: fr.boitedefete.TriggerActivity
    intent_action: fr.boitedefete.action.POWER_OFF
```

Available actions:

| Action | Effect |
|---|---|
| `fr.boitedefete.action.START` | wake and pair |
| `fr.boitedefete.action.POWER_OFF` | fade out, then switch off |
| `fr.boitedefete.action.TOGGLE` | decide based on the speakers' actual state |
| `fr.boitedefete.action.UNLINK` | break the pair without switching off |

On the first call the companion app will ask for the "Display over other apps" permission. It is genuinely needed: since Android 12 a background app cannot start a service, and this permission is what lifts the restriction.

`command_broadcast_intent` also works, but only while Party Pair has been opened recently. Prefer `command_activity`.

**The phone must be within Bluetooth range**, since it is the phone that talks to the speakers. For a routine triggered as you leave home, make sure the command arrives while the phone is still there — otherwise see driving them from a Raspberry Pi, below.

## Tasker

Action *System → Send Intent*:

- Action: `fr.boitedefete.action.TOGGLE`
- Package: `fr.boitedefete`
- Class: `fr.boitedefete.TriggerActivity`
- Target: Activity

## adb

```bash
adb shell am start -n fr.boitedefete/.TriggerActivity \
  -a fr.boitedefete.action.TOGGLE
```

---

## Wake-up

In the settings, *Wake the speakers with my alarm* uses the **next alarm set on the phone**, whichever app created it. The speakers come on shortly before, the music starts, and the app reschedules itself for next time.

Android does not say which alarm comes from bedtime mode — nothing in the information it gives away its origin. An adjustable **time window** (4–11 by default) therefore filters out kitchen timers and daytime reminders.

Bedtime mode switches the radios off. The app waits up to three minutes for Bluetooth to return, then gives the stack a moment to settle.

You will need to grant the exact-alarm permission, and exempt the app from battery optimisation.

> **Keep your usual alarm as a backup.** An unplugged or out-of-range speaker should not be what makes you oversleep.

### Starting the right playlist

Fill in the **playlist name** in the settings. It is handed to the music app as a play request — the only public Android interface that actually starts something specific.

A **playlist link** can be pasted too: it opens the page, with no guarantee of starting playback. The app tries both, checking each time that sound is actually coming out before moving on.

Nothing obliges a music app to honour these requests. The result depends on it.

**One limitation worth knowing:** Android forbids a background app from opening another app. Triggered by an alarm, Party Pair therefore presses the play key — which resumes whatever was last queued — and, if nothing starts, posts a notification you can tap to launch the playlist. That notification appears on the lock screen.

---

## Volume, bass, balance

The volume applied on each wake-up uses the speaker's own 0–32 scale, so a loud night never turns into a loud morning.

**Balance** lowers the nearer speaker rather than raising the other, so the level you asked for is never exceeded.

**Bass boost** offers three settings, applied on every wake-up.

---

## Driving the speakers without a phone

Nothing about the protocol is Android-specific. From a Raspberry Pi or a NAS with Bluetooth, a few lines are enough:

```python
import asyncio
from bleak import BleakClient

TX = "65786365-6c70-6f69-6e74-2e636f6d0002"
TWS_LINK = bytes.fromhex("aa130400390101")

async def link(mac):
    async with BleakClient(mac) as client:
        await client.write_gatt_char(TX, TWS_LINK, response=False)

async def main():
    await link("AA:BB:CC:DD:EE:FF")   # secondary speaker
    await asyncio.sleep(0.25)
    await link("11:22:33:44:55:66")   # main speaker

asyncio.run(main())
```

Two properties make this comfortable: no connection needs to be held open, and the stereo link survives Bluetooth disconnection. A few seconds of contact is enough.

To have the speakers connect to your dongle rather than a phone, send `AA 84 06` followed by the six bytes of its address before the link command.

Expose the script as a `shell_command` or over MQTT, and Home Assistant drives the speakers without depending on a phone at all.

---

## Backup

The settings offer to copy your configuration to the clipboard and restore it by pasting. Useful before changing phones.

## Known limitations

The official JBL app and this one cannot talk to a speaker at the same time: close one before using the other.

If the secondary speaker does not answer, the sequence carries on with the main one alone and says so. A complete failure posts a notification naming the speaker at fault — useful when the trigger came from an alarm or a routine.
