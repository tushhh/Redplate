# Redplate

Single-user, offline-first strength training app for **Samsung Galaxy S24 Ultra**.

> **Status:** Early development
>
> This README is the implementation and contributor guide. The product contract/spec lives in the project docs and is authoritative if anything here conflicts.

---

## Table of Contents

- [Overview](#overview)
- [Core Principles (Non-negotiables)](#core-principles-non-negotiables)
- [Target Device & Platform](#target-device--platform)
- [Architecture](#architecture)
- [Feature Set](#feature-set)
- [Data Model & Storage](#data-model--storage)
- [Backup, Export, and Import](#backup-export-and-import)
- [Exercise Database Seeding](#exercise-database-seeding)
- [Media Strategy (No APK Bloat)](#media-strategy-no-apk-bloat)
- [Design System](#design-system)
- [Project Structure](#project-structure)
- [Build & Run](#build--run)
- [Testing Strategy](#testing-strategy)
- [Definition of Done (DoD)](#definition-of-done-dod)
- [Roadmap](#roadmap)
- [Privacy & Security](#privacy--security)
- [Licenses & Attributions](#licenses--attributions)
- [Contributing](#contributing)

---

## Overview

Redplate is a local-only strength training app designed to feel like gym instrumentation rather than a generic consumer fitness product.

The app is optimized for one person on one phone, with **no backend and no recurring cost**. It must remain fully functional forever in airplane mode.

### Product intent

- Fast logging during workouts
- Clear progression tracking
- Durable local data with backup + export/import
- Distinct, equipment-inspired visual identity centered around a live calibrated plate stack

---

## Core Principles (Non-negotiables)

1. **No backend / accounts / analytics / ads.**
2. **No network calls by Redplate.**
   - One controlled exception: launching external form-video links via Android `ACTION_VIEW` intent (handled by another app).
3. **No third-party SaaS dependencies** (present or future).
4. **Single-user, single-device model** (no sync, no multi-user abstractions).
5. **Portrait-only UI**.
6. **`minSdk` equals target device API level** (no compatibility branching or legacy fallbacks).

If a proposed implementation conflicts with these, stop and resolve the conflict before coding.

---

## Target Device & Platform

- **Device:** Samsung Galaxy S24 Ultra
- **Physical dimensions:** 162.3 × 79 × 8.6 mm
- **Display:** 6.8", 1440 × 3120
- **Approx. dp canvas:** ~384 × 824 dp
- **Orientation:** Portrait only
- **Platform assumptions:** Modern Android API only (no back-compat shim layer)

---

## Architecture

```text
UI (Compose)  →  ViewModel (StateFlow)  →  Repository  →  Room DAO  →  SQLite
```

### Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **App shell:** Single-activity, no Fragments
- **State:** `StateFlow`
- **DI:** Hilt
- **Persistence:** Room (single source of truth)
- **Serialization:** `kotlinx.serialization` (import/export only)

### Explicitly excluded

- Retrofit
- OkHttp
- Ktor
- Any networking dependency

---

## Feature Set

### Workout logging

- Create and run workouts/sessions
- Log sets, reps, load, and notes quickly
- Keep active-set and timer state highly visible

### Exercise catalog

- Seeded with `free-exercise-db` exercises on first launch
- Support user-created custom exercises (`isCustom = true`)

### Plate calculator (signature feature)

- Live barbell plate-stack rendering using calibrated plate colors
- Doubles as weight readout and loading aid
- Readable at distance (rack-side glanceability)

### History & progression

- Browse prior sessions
- View lift progression over time
- Highlight meaningful changes without color-only encoding

### Data portability

- Automatic Android backup
- Full-fidelity JSON export/import
- CSV export for spreadsheets

---

## Data Model & Storage

- **Room is authoritative** for all persisted app state.
- Screens observe DAO-backed `Flow`s directly (through Repository/ViewModel).
- Avoid duplicate state caches that can drift from DB truth.

### Recommended entity categories

- Exercises
- Workouts/templates
- Workout sessions
- Sets
- Optional metadata (tags, equipment, notes, media refs)

> Exact schema evolves during implementation, but export/import must preserve complete logical fidelity.

---

## Backup, Export, and Import

Data durability is critical and implemented in three layers:

### 1) Android Auto Backup (primary safety net)

- `android:allowBackup="true"`
- `backup_rules.xml` includes Room DB files
- Caches excluded
- Free and automatic

### 2) Manual JSON export/import (full fidelity)

- Export via `ACTION_CREATE_DOCUMENT`
- Import via `ACTION_OPEN_DOCUMENT`
- JSON must reconstruct the full database state exactly

### 3) CSV export (convenience)

- For spreadsheet analysis
- Lossy by design

### Reliability requirement

Export/import workflows must be implemented and tested **before real workout data is logged**.

---

## Exercise Database Seeding

On first launch:

1. Read `assets/dist/exercises.json` (from `yuhonas/free-exercise-db`)
2. Insert into Room within a single transaction
3. Mark bundled entries as non-custom
4. Keep user-created exercises in same table with `isCustom = true`

This ensures one unified exercise catalog.

---

## Media Strategy (No APK Bloat)

Exercise media (stills/animations) is **not** bundled in APK.

- Media files live in app-specific external storage
- Pushed via `adb`
- Resolved by filename convention through `MediaResolver`

Benefits:

- Smaller APK
- Faster Gradle/build loops
- No large binaries in git
- Media can evolve without app rebuild

---

## Design System

Default Material look is intentionally not the end state. Redplate uses an instrumentation-inspired style.

### Concept

- Equipment-like feel
- High contrast
- Quiet surfaces/chrome
- One loud focal object: **plate stack**

### Calibrated plate colors (functional semantics)

| Plate | Colour | Hex |
|---|---|---|
| 25 kg | Red | `#C8102E` |
| 20 kg | Blue | `#0057B8` |
| 15 kg | Yellow | `#FFD100` |
| 10 kg | Green | `#00843D` |
| 5 kg | White | `#F2F2F2` |
| 2.5 kg | Black/Red | `#1A1A1A` |

These colors are meaningful, not decorative.

### Core palette

| Token | Hex | Purpose |
|---|---|---|
| `ground` | `#000000` | Base/AMOLED background |
| `surface` | `#121417` | Cards/sheets/elevated rows |
| `line` | `#2A2F36` | Dividers/borders |
| `ink` | `#F5F5F0` | Primary text |
| `inkMuted` | `#8B939E` | Secondary labels/units |
| `live` | `#FF5C1A` | Active set/timer/primary action |

### Accent policy

- `live` appears once per screen as primary warm highlight.
- Plate colors are restricted to plate-stack rendering.
- Progress/PR states must not rely on red-vs-green alone; include icon/label.

### Typography

- **Display/numerals:** IBM Plex Sans Condensed (bundled, OFL)
- **Body/UI:** IBM Plex Sans (bundled, OFL)
- Tabular figures required for stable numeric alignment

---

## Project Structure

Suggested high-level module/package layout:

```text
app/
  src/main/
    java/.../
      ui/
      features/
      data/
        db/
        dao/
        entities/
        repository/
      domain/
      di/
      export/
      media/
    assets/
      dist/exercises.json
    res/
      xml/backup_rules.xml
```

Keep feature boundaries clear, but avoid over-engineering for multi-user or cloud scenarios.

---

## Build & Run

### Requirements

- Android Studio (current stable)
- Android SDK matching target device API level
- Kotlin/AGP versions defined in Gradle files

### Quick start

1. Clone repository
2. Open in Android Studio
3. Sync Gradle
4. Run on Samsung Galaxy S24 Ultra (or identical API emulator if needed)

### Debug setup for media

- Use `adb push` to place exercise media into app external files directory
- Ensure naming matches `MediaResolver` conventions

---

## Testing Strategy

### Must-have tests

- Room migration/integrity tests
- Seed-data insertion test (first launch)
- JSON export round-trip test (export → clear DB → import → verify equality)
- CSV export shape/content sanity test
- ViewModel state-flow tests for active workout logging

### Manual QA checklist (minimum)

- Airplane mode full-session workout flow
- Cold start with seeded exercises
- Auto backup enabled and DB included
- JSON import of real exported file succeeds
- App remains portrait-only
- No network permissions or traffic from app process

---

## Definition of Done (DoD)

A feature is done when:

- It works offline end-to-end
- It persists via Room and survives process death
- It is included in backup/export behavior where applicable
- It meets readability and interaction speed needs during training
- It does not violate non-negotiables

---

## Roadmap

### Phase 1 — Foundation

- Project skeleton (Compose/Hilt/Room)
- Exercise DB seed on first launch
- Backup rules
- JSON export/import pipeline
- CSV export baseline

### Phase 2 — Training Core

- Workout/session creation
- Set logging flow
- Timer + active set highlighting
- Plate calculator + stack rendering

### Phase 3 — Insight & Polish

- History and progression views
- PR/progression indicators (accessible)
- UI refinement to instrumentation aesthetic
- Performance and stability hardening

---

## Privacy & Security

- No account data
- No telemetry
- No external API communication
- All training data remains on-device unless user explicitly exports

---

## Licenses & Attributions

- **Exercise dataset:** `yuhonas/free-exercise-db` (public domain), bundled JSON source
- **Fonts:** IBM Plex Sans + IBM Plex Sans Condensed (OFL)

Add/update a dedicated `LICENSES.md` if additional third-party assets are introduced.

---

## Contributing

This project has strict architectural and product constraints.

Before proposing changes:

1. Verify the change is offline-compatible and local-only.
2. Verify no networking dependency is added.
3. Verify data durability implications (backup/export/import).
4. Verify UI remains portrait-first and high-legibility.

If a request conflicts with the project spec, call out the conflict explicitly before implementation.
