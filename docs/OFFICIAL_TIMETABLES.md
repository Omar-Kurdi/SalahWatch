# Refreshing the bundled official timetables

The app ships each supported area's official prayer table as an asset in
`data/prayer-sync/src/main/assets/official/`. This is a **manual, roughly annual job**: the authority
publishes about eighteen months ahead, so a refresh once a year keeps the app comfortably inside
published data.

## Why the data is bundled rather than fetched

The authority's own site loads its times from `mobileappapi.awqaf.gov.ae`, which **requires an
authorization token** — an unauthenticated request returns `401`. Its open-data API is gated too:
access is granted only to private-sector entities registered in the UAE. Neither is a dependency this
app can take.

Bundling sidesteps all of it, and buys three things worth having anyway: the exact times work offline
on a fresh install, there is no runtime dependency on a government endpoint staying up or keeping its
shape, and the app sends nothing anywhere to display them.

The cost is that coverage ends where the bundle does. `PrayerSyncer` handles that by topping up with
calculation past the last published day, so the app degrades to "approximate and marked as such"
rather than to "wrong" or "empty".

## What each file holds

```json
{"area":"Al Ain","slug":"al-ain","zone":"Asia/Dubai","source":"…","retrieved":"2026-08-31",
 "order":["fajr","sunrise","dhuhr","asr","maghrib","isha"],
 "months":{"2026-1":"053806551223152217451902,…"}}
```

One line per month; one comma-separated record per day; each record is six `HHMM` fields in `order`.
Fixed-width and flat so that a refresh shows up in review as the lines that actually changed, rather
than as a rewritten document.

`slug` must match the `OfficialArea` entry that names the file.

## How to refresh

1. Open <https://www.awqaf.gov.ae/prayer-times>, choose the city and area, and press **Show More**
   to reveal the month-by-month listing.
2. For each month you need, read the rendered table. The page renders client-side, so read the DOM
   rather than the HTML source — every row carries its own date, which is what makes it safe to
   confirm you are looking at the month you asked for before recording it.
3. Convert to the format above. Times display as 12-hour with a meridiem; capture the meridiem
   rather than assuming it, and store 24-hour.
4. Write the file, then run the tests. `OfficialTimetableTest` parses the **real shipped assets** and
   checks day counts, ordering, and that there are no gaps between the first and last day.
5. Extend the golden assertions in `bundled days match the authority's published table` if you have
   added a year, and leave the existing ones alone — they exist to catch a refresh silently changing
   a day that was already shipped.

## Adding an area

Add an `OfficialArea` entry with its slug and centre coordinates, drop the matching JSON next to the
others, and add its display name to `values/strings.xml` and `values-ar/strings.xml`. The settings
picker and the automatic nearest-area choice are both driven off the enum, so nothing else needs
touching.

Coordinates are used **only** to preselect a table for a user who has not chosen one, within
`OfficialArea.MAX_AUTO_DISTANCE_KM`. They are never used to compute a time. Keep the radius tight
enough that a user in a neighbouring emirate is not silently handed the wrong city's table — that is
the failure mode hardest to notice, because every value still looks plausible.

## Current coverage

| Area | From | To | Captured |
|---|---|---|---|
| Al Ain | 2026-01-01 | 2027-06-05 | 2026-08-31 |
| Dubai | 2026-01-01 | 2027-06-05 | 2026-08-31 |

Both were verified against the published table at capture: Al Ain's August 2026 was compared day by
day against an independently taken copy of the same month, matching 31/31 on all six prayers.

One quirk worth knowing: Dubai's published table is **perpetual** — its 2027 values are identical to
2026, day for day. Al Ain's are not. Do not assume either way when refreshing; capture what the site
actually returns for the year you asked for.
