# Status

Where the project actually is, what has and has not been verified, and what remains before release.
Read this first, then [RESEARCH.md](RESEARCH.md) for why the technical decisions were made.

---

## Current state

Ten modules, two apps, **211 unit tests passing**, detekt clean, lint clean, all four APKs
building.

| | State |
|---|---|
| Wear app + 5 complication providers | Built; complications render in the watch face picker |
| Phone app — Today, Settings, location | Built and installed |
| Phone app — date browsing (±1 month) | Built, **not yet seen on a screen** |
| Home screen widget | Built and unit-tested, **not yet placed on a home screen** |
| Prayer times — calculation | Working, cached a year at a time |
| Prayer times — nearby mosque | Built and unit-tested, **not yet run on a device** |
| Phone → watch sync (Data Layer) | Built, **not yet tested between two real devices** |
| Light theme | Built, **not yet seen on a screen** |
| Arabic | Complete across every screen, the widget, and every complication |
| Store paperwork | Written, in `docs/`. Three hard blockers remain |

### Not yet verified on hardware

The nearby-mosque source, the Data Layer sync, the light theme, the day arc, the watch ring, Arabic,
the date picker, the home screen widget, and the complications on a real watch face.

The phase logic is unit-tested, but two things can only be confirmed by sitting with a watch through
a prayer: that a countdown rolls over at the Adhan, and that a gauge sweeps without resetting when
its text switches to seconds.

The widget's content logic is unit-tested — the Adhan → Iqama → next-prayer cycle, both seconds
thresholds, the midnight case, and the alarm instants — but three things depend on the device: that
the RemoteViews layout inflates and fits, that the `Chronometer` counts *down* rather than up, and
that the alarm actually fires close enough to the boundary to look instant.

The Arabic typography changes are likewise unverified by eye. What was measured is the mechanism:
under an `ar` locale `String.format("%d")` emits Arabic-Indic digits, which the monospace face does
not carry. The fix pins the numbering system; whether the result now looks the right *size* is a
judgement only a screenshot can settle.

This is the project's standing pattern — every defect it has had was found by someone looking at a
screen, not by a failing build. Plan accordingly.

---

## Remaining before release

### 1. Take the screenshots

[docs/SCREENSHOT_PLAN.md](docs/SCREENSHOT_PLAN.md) is written as a device-test pass, not just an
asset list. Eleven shots, each one a screen that has not been seen running; if a shot cannot be
taken, that is the bug report. It covers Arabic, the light theme, the mosque picker, the correction
steppers and the complications in one sweep, and says what to look at in each.

### 2. Exercise the nearby-mosque source on a device

The riskiest untested path:

- Does the picker find anything useful near the user? Mawaqit coverage is uneven — dense in some
  cities, a single mosque in others.
- Is the 45-minute sunrise tolerance right in practice? Too strict rejects good mosques; too loose
  lets a broken one through.
- Does the fallback to calculation happen silently and correctly when a mosque is rejected?
- Do a mosque's own Iqama times appear, in both the offset and wall-clock forms?

### 3. Verify phone → watch sync between real devices

Never exercised. The codec is unit-tested — 11 tests, including truncation and version skew — but no
`DataItem` has crossed between two devices. Language mirroring rides the same path and is untested
for the same reason.

### 4. Choose a licence

`README.md` says "not yet chosen", which means all rights reserved by default. `adhan2` is MIT and
its notice must be carried either way. A release blocker.

### 5. Release signing

Release APKs are currently signed with the **debug key** so they can be sideloaded. Play rejects
that outright, and an app installed with the debug key cannot later be updated by a properly-signed
one — those installs have to be removed first.

### 6. Host the privacy policy

[docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) is the text. Play requires a reachable **URL**, so
the file alone does not satisfy it. Two placeholders in it need filling.

---

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
