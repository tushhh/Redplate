# Redplate

Single-user, offline-first strength training app for **Samsung Galaxy S24 Ultra**.

> **Status:** Active development (foundation in place)

---

## Overview

Redplate is a local-only strength training app designed to feel like gym instrumentation rather than a generic consumer fitness product.

The app is optimized for one person on one phone, with **no backend and no recurring cost**. It must remain fully functional in airplane mode.

## Current Repository Snapshot

This section reflects the current state of `tushhh/Redplate` on `master`.

- **Language:** Kotlin (100%)
- **Build system:** Gradle Kotlin DSL + Version Catalog
- **Project modules:** `:app`
- **Architecture direction:** Compose UI → ViewModel/StateFlow → Repository → Room/SQLite
- **Networking stack:** None added in the current Gradle setup

## Tech Stack (as configured)

- **Android Gradle Plugin:** `9.3.1`
- **Kotlin:** `2.2.10`
- **KSP:** `2.2.10-2.0.2`
- **Compose BOM:** `2026.02.01`
- **Room:** `2.7.1`
- **Hilt:** `2.60.1`
- **kotlinx.serialization:** `1.8.1`

Primary source: `gradle/libs.versions.toml`.

## Android Configuration (app module)

- `namespace`: `dev.redplate`
- `applicationId`: `dev.redplate`
- `compileSdk`: `36` (minor API level `1`)
- `minSdk`: `36`
- `targetSdk`: `36`
- Java compatibility: `11`
- Compose enabled
- Activity orientation locked to portrait in `AndroidManifest.xml`
- Backup is enabled (`allowBackup=true`) with backup/data extraction rules configured

## Current Dependency Set

Configured libraries currently include:

- Jetpack Compose + Material 3
- Lifecycle runtime + compose + ViewModel compose
- Hilt + hilt-navigation-compose
- Room runtime + ktx + compiler (via KSP)
- kotlinx.serialization JSON
- AndroidX test + Espresso + Compose UI test

## Project Structure (current)

```text
Redplate/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     └─ AndroidManifest.xml
├─ gradle/
│  └─ libs.versions.toml
├─ build.gradle.kts
├─ settings.gradle.kts
└─ README.md
```

> Note: This is a concise snapshot based on currently inspected root/app build files and manifest.

## Build & Run

### Requirements

- Android Studio (current stable)
- Android SDK platform/API 36 installed
- JDK compatible with project (Java 11 target)

### Quick Start

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Run the `app` configuration on a device/emulator with API 36

### Command Line

From repo root:

```bash
./gradlew :app:assembleDebug
```

## Principles (project intent)

- Offline-first, local-only behavior
- No backend/accounts/analytics/ads
- Portrait-first UX
- Durable local data model with backup/export pathways

## Contributing

Before proposing changes:

1. Keep the app offline-compatible and local-only.
2. Avoid adding networking dependencies.
3. Preserve data durability assumptions.
4. Keep UX readable and fast for workout logging.
