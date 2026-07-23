# Redplate — Project Spec

A single-user strength training app. Local-only, zero infrastructure, zero recurring cost.
Target device: **Samsung Galaxy S24 Ultra** (162.3 × 79 × 8.6 mm, 6.8", 1440 × 3120, ~384 × 824 dp).

This file is the contract. If a request conflicts with something here, say so before writing code.

---

## 1. Non-negotiables

- **No backend. No accounts. No analytics. No ads.** The app must function identically in
  airplane mode, forever.
- **No network calls by the app.** One exception, defined in COACHING.md: a "watch form videos"
  action may fire an external `ACTION_VIEW` intent. That launches another app — Redplate itself
  still opens no sockets, and every feature must work offline.
- **No third-party SaaS dependency of any kind.** If a service could pause, rate-limit, change
  pricing, or shut down, it is not allowed in this project.
- Single device, single user. Do not build multi-user abstractions, tenancy, or sync
  reconciliation. There is no "other device."
- Portrait only. Landscape is locked out.
- `minSdk` = the target phone's API level. Do **not** write compatibility shims, `VERSION.SDK_INT`
  branches, or AppCompat fallbacks. There is exactly one device to support.

---

## 2. Architecture

```
UI (Compose)  →  ViewModel (StateFlow)  →  Repository  →  Room DAO  →  SQLite
```

- **Kotlin + Jetpack Compose + Material 3**, single-activity, no Fragments.
- **Room** is the single source of truth. Every screen observes Flows from the DAO; nothing holds
  duplicate state.
- **Hilt** for DI.
- **No Retrofit, no OkHttp, no Ktor.** If you find yourself adding a networking dependency,
  something has gone wrong — stop and ask.
- `kotlinx.serialization` for import/export only.

### Data durability
1. **Android Auto Backup** — `android:allowBackup="true"`, with a `backup_rules.xml` that includes
   the Room DB and excludes caches. Automatic, free, ~25 MB ceiling we will never approach.
2. **Manual JSON export/import** via the Storage Access Framework (`ACTION_CREATE_DOCUMENT` /
   `ACTION_OPEN_DOCUMENT`). Full fidelity — must be able to rebuild the entire database from it.
3. **CSV export** for spreadsheet analysis. Lossy, convenience only.

Export must exist and be tested **before** the first real workout is logged. Data loss is the only
unrecoverable failure mode in this project.

### Exercise seed data
Bundle `yuhonas/free-exercise-db` (`dist/exercises.json`, public domain, ~800 exercises) in
`assets/`. Seed Room on first launch inside a transaction. User-created exercises live in the
same table with an `isCustom` flag.

Do **not** bundle exercise media in the APK. Media (stills, animations) lives in the app's
external files directory and is pushed with `adb`, resolved by filename convention through a
`MediaResolver`. See COACHING.md §4. This keeps the APK small, Gradle loops fast, and binaries
out of git — and lets the media set change without a rebuild.

---

## 3. Design direction

Default Material 3 is the templated answer and is rejected. So is the neon-gradient fitness-app
look. The design derives from the actual subject: **calibrated plates and gym instrumentation.**

### Concept
The app reads like a piece of equipment, not a consumer app. High contrast, instrument-grade
legibility, quiet chrome, and one loud element: the plate stack.

### Signature element — the plate stack
A live rendering of how the barbell is actually loaded, in **IPF/IWF calibrated plate colours**.
It is simultaneously the plate calculator and the weight readout. Glanceable from the rack at
two metres. This is the one memorable thing in the app; everything around it stays disciplined.

| Plate | Colour     | Hex       |
|-------|------------|-----------|
| 25 kg | Red        | `#C8102E` |
| 20 kg | Blue       | `#0057B8` |
| 15 kg | Yellow     | `#FFD100` |
| 10 kg | Green      | `#00843D` |
| 5 kg  | White      | `#F2F2F2` |
| 2.5 kg| Black/Red  | `#1A1A1A` |

These are functional, not decorative — the colours mean the same thing on screen as on the bar.

### Palette
| Token          | Hex       | Use                                          |
|----------------|-----------|----------------------------------------------|
| `ground`       | `#000000` | Base. True black — AMOLED, battery, glare.   |
| `surface`      | `#121417` | Cards, sheets, raised rows                   |
| `line`         | `#2A2F36` | Hairline dividers, stepper borders           |
| `ink`          | `#F5F5F0` | Primary type                                 |
| `inkMuted`     | `#8B939E` | Labels, units, secondary data                |
| `live`         | `#FF5C1A` | Active set, running timer, primary action    |

`live` is the only warm accent and appears in exactly one place per screen. Plate colours are used
solely inside the plate stack. Never encode PR or progression state in red/green alone — pair with
an icon or label.

### Type
- **Display / numerals:** IBM Plex Sans Condensed (OFL, bundled). Tabular figures mandatory —
  digits must not jitter as weight increments.
- **Body / UI:** IBM Plex Sans (OFL, bundled).
- **Data / logs:** IBM Plex Mono for set history and export previews.

Scale: `112sp` timer · `56sp` weight readout · `20sp` exercise name · `15sp` body ·
`12sp` labels (letterspaced, uppercase, `inkMuted`).

Bundle the fonts. Do not rely on Google Fonts at runtime — that is a network call.

### Motion
Restrained. One orchestrated moment: the plate stack animating as weight changes. Everything else
is a 120 ms fade or nothing. Respect reduced-motion.

---

## 4. Ergonomics — hard layout rules

The S24 Ultra is 162 mm tall. A one-handed thumb arc covers roughly the bottom 45–55% of the
screen. These are not suggestions.

- **Top 0–370 dp: read-only.** Exercise name, set number, target reps, previous session's numbers,
  rest countdown. Nothing tappable except a back affordance duplicated by the system gesture.
- **Bottom 490–824 dp: all controls.** Steppers, complete-set, timer skip.
- **Primary action is a full-width bar**, 88 dp tall, spanning all 384 dp. Never a corner FAB —
  a full-width bar is identical for left- and right-handed users.
- **Minimum touch target 64 dp**, not Material's 48 dp. The user is out of breath with chalk or
  sweat on their hands.
- **No gesture-only actions.** Every swipe or long-press must have a visible tappable equivalent.
  Sweat causes both false and rejected touches.
- **`keepScreenOn` during an active workout.** Otherwise the screen sleeps every rest period.
- **Haptics on set completion.** The user often isn't looking at the screen. Distinct patterns for
  set logged, PR hit, rest complete.
- **Rest timer at 112sp minimum** — readable with the phone on the floor.
- Support font scaling to 200%. No fixed-height containers around `sp` text.
- TalkBack content descriptions on every stepper. This is where screen-reader users get stranded.
- Settings toggle for left/right-hand that mirrors any asymmetric layout.

---

## 5. Feature scope

### v1 — build in this order
1. Room schema, entities, DAOs; seed from `exercises.json`
2. JSON export + import, round-trip tested
3. **Set logging screen** — weight/rep steppers, plate stack, rest timer, supersets, RPE/RIR
4. **Install on device. Do one real training session. Fix what's wrong.**
5. Routine/program builder with auto-progression rules
6. Exercise guidance sheet — text, images, form-video hand-off (COACHING.md)
7. History, PR detection, estimated 1RM curves
8. Per-muscle volume tracking and plateau detection

Steps 3 and 4 are the project. If set logging feels wrong at rep 8, nothing downstream matters.

### Explicitly out of scope
Social feed, program sharing, hosted video, cloud sync, LLM coaching, S Pen integration,
Wear OS (until v1 is being used weekly).

### Progression engine
This is the differentiator and the reason the app exists. Deterministic, local, driven by the
user's own history: double progression, percentage-based cycles, RPE-autoregulation, and fatigue
tracking via rolling per-muscle tonnage. No black boxes — every recommendation must be able to
show its reasoning in plain language. Design it to be tuned by the user, not by a vendor.

---

## 6. Conventions

- Package: `dev.<name>.redplate`, feature-based packages (`workout/`, `program/`, `history/`).
- Compose functions are stateless and hoisted; `@Preview` for every screen state including
  empty and error.
- **Empty states are invitations to act, never apologies.** Errors say what happened and how to
  fix it. Button labels are active voice and keep the same verb through the flow.
- Units: store everything in **kg internally**, convert at the display layer only.
- Timestamps: store as epoch millis UTC, convert at display.
- Never destructive-migrate Room in a build the user has installed. Write real migrations.
- Commit after every green build.

---

## 7. Known friction

- **Gradle loop is slow.** Prefer fewer, larger, well-specified tasks over many small ones.
- **You cannot see the running UI.** Use `@Preview` and ask for screenshots. The user is the
  visual QA loop — surface anything ergonomically uncertain rather than guessing.
- **Compiles ≠ correct.** Watch for lifecycle leaks, state scattered across ViewModels, and
  Flow collection outside the right scope.
- Install the Kotlin LSP plugin. Without type-aware tooling, `Flow<List<X>>` vs `List<X>` errors
  pass silently.