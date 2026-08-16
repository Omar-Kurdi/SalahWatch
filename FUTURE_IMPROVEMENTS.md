# Future Improvements

Work deliberately deferred out of the first release. Each item records why it was deferred and what
already exists to make it a small change rather than a rewrite.

---

## 1. Notifications

Two reminders, each independently switchable and **off by default**:

- *"Asr Adhan in 10 minutes"*
- *"Asr Iqama in 10 minutes"*

The lead time should be configurable per reminder, and each prayer individually switchable — someone
already at the mosque for Maghrib does not want a phone buzzing about it.

### Why this cannot reuse the complication mechanism

Worth stating plainly, because it is what makes this real work rather than a small feature.
Complications are pull- and timeline-based: the app publishes every transition in advance and is
then never woken. A notification is the opposite — it needs the device to *act* at a specific
instant. Nothing in the existing timeline can be reused.

### The mechanism, and its cost

`AlarmManager` with an exact alarm. On Android 13+ that means one of two permissions:

- `USE_EXACT_ALARM` is granted at install, but Play restricts it to apps whose *core* function is
  alarms or calendars. This app's core function is complications, so it would likely be rejected —
  and claiming otherwise on the declaration form would be a false statement to Play.
- `SCHEDULE_EXACT_ALARM` is the honest route. It is user-grantable, revocable at any time, and on
  Android 14+ denied by default for most apps. The feature must degrade gracefully: if permission is
  absent, the toggle explains why and offers to open settings, falling back to an inexact alarm
  rather than silently doing nothing.

Budget roughly one alarm per enabled reminder per day, rescheduled on the daily sync, on
`BOOT_COMPLETED`, and on `TIME_SET`/`TIMEZONE_CHANGED` — the receiver for those already exists.

### Phone first

A notification posted on the phone already bridges to a paired Wear OS watch, so a watch-side
implementation would produce two buzzes for one prayer unless explicitly suppressed. The phone side
alone is both less work and the better default.

### Before it ships

- `POST_NOTIFICATIONS` runtime permission (Android 13+), requested when the first reminder is
  switched on, never at launch.
- A notification channel per reminder kind, so Adhan and Iqama can be silenced independently from
  system settings.
- The battery and reliability cost described honestly in the settings screen rather than presented
  as free. That is also why it is off by default.
- `docs/PLAY_STORE.md` gains a permissions row; `SCHEDULE_EXACT_ALARM` may require a declaration.
  `docs/DATA_SAFETY.md` is unaffected — nothing new leaves the device.

---

## 2. Explicit user-entered Iqama times

For a mosque that publishes no offsets and none nearby publishes at all.

**Groundwork in place:** `IqamaMode.EXPLICIT_TIMES` exists and `IqamaCalculator` already branches on
it. It currently returns no Iqama rather than inventing one, on the principle that showing a wrong
congregation time is worse than showing none.

**Remaining:** per-prayer wall-clock storage, and a rule for what an explicit time means across a
DST boundary. A fixed "13:30" is a *local* time, unlike everything else in the app, which is stored
as an absolute instant. That asymmetry is the real design work here, not the UI.

Note that the mosque source already delivers much of what this was originally for: where a mosque
publishes its congregation times, the app reads them directly, in either the offset or the
wall-clock form.

---

## 3. Mosque profiles

**Groundwork in place:** `IqamaMode.MOSQUE_PROFILE` exists as a mode. Because Iqama derivation is
isolated behind `IqamaCalculator` and everything downstream consumes only the resulting
`PrayerTime.iqama`, a mosque profile is a new branch in one function — no changes to the
complication, sync or UI layers.

**Out of scope:** running an online mosque database of our own. The app reads a public directory; it
does not host one.

---

## 4. Watch-side standalone operation

The watch relies on the phone for the authoritative fetch and syncs over the Data Layer, with the
offline calculator as a fallback. A fully standalone mode — the watch fetching for itself over
Wi-Fi/LTE when unpaired — is possible but was not required, and every network call on the watch is a
battery cost the current design avoids entirely.

It would also mean adding `INTERNET` to the watch APK, which several privacy claims currently rest
on not having.

---

## 5. Reducing the offline estimate's error

The offline fallback agrees with the API to within 3 minutes, measured across 80 samples — 5
locations × 2 methods × 2 schools × 4 dates, in `AlAdhanGoldenTest`. Dhuhr and Maghrib match exactly;
Fajr, Sunrise and Isha are within a minute; Asr is the outlier at up to 3.

The Asr spread is symmetric about zero rather than biased, so it is implementation noise between two
solar-geometry implementations, not a constant that could be corrected away. Closing it would mean
reimplementing the Asr shadow calculation to match exactly. Given the fallback is used only when
nothing is cached, and its times are marked as estimates, this is low value — but it is the reason
the tolerance is 3 minutes and not 1.

---

## 6. Golden-fixture coverage for the remaining methods

All 23 methods are selectable, but only two are covered by recorded API fixtures. Before leaning
harder on the full list, extend `aladhan-golden.json` to cover the others — particularly the
interval-based Isha methods (4, 8, 10, 22) and the Moonsighting Committee method (15), whose
seasonal adjustment is the one behaviour beyond a pair of angles.

---

## 7. The default calculation method

The app ships with a single global default. There is no good universal answer: the correct method is
whichever the user's local authority uses, and that varies by country.

Worth considering: choosing a sensible default from the user's coordinates on first run — the API
exposes enough regional information to make a reasonable guess — while leaving the setting fully
manual. This is a first-run experience improvement rather than a correctness one, since any user can
already pick the right method themselves.
