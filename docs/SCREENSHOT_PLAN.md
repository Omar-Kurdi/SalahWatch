# Screenshot plan

Two separate sets. Play treats the phone and Wear OS form factors as different listings, and the
Wear one will not accept phone screenshots — a round 384 × 384 frame is a hard requirement there.

This doubles as the device-testing checklist. Several of these screens have since been captured and
are in [docs/images](images) and the README; what follows is still the list to work from for the
store assets, which need clean, deliberately composed shots rather than whatever the device happened
to be showing. Every shot below is a screen that has not yet been
seen running, so capturing the set *is* the verification pass: if a shot cannot be taken, that is
the bug report.

---

## Setup, once

Pick a location with good Mawaqit coverage — a large city — so the mosque picker has something to
show. Somewhere at a moderate latitude keeps the prayer times spread pleasantly across the day; far
north or south in summer compresses them.

Capture at a time of day where the next prayer is **twenty minutes to two hours away**. A countdown
reading `in 1h 24m` shows what the app is for; one reading `in 9h 12m` shows a number.

Set the watch face to something plain and dark before shooting complications. A busy face makes the
complication unreadable at thumbnail size, which is the size most people will judge it at.

---

## Phone — 9 shots

Portrait, 1080 × 2400 or the device's own resolution. Play accepts 2–8; six is enough to tell the
story without repetition.

| # | Screen | What it has to show | Watch for |
|---|---|---|---|
| 1 | **Today**, dark | Live clock, both dates, day arc, next prayer with countdown, schedule below | The hero shot. The day arc must be visible without scrolling — crop or scroll so it is |
| 2 | **Today**, light | The same screen in the light theme | The whole point of this shot is that the cards are *pale*. Dark boxes on cream means the palette sweep missed a surface |
| 3 | **Today**, Arabic | The same screen, right to left | Layout mirrored: the schedule's time column on the left, the navigation labels in Arabic. Hijri month names should be in Arabic script, not transliterated |
| 4 | **Settings** — prayer times from | The source radio pair with "Nearby mosque" selected and the mosque list beneath | Each mosque must appear **once**. The chosen one is brass in the list and is not also repeated above it |
| 5 | **Settings** — method and school | The method dropdown open, angles visible beside each name | The angles are what distinguishes the methods; a closed dropdown shows nothing useful |
| 6 | **Settings** — Iqama and corrections | The Iqama source pair, the five offsets, the six correction steppers | Set one correction to a non-zero value so the brass `+2` reads against the muted zeros |
| 7 | **Schedule widget** | The widget on a real home screen, over a wallpaper, with the next prayer counting down | Take it inside the seconds window so the `Chronometer` is visibly ticking in `mm:ss`. Check it is counting **down** — a Chronometer that counts up means `setChronometerCountDown` did not take |
| 8 | **Strip widget** | The one-row widget on a home screen, with the upcoming prayer boxed | Five columns, evenly spaced. The icons must be distinguishable from each other at real size — Dhuhr and Asr are separated only by where the sun sits |
| 9 | **Today**, a future date | The month grid open, or a date a week ahead selected | On another date the clock, the day arc and the next-prayer panel are all absent by design. If a countdown is visible on a future date, that is a bug, not a shot |

### While taking these, check the things only a screen can show

**Arabic numerals.** Shot 3 is the one that settles a change made without a device: every time and
countdown must be in European digits (5:45), not Arabic-Indic (٥:٤٥), and the schedule's time column
must be a straight vertical line. A ragged column means the monospace face is not being used and the
digit fix did not take. Prayer names should read as joined words, not as spaced-out letters.

- **Shot 2** — every surface must be pale: section cards, the next-prayer panel, the stepper
  buttons, the navigation bar. The status bar icons must be dark against the light background, not
  white and invisible.
- **Shot 3** — confirm the app switches language when the setting is chosen, and *stays* switched
  after force-quitting and reopening.
- **Shot 4** — tap a mosque, then a different one, and confirm the Today tab's times change to
  match. Each mosque must appear exactly once.
- **Shot 6** — tap `+` on a correction five times slowly: it must read `+1`, `+2`, `+3`, `+4`, `+5`,
  with the Today tab following immediately. Then step back down and confirm it reverses.

---

## Wear OS — 5 shots

**384 × 384, round.** Take them on the watch itself (`adb shell screencap`) rather than in an
emulator if possible; the emulator's default face is not representative.

| # | Shot | What it has to show |
|---|---|---|
| 1 | **Watch face with complications** | The hero. Three or four slots filled from this app at once — Prayer Time, Adhan Countdown, and Prayer Progress as a gauge — which is the thing five providers exist for |
| 2 | **Complication picker** | The provider list scrolled to SalahWatch, showing all five distinctly-named entries |
| 3 | **Watch app — next prayer** | The countdown and the prayer ring around the bezel |
| 4 | **Watch app — schedule** | Scrolled down to the day's list |
| 5 | **Progress ring, mid-prayer** | The RANGED_VALUE ring part-filled. Take it when the ring is genuinely partway, not at 2% |

### While taking these, check

- **Rollover.** Sit with a complication through an Adhan and confirm it moves to the next prayer on
  its own. A watch is the only place this can be confirmed.
- **The countdown windows.** An Adhan countdown must switch from `22m` to `14:59` and tick by the
  second fifteen minutes out; an Iqama countdown does the same ten minutes out. Adhan
  Time/Countdown must leave its clock time thirty minutes before the Adhan. On Prayer Progress,
  confirm the whole cycle: time → countdown → `Iqama` countdown → `Pray 3:12` counting up → the next
  prayer.
- **The gauge does not reset** when a countdown switches to seconds. The arc should sweep once
  across the whole wait.
- Arabic prayer names, and the `Iqama` and `Pray` titles, fit the seven-character budget on a real
  face. They pass by unit test; a watch face may still lay them out differently.
- The watch keeps working with the phone switched off — turn Bluetooth off on the phone and confirm
  the complications still show today's times.
- The app icon on the watch launcher: the crescent should sit **centred** in its ring.

---

## Store graphics

| Asset | Size | Notes |
|---|---|---|
| App icon | 512 × 512 PNG, no alpha | Render `ic_launcher_foreground` over `ic_launcher_background`. Do not redraw the crescent by hand — its path is generated so that the *bounding box* is centred, which is not the same as centring the circle it was cut from |
| Feature graphic | 1024 × 500 | A watch face with complications on the left, the phone's Today screen on the right, on the night background `#05070F`. Brass `#C9A227` for any text. Must survive being shown at thumbnail size |

Both are needed before submission; see [PLAY_STORE.md](PLAY_STORE.md).
