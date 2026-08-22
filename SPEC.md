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

Any date within a month either way can be browsed, by stepping a day at a time or by picking from a
month grid. On a date other than today the live elements are omitted rather than frozen: a countdown
to a prayer three weeks out is noise, and dimming "passed" prayers on a future day would be false.
What remains is a timetable, which is what browsing to a date is asking for. The cache retains a
month of history so those days show the times that were actually published rather than a fresh
estimate of them.

**Settings** — prayer time source and mosque, location, appearance, language, method, school,
twilight (when relevant), Iqama source and offsets, per-prayer corrections.

### Home screen widgets

Two, answering different questions. Both read the same cache and share one implementation; they
differ only in how they draw it.

**Schedule widget.** The five prayers and sunrise with their Adhan and Iqama times, headed by the
next Adhan or Iqama counting down. It follows the same cycle as complication (e): after an Adhan the countdown turns to
that prayer's own Iqama, and only once that passes does the next prayer take over. Sunrise is listed
but never counted down to. Tapping it opens the app.

The countdown resolves the seconds rule differently from the watch, and deliberately. A widget
cannot redraw once a minute — the platform floor for periodic updates is thirty minutes, and
anything finer means waking the app on a timer, which this project does not do. So outside the
seconds window it shows the target's **clock time**, which cannot go stale because it does not
change; inside the window a `Chronometer` counts down in `mm:ss`, ticked by the system rather than
by this app. The thresholds are the same fifteen and ten minutes used everywhere else.

Transitions are driven by a single inexact alarm armed for the one instant the current rendering
stops being true. Exact alarms are not requested: `SCHEDULE_EXACT_ALARM` is not granted by default
from API 33, and `USE_EXACT_ALARM` is restricted by Play policy to alarm and calendar apps.
Lateness is bounded and visible rather than corrupting: a `Chronometer` past its base counts back
*up* from zero, so an alarm that fires late reads as a countdown that has bounced off zero, not as a
wrong time. The next redraw corrects it.

It targets three cells square and resizes down to about two. It does not clip as it shrinks —
chrome goes first (the date and location line, then the estimate notice, then the divider), then the
Iqama column on narrow widgets, then type and padding come down. **All six entries survive every
size**, because a timetable missing its last prayer is worse than a small one.

Sizing comes from the launcher's reported options rather than from separate layouts per breakpoint,
so there is one layout to keep correct. Note the options bundle is empty on first placement; that is
treated as "full size", because guessing small on a large widget looks like a defect while guessing
large on a small one is corrected within a frame.

**Strip widget.** One row, four cells by one. Five prayers across, each with an icon, its name and
its Adhan time. The prayer being counted to is boxed, and its countdown appears beneath it once
inside the seconds window — driven by the same phase timeline, with no threshold of its own.

Sunrise is not among the five. Five columns leave each one wide enough for a time and a countdown;
six do not.

Both widgets follow the system light/dark setting rather than the in-app theme preference. They are
drawn by the launcher, which has no access to the app's own setting.

---

## Languages and appearance

English and Arabic throughout — every screen, the widget, and every complication, with layout
direction following the language. The phone offers light, dark, or follow-system; the watch is
always dark, because on an AMOLED panel a black pixel is an unlit pixel.

**Numerals are always European**, in both languages. Arabic's default CLDR numbering system is
`arab`, so an `ar` locale can render times and countdowns as ٥:٤٥. The monospace face this app sets
its times in carries no Arabic-Indic glyphs, so such digits fall back to the system Arabic face —
optically larger at the same size, and proportional rather than tabular, which breaks the one
typographic rule the design actually has: that a column of times lines up.

Scope of what was measured, since it decides how much to trust this: `String.format("%d")` does emit
Arabic-Indic digits under `ar`, so every countdown was affected. `DateTimeFormatter` did not, on the
JDK — but Android's CLDR data differs and was not checked on a device, so the formatters are pinned
too and the result no longer depends on which platform resolves it. Month names, weekday names and
the AM/PM markers are unaffected either way: those come from the language, not the numbering system.

Arabic also drops the scale's letter-spacing to zero. Arabic is a joined script, and tracking pulls
the glyphs apart at the seams so a word stops reading as one word.

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
