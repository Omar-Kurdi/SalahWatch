# Review instructions

Two audiences, deliberately kept apart. The first section is written to be pasted into the Play
Console's **App access / Instructions for review** box. The rest is for us — how to check the claims
in these documents against the artefact rather than against intent.

---

## For the Play reviewer — paste this

```
No account or login is needed. Everything in the app is available immediately.

SETTING A LOCATION

The app needs a location before it can show prayer times. There are three ways, and the
first needs no permission:

  1. Settings tab -> Location -> "Set it myself" -> type a city -> Find
     (or enter latitude and longitude by hand)
  2. Settings tab -> Location -> "Use my location" -> grant approximate location

Any city name or coordinate pair works. A large city will also have mosques listed for the
test below.

The location permission is optional and approximate only. Background location is never
requested and is not declared in the app.

TESTING THE WATCH COMPLICATIONS

The watch app is a companion to the phone app and shares its application ID. It supplies
five complication data sources; it does not draw a watch face.

  1. Install both APKs; open the phone app and set a location as above
  2. On the watch, long-press the watch face -> Customise -> pick a complication slot
  3. Scroll to "SalahWatch" -- five providers are listed
  4. Pick any; it renders immediately. Tapping it opens the watch app

The watch keeps a local copy of the timetable, so it continues to work with the phone
switched off or out of range. The watch app has no internet permission.

PRAYER TIMES FROM A MOSQUE

Settings -> "Prayer times from" -> "Nearby mosque" lists mosques near the chosen location
that publish their timetable on Mawaqit (mawaqit.net), a public directory. Coverage
varies by region. If none are found, or a mosque's published data fails validation, the
app falls back to calculation automatically.

There is no user-generated content, no communication feature, no advertising and no
purchase of any kind.
```

---

## For us — verifying the claims before submitting

The rule this project runs on: **verify the built artefact, not the build result.** A manifest that
silently dropped most of its complication services, and a missing `RECEIVE_BOOT_COMPLETED`, were both
found by inspecting an APK. Neither made the build fail.

```bash
JAVA_HOME=~/.local/share/prayer-toolchain/jdk-21.0.12+8 ./gradlew build detekt
```

```bash
JAVA_HOME=~/.local/share/prayer-toolchain/jdk-21.0.12+8 ./gradlew assembleRelease
```

Then, with `aapt2` from `~/Android/Sdk/build-tools/36.1.0/`:

```bash
aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -3
```

Expect `package: name='com.salahwatch'` and `application-label:'SalahWatch'`.

```bash
aapt2 dump xmltree --file AndroidManifest.xml wear/build/outputs/apk/release/wear-release.apk | grep -c 'E: service'
```

Expect **7** — five complication providers, the data-layer listener, and Room's invalidation
service. Any smaller number means services were lost on the way into the APK.

```bash
aapt2 dump xmltree --file AndroidManifest.xml wear/build/outputs/apk/release/wear-release.apk | grep -A2 'uses-permission' | grep 'name'
```

Expect exactly `RECEIVE_BOOT_COMPLETED` and the WorkManager-generated
`com.salahwatch.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **An `INTERNET` permission appearing here
is a regression** — it means a networking dependency has reached the watch through
`:data:prayer-times`, and both [PRIVACY_POLICY.md](PRIVACY_POLICY.md) and
[DATA_SAFETY.md](DATA_SAFETY.md) would become untrue.

For the phone, expect exactly: `INTERNET`, `ACCESS_COARSE_LOCATION`, `ACCESS_NETWORK_STATE`,
`FOREGROUND_SERVICE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`. **`ACCESS_FINE_LOCATION` or
`ACCESS_BACKGROUND_LOCATION` appearing would contradict the Data safety declaration** and, for the
latter, drag the submission into Play's background-location review process.

### Signature

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

If the subject reads `CN=Android Debug` the build is **not submittable**. That is the current state;
see blocker 1 in [PLAY_STORE.md](PLAY_STORE.md).
