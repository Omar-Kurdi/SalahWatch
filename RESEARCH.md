# Technical rationale

Why the technical decisions are what they are, and what was verified rather than assumed.
[STATUS.md](STATUS.md) records where the project currently stands; [SPEC.md](SPEC.md) states what the
app does.

Everything here was checked against primary sources: official Google, Android and Samsung
documentation, the **published AAR artifacts** — downloaded and inspected, not read from a summary —
Google Maven and Maven Central metadata, and live HTTP calls. Where something could not be verified,
it says so.

---

## 1. Platform

### 1.1 A Galaxy Watch is a standard Wear OS target

There is no Samsung-specific complication API and no Samsung-specific registration. Samsung's own
current guidance tells developers to extend `SuspendingComplicationDataSourceService` from the
AndroidX `androidx.wear.watchface.complications` libraries, register with the standard
`BIND_COMPLICATION_PROVIDER` permission and `ACTION_COMPLICATION_UPDATE_REQUEST` intent filter, and
respect the 300-second minimum update period. The older Tizen documentation does not apply.

| Wear OS | Android | API | Samsung devices |
|---|---|---|---|
| 3 | 11 | 30 | Galaxy Watch 4 / 5 (original firmware) |
| 4 | 13 | 33 | Watch 4/5/6 updates, Watch 6 launch |
| 5 | 14 | 34 | Watch 7 / Ultra |
| 6 | 16 | 36 | Watch 8 launch; One UI 8 Watch rolling back to Watch 4 |

### 1.2 Why minSdk is exactly 33 on the watch

The complication **dynamic value** APIs — `RangedValueComplicationData.Builder(dynamicValue:
DynamicFloat, …)` and `DynamicComplicationText` — are gated `@RequiresApi(TIRAMISU)`, API 33.

- **Below 33**, a `RANGED_VALUE` arc carries a static `Float`, so it can only advance when the
  provider is polled, and the floor is 300 seconds. A progress ring would sit still, then jump.
- **At 33**, the arc is an expression the platform evaluates on-device at ~1 Hz. It sweeps smoothly
  and the app is never woken to move it.

That is what lets every provider declare `UPDATE_PERIOD_SECONDS=0`. Going higher buys nothing: Wear
OS 5 and 6 add no complication APIs, so raising the floor would drop devices for no functional gain.
The complication AARs themselves declare `minSdkVersion 26`, so they are not the constraint.

Phone `minSdk` is 29; `compileSdk` and `targetSdk` are 36 on both.

### 1.3 What is actually in the shipped artifacts

Confirmed present in `androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0` by
unpacking the AARs and inspecting class names and the constant pool:

- `SuspendingComplicationDataSourceService`, `SuspendingTimelineComplicationDataSourceService`
- `ComplicationDataTimeline`, `TimelineEntry`, `TimeInterval`, `ComplicationRequest`,
  `ComplicationDataSourceUpdateRequester`
- `TimeDifferenceComplicationText`, `TimeFormatComplicationText`, `DynamicComplicationText`
- `ShortTextComplicationData`, `LongTextComplicationData`, `RangedValueComplicationData`,
  `MonochromaticImageComplicationData`, `GoalProgressComplicationData`
- Manifest metadata constants: `SUPPORTED_TYPES`, `UPDATE_PERIOD_SECONDS`, `PROVIDER_CONFIG_ACTION`,
  `SAFE_WATCH_FACES`, `CONFIG_RESTORE_SUPPORTED`, `DEFAULT_CONFIG_SUPPORTED`

Two corrections worth recording:

- `setExtras(...)` / `SET_COMPLICATION_EXTRAS` exists in `androidx-main` but **is not** in the
  published 1.3.0 artifacts. Not used.
- The AndroidX watch **face** APIs were deprecated in 1.3.0 in favour of Watch Face Format. The
  complication **data source** APIs are explicitly not deprecated. This app builds only data sources.

---

## 2. The update model, which is the whole battery story

`UPDATE_PERIOD_SECONDS` has a hard **300-second floor**. Polling can never drive a second-accurate
countdown. Three platform mechanisms make that irrelevant:

**`TimeDifferenceComplicationText`** renders a live countdown with zero wakeups. The data source
supplies a reference `Instant` and a style; the watch face renders the difference itself.

- `Builder(style, countDownTimeReference)` / `Builder(style, countUpTimeReference)`
- `.setText("^1")` substitutes the difference into surrounding text, `.setMinimumTimeUnit(TimeUnit)`,
  `.setDisplayAsNow(Boolean)`
- Styles: `STOPWATCH`, `SHORT_SINGLE_UNIT`, `SHORT_DUAL_UNIT`, `WORDS_SINGLE_UNIT`,
  `SHORT_WORDS_SINGLE_UNIT`

