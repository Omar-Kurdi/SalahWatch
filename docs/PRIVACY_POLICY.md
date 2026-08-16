# Privacy policy — SalahWatch

> **This file is the text, not the deliverable.** Google Play requires a privacy policy at a
> **publicly reachable URL**, entered in the Play Console and linked from the store listing. A
> Markdown file in a repository does not satisfy that, even a public one. Host this — GitHub Pages,
> a static host, anywhere reachable without a login — and put the resulting URL in the Console.
>
> Before publishing, replace the two placeholders below: `[CONTACT EMAIL]` and `[POLICY URL]`.

**Last updated:** 15 August 2026

---

## The short version

SalahWatch has no account, no analytics, no advertising and no tracking. It stores your settings and
a year of prayer times on your own devices. The only thing that ever leaves your phone is your
approximate location, sent to the prayer-time services so they can tell you when the prayers are
where you are.

---

## What the app stores on your device

- **Your settings** — location, calculation method, school, Iqama offsets, corrections, chosen
  mosque, theme and language.
- **A prayer timetable**, up to a year ahead, so the app works offline.

Both are held in the app's own private storage on the phone, and a copy is sent to your paired watch
so the watch keeps working when the phone is away. Nothing is uploaded to any server operated by us,
because there is no server operated by us. Uninstalling the app deletes all of it.

## What leaves your device, and where it goes

Your **approximate coordinates** are sent to whichever prayer-time service you have chosen:

| Service | Operator | What is sent | Why |
|---|---|---|---|
| AlAdhan | [aladhan.com](https://aladhan.com) | Latitude and longitude, calculation method, school | To calculate a year of prayer times |
| Mawaqit | [mawaqit.net](https://mawaqit.net) | Latitude and longitude when searching for mosques; afterwards only the chosen mosque's public page address | To find nearby mosques and read the timetable they publish |

Both are requested without any account, key or identifier. Nothing that identifies you or your
device is attached — no advertising ID, no installation ID, no device fingerprint. Each service
receives your coordinates and its own server logs will record the request, in the ordinary way any
web request is logged. Their handling of that is governed by their own privacy policies, linked
above.

If you set your location by typing a city or entering coordinates by hand, no location permission is
used at all. City lookup goes through Android's own geocoder on the device, not through us.

## Location permission

The app requests **approximate (coarse) location only**, and only while it is open.

- **Precise location is never requested.** Prayer times do not change measurably over a few
  kilometres, so precise location would spend GPS power for an identical answer.
- **Background location is never requested.** The app has no reason to know where you are when it is
  closed, and the permission is not declared in the app at all.
- **The permission is optional.** Choose "Set it myself" in Settings and the app never asks for it.

## What the app does not do

- No user accounts, sign-in or profiles.
- No analytics, crash reporting, telemetry or usage measurement of any kind.
- No advertising, and no advertising identifier is read.
- No third-party tracking libraries.
- No contacts, photos, files, microphone, camera, calendar or messages.
- No data is sold or shared with anyone for advertising or any other purpose.

## The watch app

The watch app has **no internet permission**. It cannot make a network request, not by policy but by
construction: the code it is built from contains no networking. It receives its timetable from the
paired phone over the Wear OS data layer and reads it locally.

## Children

The app is suitable for all ages, collects nothing, and has no features directed at children
specifically.

## Your control over your data

- **Delete everything:** uninstall the app. All settings and cached times go with it. Do this on the
  watch as well as the phone.
- **Delete just the cached times:** clear the app's storage in Android's app settings.
- **Stop sharing your location:** revoke the location permission, or switch to "Set it myself".
- **Stop contacting Mawaqit:** choose "Calculation" as your prayer-time source.

There is no data held by us to request, export or erase, because none is collected.

## Changes to this policy

Any change will be published at [POLICY URL] with the date above updated. Material changes will be
noted in the app's release notes.

## Contact

Questions about this policy: [CONTACT EMAIL]
