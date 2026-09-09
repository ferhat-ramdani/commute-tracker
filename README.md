# Commute Tracker

An Android app that tracks how long you spend travelling between the places you visit —
Home, Work, and anywhere else — and groups those trips into routes.

Everything runs **on the device**. The app has **no internet permission at all**, so no
location data can ever be uploaded. Cloud backup of its data is disabled too.

## What it does

### Automatic background tracking (Phase A.1 — current)

- Detects real journeys on its own — you don't press start.
- **Battery-friendly by design:** fully asleep while you're not moving. The OS
  activity-recognition sensor (hardware, near-zero power) and geofences around your
  known places wake it when you start or stop moving. GPS runs only *during* a trip
  (at high accuracy — that's the part where precision matters), with the ongoing
  notification. Between trips, one fix every ~30 min keeps a continuous location
  history for almost nothing.
- **Noise is rejected.** Mailbox runs, pottering in the garden, circling the car park —
  not recorded. A trip must be a roughly straight journey of at least **600 m**, and
  its start is back-dated to when you actually left.
- **Transit waits don't split a trip.** A mid-journey stop of up to ~20 min (bus,
  train, traffic) is kept as a "waited 14 min" note; the trip stays one journey.
- **Routes tab** groups trips by start→end pair: "Home → Work · 23 trips · typ. 24 min
  (p90 31 min)", with a by-hour histogram. Rename a route freely.
- **Places tab**: name discovered places, set a match radius, mark transit stops, merge
  duplicates, pin a place to your current location.
- Manual start/stop is still on the Home tab as an override.

### Later

- **Phase B — precision:** Wi-Fi fingerprinting (nearby network IDs + names at each
  stop, no connection made) to separate places a few metres apart; a "new place or
  existing one?" review flow.
- **Phase C — naming:** a small on-device LLM (Gemma 3 1B, ~550 MB, **downloaded once**
  then fully offline) running only in the nightly charging job, to name new places
  (from the Wi-Fi network names it saw) and label routes. Rule-based fallback. Plus
  trip-path maps and CSV/JSON export.

## Privacy

| Concern | How it's handled |
|---|---|
| Data leaving the device | The app declares **no `INTERNET` permission** (and no `ACCESS_NETWORK_STATE`). It is not capable of making a network request. |
| Cloud backup / device transfer | `allowBackup="false"`; `data_extraction_rules.xml` excludes every domain from both cloud backup and device-to-device transfer. |
| Where data lives | Room/SQLite database in the app's private storage (`/data/data/com.ferhat.commutetracker/`), unreadable by other apps. |
| Raw location history | Kept in full ~90 days, then thinned to ~1 fix/hour, then deleted. Trips and places stay until you delete them. |
| Third-party SDKs | None for analytics/crash reporting. Google Play Services is used only for the on-device location, geofence and activity-recognition APIs (IPC to the system, not the network). |
| The Phase C model | Downloaded once over Wi-Fi, then runs entirely offline — no telemetry, no per-inference network. Reverse geocoding stays disabled; place names come from Wi-Fi SSIDs seen locally. |

## Permissions requested

`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
("Allow all the time" — required for background trip detection), `ACTIVITY_RECOGNITION`,
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`,
`RECEIVE_BOOT_COMPLETED`. Grant them from the **Settings tab**, which walks through each
one. Excluding the app from battery optimisation is recommended so the OS doesn't kill
tracking overnight.

## How trip detection works

1. **Log:** every GPS fix (trip fixes, activity/geofence transitions, a 30-min
   heartbeat) goes into `position_log` — the raw "where was I" history.
2. **Wake / record:** an activity-recognition ENTER `IN_VEHICLE`/`WALKING`/… or EXIT
   `STILL`, or a geofence EXIT, starts a foreground service that samples location at
   `PRIORITY_HIGH_ACCURACY` while you move; it stops ~5 min after you go still.
3. **Analyse (`TripDetector`, unit-tested):** over a rolling window of `position_log`,
   bad fixes are dropped (accuracy > 40 m, teleports), the track is Kalman-smoothed,
   then a state machine over **straightness** (`net / path`), **radius of gyration**
   and **distance-from-anchor** finds journeys. Mid-trip stops shorter than the
   transit-wait tolerance become `TripStop`s, not boundaries. A journey is kept only if
   it clears the 600 m / 700 m-path / 3-min / straightness gates.
4. **Group:** `TripGrouper` aggregates finished trips by place pair for the Routes tab.

See `analysis/DetectionConfig.kt` for every threshold, and the plan file for the full
design rationale.

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · DataStore ·
`play-services-location` (Fused Location, Geofencing, Activity Recognition Transition).
minSdk 26, target/compile SDK 35. Single module.

Pure analysis code lives in `com.ferhat.commutetracker.analysis` and is covered by unit
tests (`./gradlew testDebugUnitTest`). Sensing lives in `…​.tracking`.

## Installing on your phone

Every push to `main` runs CI (unit tests + signed release APK) and publishes a
**GitHub Release** tagged `v1.0.<build#>`. Download the APK from the Releases page and
open it. Updates install over the previous version (same signing key).

## Building locally

JDK 17–21 + Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew testDebugUnitTest assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Signing

`keystore/commute-tracker.jks` + `keystore.properties` are committed on purpose so every
build has the same signature (updates install cleanly). Not secret-grade; move to
GitHub secrets if this ever matters.

## Licence

MIT — see [LICENSE](LICENSE).
