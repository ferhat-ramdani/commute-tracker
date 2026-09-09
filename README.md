# Commute Tracker

An Android app that tracks how long you spend travelling between the places you visit —
Home, Work, and anywhere else — and groups those trips into routes.

Everything runs **on the device**. The app has **no internet permission at all**, so no
location data can ever be uploaded. Cloud backup of its data is disabled too.

## What it does

### Automatic background tracking (Phase A — current)

- Detects trips on its own. You don't press start.
- **Battery-friendly by design:** the app is fully asleep while you're not moving. The
  OS activity-recognition sensor (hardware, near-zero power) and geofences around your
  known places wake it when you start or stop moving. GPS only runs *during* a trip,
  and only then is there an ongoing notification.
- When you settle somewhere for a few minutes, it works out the trip you just made
  (from where, to where, how long, how far), matches the endpoints to your places, and
  discovers a new place if you stopped somewhere unknown.
- **Routes tab** groups every trip by its start→end pair: "Home → Work · 23 trips ·
  typ. 24 min (p90 31 min)", with a by-hour histogram. Rename a route to anything you
  like ("Friday prayer", "School run").
- **Places tab**: review discovered places, name them, set a match radius, merge two
  places that are really the same, or delete.
- Manual start/stop is still there on the Home tab as an override.

### Later

- **Phase B — precision:** Wi-Fi fingerprinting (records the set of nearby network IDs
  at each stop, no connection made) to tell places a few metres apart from each other;
  a "new place or existing one?" review flow.
- **Phase C — intelligence:** a small on-device LLM (opt-in) that runs rarely, only in a
  nightly charging-time job, to suggest names for new places and routes. Rule-based
  naming otherwise. Trip-path maps, stats, CSV/JSON export.

## Privacy

| Concern | How it's handled |
|---|---|
| Data leaving the device | The app declares **no `INTERNET` permission** (and no `ACCESS_NETWORK_STATE`). It is not capable of making a network request. |
| Cloud backup / device transfer | `allowBackup="false"`; `data_extraction_rules.xml` excludes every domain from both cloud backup and device-to-device transfer. |
| Where data lives | Room/SQLite database in the app's private storage (`/data/data/com.ferhat.commutetracker/`), unreadable by other apps. |
| Raw GPS retention | Individual location fixes are deleted ~7 days after their trip is analysed. Trips and places stay until you delete them. |
| Third-party SDKs | None for analytics/crash reporting. Google Play Services is used only for the on-device location, geofence and activity-recognition APIs (IPC to the system, not the network). |

## Permissions requested

`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
("Allow all the time" — required for background trip detection), `ACTIVITY_RECOGNITION`,
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`,
`RECEIVE_BOOT_COMPLETED`. Grant them from the **Settings tab**, which walks through each
one. Excluding the app from battery optimisation is recommended so the OS doesn't kill
tracking overnight.

## How trip detection works

1. **Wake:** activity-recognition ENTER `IN_VEHICLE`/`WALKING`/… or EXIT `STILL`, or a
   geofence EXIT, starts a foreground service that samples location at
   `PRIORITY_BALANCED_POWER_ACCURACY` (~20 s interval, batched).
2. **Settle:** activity-recognition ENTER `STILL` schedules a check 4 minutes out. If
   nothing resumes movement, the trip is over: the service stops.
3. **Analyse (`AnalysisWorker`):** [stay-point detection][li2008] over the buffered
   samples finds where you stopped; the movement between stops becomes a `Trip`;
   endpoints are resolved to `Place`s (new ones created as needed); stay-noise samples
   are dropped.
4. **Group:** `TripGrouper` (pure function, unit-tested) aggregates trips by place pair
   for the Routes tab.

[li2008]: https://dl.acm.org/doi/10.1145/1463434.1463477

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
