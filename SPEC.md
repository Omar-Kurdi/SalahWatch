# SalahWatch — Specification

An Android phone app and a Wear OS watch app that put Islamic prayer times — Adhan and Iqama — onto
a watch face through the standard Wear OS complication system.

The product goal, in one sentence: **glance at your watch and know what the next prayer is, when it
is, and how long you have** — without opening an app.

---

## Platform constraint

This app supplies complication **data**. The watch face decides how to draw it.

It can therefore only appear on watch faces that expose complication slots, and it cannot inject
anything into a face that does not. That is a property of Wear OS, not a limitation of this app, and
the store listing must not imply otherwise.

The corollary shapes the whole design: the app controls *what* is provided and must make each
provider useful at any size, in any slot type it declares.

---

## The five complications

Five separate data sources, each a distinct entry in the watch face picker. They are separate
providers rather than one configurable provider because a picker cannot offer a preference — a
single provider could only ever fill one slot usefully.

Each one offers **SHORT_TEXT** and **RANGED_VALUE**: text where the slot is text, an arc or gauge
where the slot draws one. Every one is a `title / value` pair, so none of them needs a wide slot.

**Sunrise is not a prayer.** It appears in the schedule and in both apps, but nothing counts down to
it, no cycle stops at it, and it is never "the next prayer".

### a. Prayer Time

The next prayer and the time its Adhan is called.

```text
Asr
3:50
```

Advances to the next prayer as soon as the current one is called.

### b. Adhan Time/Countdown

The next prayer's time, becoming a countdown thirty minutes before the Adhan, then the next prayer's
time again.

```text
Asr        →    Asr        →    Asr        →    Maghrib
3:50            22m             12:45           6:51
```

### c. Prayer Progress

The full cycle, and the most detailed of the five:

1. The next Adhan's clock time — `Dhuhr / 12:30`
2. From 59 minutes out, a countdown — `Dhuhr / 58m`
3. In the final fifteen minutes, that countdown switches to `mm:ss` — `Dhuhr / 14:59`
4. At the Adhan, a countdown to the Iqama — `Iqama / 12m`, switching to `mm:ss` in its last ten
   minutes
5. For five minutes after the Iqama, a timer counting up — `Pray / 3:12`
6. Then the next prayer, repeating

### d. Adhan Countdown

Always a countdown to the next Adhan.

```text
Asr
17m
```

### e. Adhan / Iqama Countdown

A countdown to the Adhan, then a countdown to that prayer's Iqama.

```text
Asr        →    Iqama
20m             4:32
```

### Titles track meaning, not the prayer

The word above the number says what the number *is*. While waiting for an Adhan that is the prayer's
name; once it has been called it is `Iqama`; once the congregation has begun it is `Pray`.

### Countdown precision

Countdowns show minutes at range and switch to `mm:ss` close in — fifteen minutes before an Adhan,
ten before an Iqama. An Iqama offset at or below ten minutes therefore reads as `mm:ss` for its
whole duration.

---

## Every provider is a timeline

Each provider is a `SuspendingTimelineComplicationDataSourceService`. It hands the platform **every
transition for the next two days in one response** and is not consulted again. Live values are
`TimeDifferenceComplicationText` and `DynamicFloat` expressions, which the platform renders itself.

`UPDATE_PERIOD_SECONDS` is 0 everywhere. Nothing wakes per second, per minute, or per prayer.

This is a correctness requirement, not only an efficiency one. A provider that answers "what should
I show now" once and declares no update period is *never asked again*, so it keeps displaying
whichever prayer was next when the user added it. Publishing transitions in advance is what makes
"when a prayer or its Iqama ends, move to the next" true by construction.

**A service must never wake every second to update a complication.**

---

## Complication types

| Type | Used |
|---|---|
| `SHORT_TEXT` | Yes — the primary form. `title` and `text` are capped at **7 characters each**, independently |
| `RANGED_VALUE` | Yes — the same pair with an arc. The value is a `DynamicFloat` the platform evaluates at 1 Hz, never a static float, which is why the watch app requires API 33 |
| `LONG_TEXT` | No. Nothing here is a sentence |
| `MONOCHROMATIC_IMAGE` | Icon only, for the picker |

A gauge measures the whole segment it belongs to — the entire wait for the Adhan — not the timeline
entry it happens to be drawn in, so the arc sweeps once instead of resetting when a countdown
switches to seconds.

The 7-character budget is why `ASR 4:32` is never emitted as one string: it goes out as
`title="ASR"`, `text="4:32"`, which is also the stacked layout a face renders naturally.

---

## Prayer times

### Two sources

**Calculation** works anywhere and needs only coordinates. It is unavoidably an approximation of a
local timetable: national authorities apply conventions that no set of solar angles reproduces, and
sunrise in particular is method-independent, so no method choice can move it.

**A nearby mosque's published timetable**, read from Mawaqit, is the times a congregation actually
prays by. Where a mosque follows its national timetable, those times *are* that timetable rather
than an estimate of it.

Every mosque calendar is validated against astronomy before use. Sunrise depends on nothing but
where and when you are, which makes it computable exactly and therefore usable as ground truth for
whether the rest of a mosque's data lines up. A calendar that fails validation falls back to
calculation rather than showing something confidently wrong.

Prayer times are never hardcoded.

