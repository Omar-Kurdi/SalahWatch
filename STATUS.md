# Status

Where the project actually is, what has and has not been verified, and what remains before release.
Read this first, then [RESEARCH.md](RESEARCH.md) for why the technical decisions were made.

---

## Current state

Ten modules, two apps, **261 unit tests passing**, detekt clean, lint clean, all four APKs
building.

| | State |
|---|---|
| Wear app + 5 complication providers | **Verified on a real watch face** — Prayer Time and Adhan Countdown both live |
| Watch app | **Verified on device** — next prayer, countdown, both calendars, bezel ring |
| Phone app — Today, Settings, location | **Verified on device** |
| Phone app — date browsing (±1 month) | **Verified on device**, arrows and month grid both |
| Home screen widgets (schedule + strip) | **Verified on a home screen** — both render, the countdown ticks down |
| Prayer times — calculation | Working, cached a year at a time |
| Prayer times — nearby mosque | **Verified on device** — a real mosque was found and listed |
| Phone → watch sync (Data Layer) | **Verified between two real devices** |
| Light theme | Built, **not yet seen on a screen** |
| Arabic | **Verified on device** — RTL, joined names, European digits throughout |
| Store paperwork | Written, in `docs/`. Three hard blockers remain |

### Devices are available — check before assuming otherwise

A Samsung **SM-S918B** phone (Android 16, density 450) and a **SM-L330** watch (Wear OS, API 36,
480x480 @ 340) connect over adb, with both apps installed. Run `adb devices` at the moment you need
it; an earlier version of this file asserted no device existed, went stale within the same day, and
led to a run of claims being reported as unverifiable when they were not.

Both usually sit on a secured lock screen and re-lock quickly. Anything involving the launcher needs
the user to unlock and keep them awake. Two devices means every `adb` call needs `-s <serial>`.

### What the devices have settled

- **Phone → watch sync works.** A fresh watch install showed "Set a location in the phone app";
  after the phone app republished, the watch had the full schedule. This was the longest-standing
  unverified item in the project.
- **The nearby-mosque source works.** A real mosque near the configured location was found and
  listed, with its own Iqama offsets applied (Maghrib +8, the rest +20/+25).
- **Date browsing works**, and the design decision behind it holds up: on another date the clock,
  the day arc and the next-prayer panel are all absent, leaving a plain timetable, and the Hijri
  date and times both track the chosen day.
- **Arabic works**, including the part that could only be inferred before: every numeral renders in
  European digits (`4:35 ص`, `خلال 5 س 6 د`) rather than Arabic-Indic, prayer names read as joined
  words rather than spaced-out letters, and the layout mirrors.
- **The strip widget's real placement is 406dp x 96dp**, which its size tiers are now tuned
  against rather than an assumed ~70dp cell.
- **Both widgets render on a home screen**, and the `Chronometer` counts *down* rather than up —
  the one flag that looks identical in code either way.
- **Two complications run on a real watch face**, showing the next prayer and its countdown.
- **The location permission is actually requested.** It never was: the manifest declared
  `ACCESS_COARSE_LOCATION` and the provider checked it, but nothing asked, so "use my location" did
  nothing on a fresh install and the system settings screen was the only way to grant it. Now the
  request fires when that option is chosen, and a denial falls back to manual rather than leaving
  the radio on a mode that cannot work.

Looking at it also found a defect no test would have: the day arc's end labels were drawn from
`Prayer.shortName` — an ASCII identifier meant for the complication text budget — so they stayed
"Fajr" and "Isha" in English while every other word on the screen was Arabic. Fixed.

### Still not verified on hardware

- **Whether the alarm fires close enough to a prayer boundary to look instant.** This needs sitting
  with a widget through an Adhan; everything up to that point is confirmed.
- **A complication rolling over at the Adhan**, and a gauge sweeping without resetting when its text
  switches to seconds. Same reason — it takes a prayer passing while someone watches.
- **The light theme.**
- **The schedule widget's spare vertical space and truncated labels.** At three cells wide the
  prayer name column clips "Maghrib" to "Maghr…" and the date to "Satur…", and roughly a quarter of
  its height goes unused. Seen, not yet addressed.

