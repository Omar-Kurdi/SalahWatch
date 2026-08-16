# Data safety form — answers

What to select in the Play Console's **Data safety** section, and why. Play's declarations are
binding: a wrong answer here is a policy violation regardless of what the app actually does, so each
answer below names the code it was read from.

Two definitions decide almost everything on this form:

- **Collected** — the data leaves the device, for any reason, even transiently.
- **Shared** — the data is transferred to a *third party*. Sending coordinates to someone else's API
  is sharing, even with no account and no identifier attached.

Data that stays on the device is neither, and is not declared at all.

---

## Top-level answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** — every outbound request is HTTPS |
| Do you provide a way for users to request that their data is deleted? | **Yes** — uninstalling deletes everything; there is nothing held off-device to request |

---

## Data types

### Location → Approximate location

| Field | Answer |
|---|---|
| Collected | **Yes** |
| Shared | **Yes** |
| Processed ephemerally | No — the coordinates are stored in app settings so the app works offline |
| Required or optional | **Optional** — a user can type a city or enter coordinates instead, and never grant the permission |
| Purpose | **App functionality** only |

Read from: `data/location/src/main/AndroidManifest.xml` declares `ACCESS_COARSE_LOCATION` and
nothing else. `AlAdhanClient` and `MawaqitClient` put latitude and longitude in query parameters.

**Precise location: not collected.** `ACCESS_FINE_LOCATION` is not declared anywhere in either app.

**Third parties receiving it:** AlAdhan (`api.aladhan.com`) and Mawaqit (`mawaqit.net`). Both are
contacted anonymously — no account, no API key, no advertising or installation identifier.

City lookup does **not** count here: it goes through Android's own `Geocoder` on the device
(`PlaceLookup`), so the query never passes through this app's code to a service of our choosing.

### Everything else — not collected

Declare **no** for all of the following. None of them is read, requested or transmitted anywhere in
either app:

| Category | Note |
|---|---|
| Personal info | No account, name, email, address, phone number or user ID exists |
| Financial info | No purchases, no payment handling |
| Health and fitness | Not read, despite this being a watch app |
| Messages, Photos, Videos, Audio, Files | No permission for any of them |
| Contacts, Calendar | Not read |
| App activity | **No analytics of any kind.** No page views, no searches, no interactions, no installed apps |
| Web browsing | Not applicable |
| App info and performance | **No crash logs, no diagnostics, no performance telemetry.** No Crashlytics, no Firebase, no third-party SDK |
| Device or other IDs | No advertising ID, no installation ID, no device fingerprint |

Verify by inspecting the release APK's permissions rather than trusting this table — see
[REVIEW_INSTRUCTIONS.md](REVIEW_INSTRUCTIONS.md).

---

## Permissions in the release builds, and why each exists

**Phone**

| Permission | Why |
|---|---|
| `INTERNET` | Fetching a year of prayer times |
| `ACCESS_COARSE_LOCATION` | Optional. Approximate position for the prayer-time calculation |
| `ACCESS_NETWORK_STATE` | WorkManager's network constraint — the sync waits for a connection rather than failing |
| `WAKE_LOCK`, `FOREGROUND_SERVICE` | Merged in by WorkManager for the daily background refresh |
| `RECEIVE_BOOT_COMPLETED` | So the refresh survives a restart |

**Watch**

| Permission | Why |
|---|---|
| `RECEIVE_BOOT_COMPLETED` | Re-registering the complication timeline after a restart |

That is the whole list. The watch has **no** `INTERNET` permission: the module it depends on for
prayer times has no networking dependency to merge one in, so the watch app is structurally
incapable of making a network request.

---

## Wording for the "Data safety" summary users see

Play generates most of this, but the section reads better with the optional explanation filled in:

```
SalahWatch sends your approximate location to the prayer-time service you choose, so it
can work out when the prayers are where you are. Nothing else leaves your device. There
is no account, no analytics and no advertising. Setting your location by typing a city
instead means the location permission is never used.
```