### Configurable

- **Method** — all 23 the API offers, with the angles shown beside each name so the choice is
  legible. Users should pick the method their local authority uses.
- **School** — Shafi (default) or Hanafi. Affects Asr.
- **Shafaq** — general (default), ahmer, abyad. Only meaningful for the Moonsighting Committee
  method, so the setting is shown only when that method is selected.
- **Per-prayer corrections** — whole minutes, applied when times are read rather than when they are
  fetched, so they work against a mosque's fixed table as well as calculated times and take effect
  immediately. Bounded to ±120 minutes.

---

## Iqama times

Adhan times are astronomical. Iqama times are a human decision made by a mosque, so they can only
ever be configuration.

**Fixed offset (default).** Per prayer, so a non-uniform set is expressible:

```text
Fajr +25   Dhuhr +20   Asr +20   Maghrib +8   Isha +20
```

**From the mosque.** The offsets or wall-clock times the chosen mosque publishes. Mawaqit returns
either form in the same field, so both are read; a published time is rejected if it lands more than
two hours after its own Adhan, which is how mistyped data gets in.

Explicit user-entered wall-clock times and mosque profiles are deferred — see
[FUTURE_IMPROVEMENTS.md](FUTURE_IMPROVEMENTS.md).

---

## Location

**Automatic** — follows the device. Coarse accuracy, foreground only. Background location is never
requested and is not declared in either app.

**Manual** — a city name, or latitude and longitude entered directly. This matters for stability:
a fixed location means prayer times never shift because of a stray fix. Manual entry needs no
location permission at all.

The phone owns location and geocoding; the watch receives a resolved position.

---

## Timezone and day transitions

Times are stored as absolute instants with the location's zone kept alongside for rendering. That
makes the following fall out correctly rather than needing special cases, and each has automated
tests:

- daylight saving transitions in both directions
- travel across timezones
- manually changed device time
- an Isha Iqama that crosses midnight
- the turn of the year
- a date change while the watch is offline

The day's data updates automatically when the date changes, without the phone app being opened.

---

## Offline and caching

A whole year is fetched at a time and cached locally. The app then works offline indefinitely.

```text
Phone → fetch → local database → watch sync → complication
```

Never:

```text
Watch → internet → server → API
```

**The watch app has no `INTERNET` permission.** That is structural: the module the watch depends on
has no networking dependency to merge one in. After one sync the watch needs nothing — not the
phone, not a network.

---

## Phone ↔ watch

The phone pushes the schedule and all preferences over the Wear OS Data Layer as a single payload,
so the watch cannot run with a half-applied settings change. The watch keeps a full local copy and
continues working while the phone is off or out of range.

---

## Battery

A complication must not poll GPS, recalculate continuously, wake the CPU every minute, or keep
talking to the phone. The platform enforces a 300-second floor on polling-based updates, so the
design uses timelines and time-dependent text instead of polling — and consequently declares no
update period at all.

No alarm is tied to a prayer time. The complications already hold every transition.

---

## Privacy

- No account, no analytics, no advertising, no tracking of any kind.
- Approximate coordinates go to the chosen prayer-time service and nowhere else, anonymously — no
  key, no identifier.
- City lookup uses the platform geocoder on-device.
- Nothing is collected by the developer, because there is no server.

Full text in [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md).

---

## Applications

### Watch app

Opened by tapping any complication. Built for a glance, in the order a glance wants: what is next
and how long, the day's shape on the bezel, then the full schedule for anyone who scrolls.

No wall clock — the watch face already shows one.

### Phone app

Where configuration happens and the day is reviewed.

**Today** — live clock, Gregorian and Hijri dates, the day arc, the next prayer with a live
countdown, and the full schedule with passed prayers dimmed and the next one highlighted.

**Settings** — prayer time source and mosque, location, appearance, language, method, school,
twilight (when relevant), Iqama source and offsets, per-prayer corrections.

---

## Languages and appearance

English and Arabic throughout — every screen and every complication, with layout direction following
the language. The phone offers light, dark, or follow-system; the watch is always dark, because on
an AMOLED panel a black pixel is an unlit pixel.

The design should read as a considered Islamic utility rather than a generic Android demo: clean
typography, subtle Islamic visual language, minimal animation, high readability, large touch
targets. Avoid heavy decorative pattern. The complication itself stays extremely simple.

---

## Architecture

```text
app/                phone application
wear/               watch application + 5 complication services
core/model          domain types — pure JVM
core/calculation    calendar, phase engine, offline calculator — pure JVM
core/database       Room, behind a narrow facade
core/preferences    DataStore
data/prayer-times   read path: cache + local fallback, no network
data/prayer-sync    write path: API clients, syncer, background job
data/location       device location + geocoder
data/sync           Data Layer codec and publisher
```

`core/model` and `core/calculation` have no Android dependency, so the timezone, DST and
midnight-rollover logic is unit-testable without an emulator.

`data/prayer-times` deliberately has no networking. That is what keeps `INTERNET` out of the watch
APK.

---

## Out of scope

Deferred deliberately, with reasoning in [FUTURE_IMPROVEMENTS.md](FUTURE_IMPROVEMENTS.md):

- Notifications
- Explicit user-entered Iqama times
- Mosque profiles beyond the published-timetable source
- A watch-side standalone fetch