The seven glyphs — six prayers plus the mosque shown during an Iqama countdown — were rendered to
PNG and inspected at 96px and 20px, so they are known to read at size, but not how they sit against
a wallpaper. Two were redrawn after inspection: Sunrise and Maghrib had their direction arrow hidden
inside the sun's arc, and the mosque's dome merged into its base at small size.

---

## Remaining before release

### 1. Take the screenshots

[docs/SCREENSHOT_PLAN.md](docs/SCREENSHOT_PLAN.md) is written as a device-test pass, not just an
asset list. Several of its shots have now been taken and the screens behind them verified — Today,
date browsing, Arabic, the mosque picker, the watch app. What is left is the light theme, the two
widgets and the complications on a face, all of which need a placement or a theme switch first.

### 2. Place both widgets and a complication, and watch them through a prayer

The one category adb cannot reach on its own: `cmd appwidget` has no shell implementation, and
adding a complication to a watch face is a manual step. Someone has to place them once; after that
they survive reinstalls and can be screenshotted freely.

What only that will show:

- Whether the RemoteViews layouts fit, at 5x1 and resized down.
- Whether the `Chronometer` counts **down** rather than up — a wrong flag looks identical in code.
- Whether an inexact alarm lands close enough to a prayer boundary to read as instant.
- Whether the brightened palette holds up over a real wallpaper.
- On the watch: that a countdown rolls over at the Adhan, and a gauge sweeps without resetting when
  its text switches to seconds.

### 3. ~~Verify phone → watch sync between real devices~~ — done

Exercised between an SM-S918B and an SM-L330. A freshly installed watch app showed "Set a location
in the phone app"; once the phone republished, the watch had the full schedule, the countdown and
the bezel ring. Language mirroring rides the same path and now has at least one real round trip
behind it.

The nearby-mosque source is likewise no longer untested: a real mosque was found near the configured
location, listed with its own Iqama offsets. What remains unknown there is coverage elsewhere —
Mawaqit is dense in some cities and sparse in others — and whether the 45-minute sunrise tolerance
rejects the right calendars in practice.

### 4. ~~Choose a licence~~ — chosen

[LICENSE](LICENSE) is a personal and non-commercial licence; commercial use needs written
permission. Note this is **not** an open-source licence, which is a deliberate choice rather than an
oversight — it does not affect Play distribution, but it does mean the project cannot be described
as open source.

Third-party attribution is done: [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) carries adhan2's
MIT notice and the full Apache 2.0 text, is synced into both APKs as a raw resource by a Gradle task
so the two cannot drift, and is readable in the phone app under Settings → About. Verified present
in all four artifacts rather than assumed from the build.

### 5. Release signing

Release APKs are currently signed with the **debug key** so they can be sideloaded. Play rejects
that outright, and an app installed with the debug key cannot later be updated by a properly-signed
one — those installs have to be removed first.

### 6. Host the privacy policy

[docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) is the text. Play requires a reachable **URL**, so
the file alone does not satisfy it. Two placeholders in it need filling.

---

## Official timetables

Al Ain and Dubai ship the authority's own published table as an asset; see
`docs/OFFICIAL_TIMETABLES.md` for the format and the refresh procedure.

**Both bundles end 2027-06-05.** Past that the app falls back to calculation and marks the days as
estimates, so nothing breaks — but the times stop being exact. Refresh before mid-2027; the site
publishes far enough ahead that doing it once a year is comfortable.

Not yet seen on a device: the area picker, and what a day past the end of a bundle looks like in the
UI. Both are covered by unit tests, neither has been looked at on a screen.

## Decisions worth not revisiting