**`SuspendingTimelineComplicationDataSourceService`** covers the whole day in one response. Each
`TimelineEntry(TimeInterval(start, end), data)` covers one phase; the platform selects the entry
whose validity contains "now", the shortest wins where entries overlap, and gaps fall back to the
default. **Every prayer transition for the next ~48 hours goes out in a single request.**

**Dynamic values** re-evaluate on the platform at ~1 Hz without waking the app.

Combining the first two is the key finding: a timeline entry can itself contain a
`TimeDifferenceComplicationText`, so the full cycle — time → countdown to Adhan → countdown to Iqama
→ count-up since Iqama → back to time — runs with literally zero background work.

**A provider that is not a timeline never advances.** With `UPDATE_PERIOD_SECONDS=0` and a single
static answer, the platform never asks again, and the complication freezes on whichever prayer was
next when the user added it. Timeline entries are what make rollover correct, not an optimisation on
top of it.

Push updates use `ComplicationDataSourceUpdateRequester.requestUpdateAll()`. Google's guidance is not
to call it more than once per five minutes on average; this app calls it roughly once a day plus on
settings changes.

**`onComplicationRequest` is annotated `@UiThread`** — all database and IO work is wrapped in
`withContext(Dispatchers.IO)`.

---

## 3. Type constraints that shaped the UI

- **SHORT_TEXT** — `MAX_TEXT_LENGTH = 7`, applied to `text` **and** `title` independently. This is
  why `ASR 4:32` (8 characters) is never emitted as one string: it goes out as `title="ASR"`,
  `text="4:32"`, which is also the stacked layout a face renders naturally. All prayer names fit —
  `MAGHRIB` is exactly 7, and the longest Arabic names are 6.
- **RANGED_VALUE** — `Builder(value, min, max, contentDescription)` plus `setText`, `setTitle`,
  `setValueType`, `setColorRamp`; and the `@RequiresApi(TIRAMISU)` dynamic-value overload.
- **LONG_TEXT** — no hard limit, but nothing in this app is a sentence, so it is not offered.
- All types support `setTapAction(PendingIntent)` and `setValidTimeRange(TimeRange)`.

---

## 4. Phone ↔ watch, and location

**Data Layer** (`play-services-wearable`): `DataClient` + `PutDataMapRequest` +
`WearableListenerService`. The **DataItem payload limit is 100 KB**. Non-urgent items may be delayed
up to 30 minutes; `setUrgent()` forces prompt sync. Data is buffered while disconnected and delivered
on reconnect. Both APKs must share an application id and signing key. Does not work when the watch is
paired to an iPhone.

**Location on Wear**: permission grants are **not** inherited from the phone app. This app avoids the
problem entirely — the phone owns location and sends a resolved position, so the watch requests
nothing. A single coarse `getCurrentLocation()` on the phone, only when the user picks "Automatic",
is sufficient, and no background location permission is needed.

**Geocoding uses the platform's own `android.location.Geocoder`.** Verified against the AOSP source:
not one method carries a `@RequiresPermission` annotation, so forward and reverse geocoding need no
location permission at all. It also needs no key and no account, and the query goes through the OS
backend rather than a service of our choosing — better for privacy than any third-party geocoder.
Guard every call with `Geocoder.isPresent()`, which returns false on de-Googled devices; the fallback
is manual latitude/longitude entry, which always works.

