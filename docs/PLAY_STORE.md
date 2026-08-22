# Play Store listing

Everything the Play Console asks for, written out so it can be pasted rather than composed under
time pressure. Facts here are taken from the built release APKs, not from intent — see
[REVIEW_INSTRUCTIONS.md](REVIEW_INSTRUCTIONS.md) for how to re-verify them.

---

## Blockers — none of this can be submitted until these are cleared

| # | Blocker | Why it blocks | Where |
|---|---|---|---|
| 1 | **Release builds are signed with the debug key** | Play rejects the debug certificate outright. Worse, anything already sideloaded with it can never be updated by a properly-signed build — those installs must be uninstalled | `app/build.gradle.kts`, `wear/build.gradle.kts`, both `buildTypes.release` |
| 2 | ~~No licence chosen~~ — **done** | [LICENSE](../LICENSE) is a personal and non-commercial licence. Not an open-source one, so the project must not be described as open source. `adhan2`'s MIT notice and the Apache 2.0 text are carried in [THIRD_PARTY_NOTICES.txt](../THIRD_PARTY_NOTICES.txt), shipped inside both APKs | [LICENSE](../LICENSE) |
| 3 | **Privacy policy has no public URL** | Play requires a URL, not a file. [PRIVACY_POLICY.md](PRIVACY_POLICY.md) is the text; it still has to be *hosted* somewhere reachable without login | see that file's header |
| 4 | **`versionCode` is 1 on both apps** | Fine for a first upload, but phone and watch must be bumped together for every subsequent one | both `build.gradle.kts` |

Items 1 and 3 are the remaining hard stops. Nothing else on this page matters until they are done.

---

## Identity

| Field | Value |
|---|---|
| App name | **SalahWatch** |
| `applicationId` | `com.salahwatch` — **permanent once published**, identical on phone and watch as Wear delivery requires |
| Default language | English (United Kingdom) |
| Also localised | Arabic (`ar`) — full translation, right-to-left |
| Category | Lifestyle |
| Tags | Prayer times, Islam, Wear OS, Watch face complications |
| Content rating | Everyone — no user content, no ads, no purchases, no communication features |
| Contains ads | No |
| In-app purchases | No |

---

## Build facts

Read out of the release APKs with `aapt2 dump badging` / `dump xmltree`.

| | Phone | Watch |
|---|---|---|
| `minSdk` | 29 (Android 10) | 33 (Wear OS 4) |
| `targetSdk` / `compileSdk` | 36 | 36 |
| Permissions | `INTERNET`, `ACCESS_COARSE_LOCATION`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | `RECEIVE_BOOT_COMPLETED` only |
| Services | 1 (data-layer listener) | 7 — five complication providers, the listener, and Room's invalidation service |

`ACCESS_BACKGROUND_LOCATION` is never declared, which keeps this clear of Play's background-location
declaration process entirely. Coarse only, foreground only.

The watch APK carries no `INTERNET` permission, and that is structural rather than incidental: the
module the watch depends on has no networking dependency to merge one in. Worth stating in the
listing, because "the watch app cannot phone home" is a stronger claim than "it doesn't".

---

## Store listing copy

### App name (30 characters max)

```
SalahWatch
```

### Short description (80 characters max)

```
Prayer times on your watch face. Adhan, Iqama and a live countdown at a glance.
```
*(78 characters.)*

### Full description (4000 characters max)

