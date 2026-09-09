# Commute Tracker

A tiny Android app to track how long you spend commuting between saved locations
(Home, Work, and any others you add).

## What it does (v1)

- **Start a commute** by picking a *From* and *To* location.
- **End a commute** — the app records start time, end time, and total duration.
- **Cancel** an in-progress commute if you started it by mistake.
- **Manage locations** — add, rename, delete. "Home" and "Work" are seeded on first run.
- **History** of every finished commute with date and duration.

Data is stored locally in a Room/SQLite database (`commute-tracker.db`). No network,
no accounts, no location permissions.

### Not in v1 (possible later)

- GPS auto-detection of which location you're at / auto start-stop.
- Stats (averages per route, per weekday), CSV export.
- A persistent notification while a commute is running.

## Tech

- Kotlin + Jetpack Compose (Material 3)
- Room for persistence
- Single-activity, Navigation Compose
- minSdk 26 (Android 8.0), targetSdk 35

## Installing on your phone

Every push to `main` runs the CI pipeline, which builds a **signed release APK**
and publishes it as a **GitHub Release**.

1. Open the repo's **Releases** page on your phone.
2. Download the latest `commute-tracker-<version>.apk`.
3. Open it and allow "install from this source" if prompted.
4. Updates install straight over the previous version — every build is signed with
   the same key (`keystore/commute-tracker.jks`).

## Building locally

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

## Signing

`keystore/commute-tracker.jks` and `keystore.properties` are committed on purpose.
For a personal, sideloaded app the point of the key is a **stable signature** so
updates install cleanly — not secrecy. If you ever publish this app or want the key
private, regenerate the keystore and move the four `keystore.properties` values into
GitHub Actions secrets.

## CI pipeline

`.github/workflows/android.yml`:

- **push to `main`** → build signed APK → upload as workflow artifact → publish a
  GitHub Release tagged `v1.0.<run-number>`.
- **pull request** → build + upload artifact only (no release).
- **manual run** (`workflow_dispatch`) supported.

`versionName` = `1.0.<run-number>`, `versionCode` = `<run-number>`, so every release
is strictly newer than the last.