**Background and daily rollover**: verified against the implicit-broadcast exemption list.
`ACTION_BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `LOCALE_CHANGED` **are** exempt and can
be received by manifest-declared receivers. **`ACTION_DATE_CHANGED` is not exempt** and must not be
relied on. Rollover therefore uses a WorkManager job plus those receivers — and, critically, a
timeline that already covers ~48 hours, so a missed job degrades nothing.

---

## 5. Where prayer times come from

### 5.1 Calculation, and its structural ceiling

The AlAdhan API, requested by latitude/longitude. `iso8601=true` is not optional: without it,
timings come back as bare wall-clock strings and the DST boundary becomes ambiguous. With it, every
timestamp carries its own UTC offset. Verified across a DST changeover — the offset flips correctly
from `+01:00` to `+00:00` on the right night — and `meta.timezone` returns the IANA zone.

Endpoints (no API key):

- `GET /v1/calendar/{year}?latitude=&longitude=&method=&school=&annual=true&iso8601=true` — the
  **whole year** in one call. Measured: 493 KB, 0.96 s.
- `GET /v1/timings/{DD-MM-YYYY}` — single-day fallback.
- `GET /v1/methods` — the method list.

City-name endpoints are deliberately unused. They depend on a third-party geocoding key service that
has since shut down, and they return **masked coordinates** (`8.8888888`, `7.7777777`) without a key,
so they cannot supply coordinates to cache anyway. Coordinates are the canonical representation
throughout.

**The ceiling: sunrise is method-independent.** It depends only on latitude, longitude and date —
never on which authority or set of angles is chosen. Measured against an official national timetable
over a full month, calculated sunrise was 4–5 minutes out on *every* day, identically across methods
3, 4, 8, 9, 10 and 16. No selectable method moves it, so no calculation source can reproduce a
national table exactly. The five prayers land within ±1 minute.

### 5.2 A nearby mosque's published timetable

Mawaqit is the platform mosques use to publish their own timetables.
`GET /api/2.0/mosque/search?lat=&lon=` returns proximity-sorted mosques with today's times and Iqama
information; a mosque's full year is embedded in its public page. Coverage is global but uneven —
verified returning results across six cities on four continents, from around a dozen mosques in some
to one in others.

Nearby mosques do **not** all publish the same table, and none of the three near Al Ain reproduces
the official one exactly. Measured against Awqaf's own published Al Ain table for August 2026, the
nearest mosque (Sheikh Tahnoun, 2.6 km) deviates by at most a minute but not on every prayer:

| Prayer | Exact | Days differing | Direction |
|---|---|---|---|
| Dhuhr | 30/31 | 1 | −1 |
| Asr | 30/31 | 1 | −1 |
| Shurooq | 28/31 | 3 | +1 |
| Fajr | 27/31 | 4 | +1 |
| Isha | 24/31 | 7 | −1 |
| Maghrib | 23/31 | 8 | −1 |

The other two mosques are on a different table again — identical to each other on all 366 days, and
**5 minutes out on sunrise, 4 on Asr**. Agreement between mosques therefore indicates a shared
upstream, *not* an official one, so selecting by consensus would pick the wrong table here.

The practical consequence: a mosque source gets within a minute, which is far closer than
calculation reaches, but it is a mosque's own timetable and not the authority's. Only the
authority's published table reproduces the authority's times.

### 5.3 Why mosque data is validated rather than trusted

Mosque timetables are entered by people, and two hazards appeared in live data:

- **Calendars carry six *or seven* times a day.** Reading from the front would shift every prayer by
  one slot — wrong, plausible, and silent. Verified across three countries that the **last six** are
  always `[fajr, sunrise, dhuhr, asr, maghrib, isha]`.
- **A mosque can declare a timezone it is not in**, which shifts every prayer by an hour.

So every calendar is checked against an independently computed sunrise — the one value verifiable
without reference to any authority — and rejected beyond a **45-minute tolerance**, falling back to
calculation. The tolerance is wide because real mosques legitimately differ by tens of minutes on
horizon convention, and far narrower than the hours a timezone error produces.

A third hazard is in the Iqama field: it carries **either** offsets (`"+20"`) **or** wall-clock times
(`"12:46"`), in the same key, with nothing to say which. Neighbouring mosques in one city return each
form. Both are parsed; a wall-clock time landing more than two hours after its own Adhan is rejected
as mistyped.

---

## 6. The offline fallback, calibrated

When nothing is cached — a fresh install offline, or travel with no connection — `adhan2` computes
times on-device and every surface marks them **estimated**.

The disagreement between the two implementations is measured, not assumed: 80 golden samples captured
from the live API across 5 locations spanning the latitude range, both methods used as references,
both schools, and 4 dates covering solstices, equinoxes and both DST changeovers.

| Prayer | Exact | Range | Worst |
|---|---|---|---|
| Dhuhr | 80/80 | 0 | 0 min |
| Maghrib | 80/80 | 0 | 0 min |
| Fajr | 78/80 | 0..+1 | 1 min |
| Sunrise | 76/80 | −1..0 | 1 min |
| Isha | 70/80 | −1..+1 | 1 min |
| Asr | 38/80 | −3..+2 | 3 min |

Two findings produced those numbers:

- **adhan2's `CalculationMethod` presets must not be used for the angles.** Its presets apply their
  own per-prayer adjustments that differ from what the API reports for the same method.
  `AlAdhanMethods` transcribes the API's own table instead, which is what makes Dhuhr and Maghrib
  exact on every sample.
- **adhan2 must be told `HighLatitudeRule.TWILIGHT_ANGLE`** to match the `ANGLE_BASED`
  latitude-adjustment the API reports. Left at adhan2's default, a high-latitude city in late June
  was **71 minutes out on Fajr**. This single setting is what makes the offline fallback usable at
  high latitudes at all — and it would not have been caught without golden data from a high-latitude
  city.

Asr is the residual outlier, and the error is **school-dependent**:

| School | Exact | Range | Mean | Mean abs error |
|---|---|---|---|---|
| Shafi | 12/40 | −3..+2 | −0.15 | 0.95 min |
| Hanafi | 26/40 | −1..+1 | +0.05 | 0.35 min |

The whole ±3 worst case sits in Shafi. That is consistent with the geometry: Shafi defines Asr at a
shadow ratio of 1×, which falls earlier in the afternoon while the sun is still high and its altitude
changes slowly, so a tiny discrepancy in computed altitude converts into a larger difference in
*minutes*. Hanafi's 2× ratio puts Asr later, when the sun descends faster.

Both schools are unbiased about zero, so there is no systematic offset to correct — this is
implementation noise between two solar-geometry implementations, not a wrong constant. **Three
minutes is therefore the honest bound on an estimated time**, enforced by a test rather than asserted
in prose.

---

## 7. Libraries

| Artifact | Version | Purpose |
|---|---|---|
| `androidx.wear.watchface:watchface-complications-data-source-ktx` | 1.3.0 | complications |
| `androidx.wear.protolayout:protolayout-expression` | 1.4.2 | `DynamicFloat` — pinned, since the transitive 1.0.0 predates the expression schema needed |
| `androidx.wear.compose:compose-material3` / `-foundation` | 1.6.2 | watch UI |
| `androidx.compose:compose-bom` | 2026.06.01 | phone UI |
| `com.batoulapps.adhan:adhan2` | 0.0.7 | offline calculation (MIT, Meeus algorithms) |
| `androidx.room` | 2.8.4 | schedule cache |
| `androidx.datastore:datastore-preferences` | 1.2.1 | settings |
| `androidx.work:work-runtime-ktx` | 2.11.2 | daily refresh |
| `play-services-wearable` | 20.0.1 | Data Layer |
| `play-services-location` | 21.4.0 | fused location |
| `okhttp` | 5.4.0 | API clients |
| `kotlinx-serialization-json` | 1.11.0 | JSON |

Toolchain: Kotlin 2.4.10, KSP 2.3.11, AGP 8.13.2, **JDK 21**, detekt 1.23.8.

Three version constraints that are not free choices:

- **JDK 21** — `adhan2` 0.0.7 ships Java 21 bytecode. JDK 17 fails at runtime.
- **KSP 2.3.11** — KSP versions its own line. 2.3.10 added Kotlin 2.4 support; there is no 2.4.x KSP.
- **Compose BOM pinned to 2026.06.01** — Compose 1.12 and androidx.core 1.19 require compileSdk 37,
  which is preview-channel only and past AGP 8's ceiling.

**No dependency-injection framework.** The object graph is a handful of repositories with no variants
or alternative bindings, so a container would add a build step and a layer of indirection without
removing a single decision. If the graph gains conditional wiring, that trade changes.

---

## 8. Module layout

```
build-logic/               convention plugins (included build, not buildSrc)
app/                       phone app
wear/                      watch app + 5 complication services
core/model                 domain types — pure JVM
core/calculation           calendar, phase engine, adhan2 wrapper — pure JVM
core/database              Room, behind a narrow facade
core/preferences           DataStore
data/prayer-times          read path: cache + local fallback. No network, no WorkManager
data/prayer-sync           write path: API clients, syncer, WorkManager job
data/location              fused location + Geocoder
data/sync                  Data Layer codec + publisher
```

Three structural decisions, each for a concrete reason:

- **Iqama derivation has no module of its own.** It is one pure function over Adhan times plus
  settings, so it lives in `core/calculation` beside the times it derives from. A module boundary
  would have separated a function from its only input.
- **`data/prayer-times` is split from `data/prayer-sync`.** The watch depends on the read path only.
  Keeping them together merged OkHttp's `INTERNET` and WorkManager's `WAKE_LOCK` into the watch APK
  via manifest merging — a real leak, caught by inspecting the built APK. The watch now declares
  exactly one permission and contains no OkHttp or WorkManager classes at all.
- **`build-logic` is an included build, not `buildSrc`.** `buildSrc` puts AGP and KGP on the root
  build's classpath, which collides with the version catalog's `alias(...) apply false` plugin
  declarations. An included build with `compileOnly` plugin dependencies is the standard resolution.

---

## 9. Test coverage this produced

DST transitions in both directions, timezone travel, manual clock changes, a date change while
offline, an Isha Iqama crossing midnight, leap years, high latitudes, the offline-versus-API accuracy
diff, mosque calendar validation, phase-timeline contiguity, and the Data Layer payload limit.

`core/model` and `core/calculation` have no Android dependency, so all of the date and time logic is
testable on the JVM without an emulator.