```
SalahWatch puts Islamic prayer times on your watch face, where you actually look.

Five separate Wear OS complications mean you can fill several slots on one face
independently — the next prayer in one, a countdown in another, a progress ring in a
third. Tap any of them to open the watch app for the full day.

WHAT YOU CAN PUT ON YOUR WATCH FACE

• Prayer Time — the next prayer and its time, rolling over on its own
• Adhan Time/Countdown — the time, becoming a countdown half an hour before the Adhan
• Prayer Progress — the whole cycle: the time, a countdown from an hour out, a countdown
  to the Iqama, then a timer counting up through the first minutes of the prayer
• Adhan Countdown — how long until the next call to prayer
• Adhan / Iqama Countdown — that countdown, then one to the congregation

Each of them draws as an arc in a gauge slot and as text everywhere else, so they fit
whatever your watch face offers. Countdowns show minutes at range and switch to seconds
as the moment approaches.

TIMES THAT MATCH YOUR MOSQUE

Most apps calculate prayer times from the sun's angles. That is unavoidably an
approximation of a local timetable, because national authorities apply conventions no
set of angles reproduces — sunrise in particular is method-independent, so no amount of
configuring can move it.

So SalahWatch offers a second source: the published timetable of a mosque near you, via
Mawaqit. Where a mosque follows its national timetable, those times are that timetable
rather than an estimate of it. Checked against an official national timetable for a full
month, a nearby mosque matched all six times on all thirty-one days.

Every mosque calendar is validated against astronomy before it is used. Sunrise depends
on nothing but where and when you are, which makes it computable exactly — and therefore
usable to check whether the rest of a mosque's published data lines up. A calendar that
fails falls back to calculation rather than showing you something confidently wrong.

ON YOUR PHONE TOO

Two home screen widgets, and both count down without the app running at all. One shows
all five prayers and sunrise with their Adhan and Iqama times, and scales to whatever
size you give it. The other is a single row: five prayers across, with the next one
boxed. Browse any day within a month either way to check a time in advance, or look back
at one.

WORKS WITHOUT YOUR PHONE

A whole year of times is fetched at once and kept on the watch. After one sync the watch
needs nothing: not your phone, not a network. The watch app has no internet permission at
all.

BUILT NOT TO DRAIN YOUR BATTERY

Complications hold a timeline of every transition the watch face needs, so nothing has to
wake the device when a prayer arrives. There is no alarm per prayer and no polling.

ALSO

• 23 calculation methods, with the angles shown so you can see what each one does
• Shafi and Hanafi, each explaining what it does to Asr
• Iqama from your own offsets, or from the mosque's published times
• Per-prayer corrections, in minutes, against your local timetable
• Gregorian and Hijri dates, from the Umm al-Qura calendar
• Two home screen widgets for the phone, one full, one a single row
• Any date within a month either way, by day or from a month grid
• Full Arabic, including right-to-left layout
• Light and dark themes on the phone; the watch is always dark, because an unlit pixel
  costs no battery on an AMOLED display

PRIVACY

No account. No analytics. No advertising. No tracking of any kind. Location is coarse
and foreground-only, and background location is never requested. Your coordinates are
sent to the prayer-time services only to work out prayer times for where you are.
```

### Promotional text / release notes (500 characters max)

```
First release. Five Wear OS complications, two home screen widgets for the phone, prayer
times from calculation or from a nearby mosque's own published timetable, a full year
cached for offline use, English and Arabic.
```

---

## Assets to produce

See [SCREENSHOT_PLAN.md](SCREENSHOT_PLAN.md) for the shot list. Sizes:

| Asset | Requirement | Notes |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit, no alpha | Render `ic_launcher_foreground` over `ic_launcher_background` — do **not** re-draw the crescent by hand; its geometry is generated |
| Feature graphic | 1024 × 500 PNG or JPEG | No transparency. Text in it must be legible at thumbnail size |
| Phone screenshots | 2–8, 16:9 or 9:16, 320–3840 px per side | Required |
| Wear OS screenshots | 1–8, **384 × 384**, round | Required for the Wear form factor, and separate from the phone set |

---

## Wear OS delivery

The watch app ships as a **separate APK under the same `applicationId`**, uploaded to the Wear OS
form factor of the same Play app. Both must be signed by the same key and both `versionCode`s must
be raised on every release, or Play will refuse the pair.

The Wear listing needs its own screenshots and its own short description; it does not inherit the
phone's.

---

## Pre-submission checklist

- [ ] Real upload keystore created, stored outside the repo, backed up; `signingConfig` replaced in both modules
- [x] Licence chosen, `README.md` updated, `adhan2`'s MIT notice carried in both APKs
- [ ] Privacy policy hosted at a public URL; URL entered in the Play Console
- [ ] Data safety form completed from [DATA_SAFETY.md](DATA_SAFETY.md)
- [ ] Content rating questionnaire completed
- [ ] Phone and Wear screenshots captured per [SCREENSHOT_PLAN.md](SCREENSHOT_PLAN.md)
- [ ] Both widgets placed on a real home screen and watched across an Adhan and its Iqama — the
      `Chronometer` counting down rather than up, and the alarm firing close to the boundary
- [ ] Schedule widget resized from four cells down to two: all six entries still present at every
      size, nothing clipped
- [ ] Arabic checked by eye on a real screen: numerals European, times still in a straight
      column, prayer names no longer letter-spaced apart
- [ ] Reviewer notes pasted from [REVIEW_INSTRUCTIONS.md](REVIEW_INSTRUCTIONS.md)
- [ ] Both APKs verified with `aapt2 dump badging` after the signing change — check the artifact, not the build result
- [ ] Internal testing track exercised on a real phone and a real watch first
