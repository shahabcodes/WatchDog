# Guardian — Parental Control (Android)

Two Jetpack Compose apps (parent + child) sharing a common core, backed by Firebase.
This repository currently covers **Step 1** (project + Gradle convention plugins + iOS-style
`core-ui` theme) and **Step 2** (Firebase Auth + user profiles).

> Responsible use: monitoring features (added in later steps) are intended for a parent
> managing a minor child's device. Keep monitoring transparent on the child device and
> comply with Google Play's policies and local law.

## Module layout

```
build-logic/convention   Gradle convention plugins (fms.android.*)
core/model               Shared domain models (UserProfile, Geofence, AppRule…)
core/common              AppResult, dispatchers, DataStore theme prefs
core/ui                  iOS (Cupertino) Compose theme + components, light/dark
core/firebase            Firebase DI, AuthRepository, ProfileRepository
parent-app               Parent app: email/password auth + profile setup + dashboard stub
child-app                Child app: anonymous auth + profile setup + pairing stub
```

## Tech stack

- Kotlin 2.1, Jetpack Compose (Material3 mapped to a Cupertino palette)
- Hilt (DI), Coroutines/Flow, DataStore
- Firebase Auth, Firestore, Storage (BOM-managed)
- Gradle convention plugins + version catalog (`gradle/libs.versions.toml`)
- minSdk 26, compileSdk/targetSdk 35, Java 17

## Prerequisites

1. **Android Studio** (Ladybug or newer) with JDK 17.
2. A **Firebase project** with Auth (Email/Password + Anonymous), Firestore, and Storage enabled.

## Firebase setup

1. In the Firebase console create two Android apps:
   - `com.shahaabapps.watchdog`
   - `com.shahaabapps.shield`
2. Download each `google-services.json` and place them at:
   - `parent-app/google-services.json`
   - `child-app/google-services.json`

   These files are git-ignored; each developer supplies their own.
3. Enable sign-in providers: **Email/Password** and **Anonymous**.
4. Deploy the rules in `firebase/firestore.rules` and `firebase/storage.rules`.

> The apps apply the `google-services` Gradle plugin, so the build will fail until the
> two `google-services.json` files are present.

## Building

Open the project in Android Studio and let it sync — it will complete the Gradle wrapper
and download dependencies. Then run the `parent-app` or `child-app` configuration.

From the command line (once the wrapper jar exists / after a first Studio sync):

```bash
./gradlew :parent-app:assembleDebug
./gradlew :child-app:assembleDebug
```

If you have a standalone Gradle 8.11+ installed, you can generate the wrapper first with:

```bash
gradle wrapper --gradle-version 8.11.1
```

## What works now

- Shared iOS-style theme with light/dark (follows system; persisted preference plumbing in place).
- Parent: email/password sign-up & sign-in, profile setup (name, photo upload, DOB), dashboard stub.
- Child: anonymous sign-in, profile setup, pairing placeholder.

## Next steps (per plan)

3. QR pairing (child shows QR, parent scans) + multi-child dashboard.
4. App-usage enforcement engine (schedules + daily limits).
5. Parent-authored geofences + child enforcement + alerts.
6. Remote command channel + screenshot.
7. Reports, settings (incl. theme switcher UI), tests, CI.

## Continuous integration (APK built on GitHub)

`.github/workflows/android.yml` builds debug APKs for both apps on every push/PR to
`main` and uploads them as downloadable artifacts (`parent-app-debug`, `child-app-debug`).
It provisions Gradle 8.11.1 directly (no wrapper jar needed on CI).

Firebase config on CI:
- By default the workflow copies the committed **placeholders** in `ci/` so a build
  always succeeds. The resulting APKs install and run but won't reach a real backend.
- For real builds, add repository secrets with base64 of your files:
  ```bash
  base64 -w0 parent-app/google-services.json   # -> GOOGLE_SERVICES_PARENT_B64
  base64 -w0 child-app/google-services.json     # -> GOOGLE_SERVICES_CHILD_B64
  ```
  (On macOS use `base64 -b0`.) Add them under Settings → Secrets and variables → Actions.

## Notes

- The convention plugins target AGP 8.8 / Gradle 8.11. If you bump AGP, the
  `CommonExtension<*, *, *, *, *, *>` arity in `KotlinAndroid.kt` may need adjusting.