| Decision | Why |
|---|---|
| Every complication is a **timeline provider** | A provider that answers once and declares no update period is never asked again, so it freezes on whichever prayer was next when it was added. This is what makes rollover work, not an optimisation |
| **Five** complications | Each is a distinct thing to look at, and a watch face picker cannot expose a preference — one configurable provider could only fill one slot usefully |
| A gauge measures the **segment**, not the entry | `arcFrom`/`arcTo` on `PrayerPhase`. Measured from the entry, an arc empties and restarts when a countdown switches to seconds |
| Corrections are applied **on read**, never on fetch | A mosque's published table has no parameter to tune, so a correction sent to the API reaches only the calculated path. On read it means the same thing under both sources, and takes effect instantly |
| Per-app language is implemented **directly** | `AppCompatDelegate.setApplicationLocales` no-ops without an AppCompat delegate, and `AppCompatActivity` will not inflate under a framework theme. See `LanguageOverride` |
| **minSdk 33** on wear | Complication dynamic values start at API 33; below it a progress arc cannot animate without polling |
| **JDK 21** required | `adhan2` ships Java 21 bytecode; JDK 17 fails at runtime |
| Kotlin 2.4.10 + **KSP 2.3.11** | KSP versions its own line; 2.3.10 added Kotlin 2.4 support. There is no 2.4.x KSP |
| compileSdk **36**, Compose BOM pinned to **2026.06.01** | Compose 1.12 and androidx.core 1.19 need compileSdk 37, which is preview-channel only and past AGP 8's ceiling |
| No dependency-injection framework | The graph is a handful of repositories with no variants |
| `:data:prayer-times` has **no network** | Keeps `INTERNET` and `WAKE_LOCK` out of the watch APK. Fetching lives in `:data:prayer-sync` |

---

## Environment

Installed user-local because the machine runs an immutable Fedora variant, so no layering and no
reboot:

```
JDK 21   ~/.local/share/prayer-toolchain/jdk-21.0.12+8     ← JAVA_HOME must point here
Gradle   ~/.local/share/prayer-toolchain/gradle-8.14.5     ← only to regenerate the wrapper
SDK      ~/Android/Sdk                                      ← platform 36, build-tools 36.1.0, adb
```

```bash
JAVA_HOME=~/.local/share/prayer-toolchain/jdk-21.0.12+8 ./gradlew build detekt
```

---

## Accuracy, measured

Validated against an official national prayer timetable over a full month:

| Source | Result |
|---|---|
| Calculation (AlAdhan) | Five prayers within ±1 min; **Sunrise systematically 4–5 min out every day** |
| Offline fallback (adhan2) | Within 3 min of AlAdhan (Asr worst, school-dependent) |
| **Nearby mosque (Mawaqit)** | **31/31 days, all six times, zero deviation** |

Sunrise is the tell. It is method-independent, so no calculation method can move it — which is why
calculation can never match a national table exactly, and why a mosque's published one can.

---

## Traps worth not falling into again

- **A complication that is not a timeline never advances.** `UPDATE_PERIOD_SECONDS=0` plus a single
  static answer means the platform never asks again. It looks fine the day you add it.
- **`evenOdd` on two circles is not a crescent.** It is the symmetric difference — both lunes. A
  crescent is one closed path of two arcs.
- **Centring a crescent's outer circle does not centre the crescent.** Centre the bounding box.
  Generate the path from parameters and render it to look at, rather than reverse-engineering arc
  flags — wrong flags still produce a plausible-looking area.
- **Regex surgery on `AndroidManifest.xml` deleted 11 of 12 services** and the release APK shipped
  with one complication. Regenerate the manifest from a list instead.
- **Mosque calendars carry six *or seven* times.** Reading from the front shifts every prayer by one
  slot, silently. Read the last six.
- **Mawaqit's `iqama` field carries offsets *or* wall-clock times**, in the same key, with nothing to
  say which. Handle both or it silently does nothing for half the mosques.
- **A screen that never observes its data source shows the previous change's result.** The database
  is written by a worker; the UI has to watch it.
- **A `StateFlow` seeded with defaults cannot say "not loaded yet".** Driving a side effect from one
  acts on the placeholder first.
- **Verify the built artifact, not the build result.** Both the manifest bug and a missing
  `RECEIVE_BOOT_COMPLETED` were found by inspecting an APK, not by the build failing.
