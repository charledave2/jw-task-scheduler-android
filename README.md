# JW Task Scheduler for Android

Native Android app for congregation meeting assignments and task reviews. This repository contains the Android app only; the existing Cloudflare/backend repository remains separate.

## Workspace modes

- **Local workspace:** keeps people, schedules, reviews, and profile photos on this device. Manual JSON backup and restore are available in Settings → Data & Backup.
- **Shared workspace:** optional sign-in to the configured online service for multiple-device access.

The local workspace is device-only; switching to shared mode does not upload or merge local data.

## Build

Requires JDK 21, Android SDK Platform 35, Build Tools 35.0.0, and Gradle 8.10.2.

```sh
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release

Bump `versionName` and `versionCode` in `app/build.gradle.kts` and push to `main`. GitHub Actions builds the APK and publishes it with a source archive and release notes.
