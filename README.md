# Redplate

A strength training app that reads like a piece of gym equipment, not a consumer app.

Single user, single device, **no backend, no accounts, no analytics, no ads, no subscription**.
It works identically in airplane mode, and it will keep working when every fitness startup
in your app drawer has been acquired and shut down.

Built for one phone: the **Samsung Galaxy S24 Ultra** (6.8", ~384 × 824 dp, portrait only).

---

## Why this exists

Every training app converges on the same shape: a flat exercise list, a rep counter, and a
paywall in front of the part that would actually coach you. The programming is either a static
PDF or a black box that can't explain itself.

Redplate is the opposite bet. The programming engine is the product, it runs entirely on the
device, and **every prescription can show its reasoning in plain language**. There is no model
to call, no server to be down, and nothing to renew.

---

## The plate stack

The one loud element in an otherwise quiet interface.

As you step the weight, the barbell on screen loads itself in **IPF/IWF calibrated plate
colours** — the same colours as the discs in your hands. It is simultaneously the plate
calculator and the weight readout, and it is legible from the rack at two metres.

| Plate  | Colour    | Hex       |
|--------|-----------|-----------|
| 25 kg  | Red       | `#C8102E` |
| 20 kg  | Blue      | `#0057B8` |
| 15 kg  | Yellow    | `#FFD100` |
| 10 kg  | Green     | `#00843D` |
| 5 kg   | White     | `#F2F2F2` |
| 2.5 kg | Black     | `#1A1A1A` |
| 1.25 kg| Chrome    | `#C0C0C0` |

The colours are functional, not decorative. Plates are only ever drawn from the pairs you
actually own, heaviest first, in rack order. If a target weight can't be assembled from your
plates, the app says so rather than showing a number you can't load — and it searches the
pairs exhaustively rather than greedily, so an odd set (one pair of 10s, two of 7.5s) still
reaches every weight it can actually make.

---

## What it does

### Coaches, rather than records

Most trackers are a spreadsheet with rounded corners. Redplate prescribes the session, then
adapts it from your own history.

- **Program generation** — answer five questions and get a full mesocycle: a split matched to
  your days, exercises filtered by the equipment you actually have, rep ranges and rest
  intervals set by your goal, and a deload planned at the end of the block.
- **Volume landmarks** — weekly hard sets per muscle tracked against MV / MEV / MAV / MRV.
  Secondary muscles get half credit; a set only counts if it was logged at 0–3 reps in reserve.
- **Progression rules** — double progression for hypertrophy, load progression for strength
  compounds, RIR autoregulation, all stepping by increments your equipment can physically make.
  Each rule is a separate decision in `ProgressionEngine`, and what it decides is written back
  to the slot: the next session actually opens at the load you earned. An effort you didn't
  report never earns an increase.
- **Blocks that move** — a week advances when its sessions are done, not when the calendar
  says so. Sets climb toward your adaptive range, the final week halves them and drops the
  load, and the next block is seeded from what you lifted.
- **Explains itself** — every slot can render its own prescription in a sentence, and every
  load change says what earned it. No black boxes.
- **Changeable** — goal, days per week, session length, priority muscles and which weekdays
  you train are all editable afterwards. Rebuilding the plan keeps your history and carries
  your loads forward.

### Knows what's in your gym

The differentiator most apps get wrong. Equipment isn't a checkbox list — it's a loading model.

```kotlin
EquipmentEntity(
    loadingScheme = FIXED_INCREMENT,          // dumbbells, kettlebells
    availableLoads = listOf(10.0, 12.0, 14.0) // the ones you actually own
)
```

If your dumbbells jump 10 → 12 → 14 kg, the engine never prescribes 13. If your cable stack
moves in 2.5 kg pins, progression accounts for a jump that might be 15% of the working load.
Equipment whose contents aren't confirmed is marked unavailable and excluded — **fail closed,
never guess open**.

Not every machine is marked in kilograms. A stack numbered 1, 2, 3 with no mass printed on it
is a `RESISTANCE_LEVEL` machine: the readout says `LEVEL 7`, the steppers move one notch at a
time, and no kilogram figure is invented. Levels are ordinal, so they're never converted and
never added into tonnage — level 8 is harder than level 6, but it isn't eight kilograms.

Each exercise names both the **station** it happens at and whatever **supplies the load** —
a barbell squat needs the half rack *and* the barbell, not just the rack. The set logging
header and the exercise browser say which, so an unfamiliar name is still a machine you can
walk to. Turning the barbell off removes the squat; it does not leave it stranded behind a
rack that weighs nothing.

Where the number describes one implement rather than the whole load — a dumbbell rack — the
readout says **`30 KG EACH`**, and tonnage counts both.

**And whatever the app thinks, you can overrule it.** Tap the load readout during a set and
type what you actually used, on a 64 dp keypad rather than the system keyboard. That value is
stored exactly as entered — never snapped to a plate inventory or a ladder the app is only
guessing at. Steppers are for convenience; the keypad is for the truth.

### Built for a gym floor, not a desk

The ergonomics are hard rules, not preferences ([`CLAUDE.md`](CLAUDE.md) §4):

- **Top 370 dp is read-only.** Nothing tappable where your thumb can't reach anyway.
- **All controls in the bottom half.** Primary action is a full-width 88 dp bar — never a
  corner FAB, so it's identical for left- and right-handed use.
- **64 dp minimum touch targets**, not Material's 48. You're out of breath with chalk on
  your hands.
- **No gesture-only actions.** Sweat causes both false and rejected touches.
- **Rest timer at 112 sp** — readable with the phone on the floor. It also runs in the
  status bar, so putting the phone in a pocket between sets doesn't lose it, and the buzz
  at zero is fired by an exact alarm rather than by the screen — it lands whether or not
  the app is still running.
- **Screen stays on** during a session; **haptics** on set logged, PR hit, and rest complete,
  with distinct patterns, because you often aren't looking at the screen. Rest-complete is
  three long buzzes at alarm priority, so Do Not Disturb doesn't swallow it.
- Near-black ground for AMOLED battery and glare, tabular figures so digits don't jitter as
  weight increments.

---

## Setting up, once

Five questions, one per screen, no keyboard. Every answer states its consequence before you
commit to it — pick four days at sixty minutes and the screen tells you, live, that it means
Upper/Lower twice each at 18–22 sets a session.

```
goal  →  schedule  →  equipment  →  who picks the exercises?  →  ⟨ plan library ⟩
```

The equipment step is the one that matters most and the one every other app gets wrong. It
doesn't ask *whether* you have dumbbells — it asks **which ones**, because that answer becomes
the only ladder progressions are allowed to step along. Anything you don't tick disappears from
the archive and stops being prescribed.

The last question is a fork, not a difficulty setting: **give me a plan**, or **I'll choose each
day**. Neither is the advanced path — the difference is only who picks the exercises, and both
track volume identically. You can mix them: follow the plan on Monday, freestyle on Saturday.

---

## A session, end to end

```
Today  →  set logging  ⇄  rest  →  next lift  →  …  →  summary
```

1. **Today** opens on the session the program says is due, the first three lifts, and where
   your weekly volume stands. One tap starts it.
2. **Set logging** is a single set, full screen: the load with its stepper and plate stack,
   the rep counter, and plain-language difficulty chips ("2 more in me", "All I had") that map
   to RIR. Guidance sits top-right, deliberately out of thumb reach — it's a pre-set decision,
   never an in-set one.
3. **Complete the set** → haptic → the rest timer starts at the prescribed interval and counts
   down against a wall-clock deadline, so it stays honest whatever the process does. The same
   countdown appears as a notification and an exact alarm, so leaving the app doesn't stop it.
4. **The rest screen's one button knows what comes next** — another set, the next lift, or
   finish. Label and behaviour come from the same value, so they can't disagree.
5. **Summary** derives tonnage, PRs and per-muscle volume from what you logged.

**Rack occupied?** Open guidance and swap in one tap — substitutes are ranked by
secondary-muscle overlap and filtered to equipment you own, without leaving the session.

### Freestyle, without giving up the coaching

```
body map  →  the session it built  →  swap / add anything  →  train
```

Tap the muscles you feel like training. The map is a status display while you do it: every
region is shaded against that muscle's own weekly landmarks, so you see what's undertrained
*while* deciding what to train.

What comes back is a real session — compounds first while you're fresh, fitted to the minutes
you said you had, filtered to your kit — with a one-line reason beside every exercise ("chest is
seven sets under target this week"). You read it before the first set, not after the last one.
Any row can be swapped and anything can be added, and both go through the same prescription
engine a programmed day does, so a lift you picked by hand is programmed exactly like one the
engine chose.

The browser behind it is tiered rather than alphabetical — what's in this session, then what you
actually train, then the archive — and each card cross-fades the start and end position of the
movement on a staggered loop, which makes a grid readable without reading a single name.

---

## Your data stays yours

Data loss is the only unrecoverable failure in this project, so it gets three independent paths:

1. **Android Auto Backup** — automatic and free, kept on with real rules rather than the
   template ones. Only `redplate.db` goes up; the `-wal` and `-shm` sidecars are excluded on
   purpose. Auto Backup does not pause the app, so a snapshot that catches those three files
   at different moments restores a database SQLite refuses to open — the torn restore. The
   write-ahead log is checkpointed into the database file when a session ends, so what gets
   backed up carries the training that was actually logged. Treat this as the convenience
   path; the JSON export is the one to rely on.
2. **JSON export / import** via the Storage Access Framework — full fidelity, rebuilds the
   entire database. Import is **atomic**: the file is fully parsed before anything is touched,
   and the wipe plus every insert run in one transaction, so a bad file leaves your log intact.
3. **CSV export** — one row per logged set, joined to exercise and session, for spreadsheet
   analysis. Lossy by design: it drops the program and equipment tables, so it is never a
   restore path.

The backup screen reports what is actually in the database and tells you the outcome of every
export and restore, success or failure. A silent failure there is the worst case — it would
leave you believing a backup exists. Exports are written with truncation, so overwriting a
larger backup with a smaller one cannot leave the old file's tail behind.

Room migrations are always real. There is no destructive fallback anywhere in the build.

---

## Architecture

```
Compose UI  →  ViewModel (StateFlow)  →  Repository  →  Room DAO  →  SQLite
```

Kotlin · Jetpack Compose · Material 3 · Room · Hilt · kotlinx.serialization · Coil.
Single activity, no fragments. Room is the single source of truth; screens observe Flows and
nothing holds duplicate state.

**There is no networking dependency.** No Retrofit, no OkHttp, no Ktor, and the only permission
in the manifest is `VIBRATE`. The app opens no sockets at all. (`COACHING.md` permits one future
exception — an `ACTION_VIEW` hand-off to search for form videos in another app — which is not
built yet.)

```text
app/src/main/java/dev/redplate/
├─ data/          Room entities, DAOs, seeds, PlateMath, ProgramGenerator, backup
├─ onboarding/    Goal → schedule → equipment → plan fork → preset library
├─ today/         The session that's due, volume standing
├─ workout/       Set logging, rest, guidance, body map, session summary
├─ plan/          Week view and program builder
├─ history/       e1RM curves, PRs, per-exercise logs
├─ settings/      Profile, equipment inventory, backup
└─ ui/            Theme, type scale, PlateStack and shared components
```

Roughly 12,000 lines of Kotlin across 74 files, with 34 `@Preview` states.

### Seed data

85 exercises hand-tagged to the equipment in one real gym — movement pattern, primary and
secondary muscles, complexity tier and fatigue cost each. Start/end position stills come from
[`yuhonas/free-exercise-db`](https://github.com/yuhonas/free-exercise-db) (public domain),
trimmed to only the images the app can reference (160 files, 12 MB). `MediaResolver` prefers a
sideloaded set in the app's external files directory, so the media can be swapped with
`adb push` and no rebuild.

---

## Build

**Requirements:** Android Studio (current stable), Android SDK 36, JDK 21.
Compiles against 36, installs on 29 and up.

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:test                 # JVM unit tests — plate maths, progression, the clock
./gradlew :app:connectedAndroidTest # instrumented — seeding, backup round-trip
```

A release build is minified and needs a keystore. Put its details in `local.properties`,
which is not committed; without them the release build is simply unsigned rather than
failing to configure.

```properties
redplate.keystore=/absolute/path/to/redplate.jks
redplate.keystorePassword=…
redplate.keyAlias=redplate
redplate.keyPassword=…
```

Optional, to sideload your own exercise media:

```bash
adb push media/ /sdcard/Android/data/dev.redplate/files/exercises/
# filenames: <exerciseId>_start.jpg / <exerciseId>_end.jpg — .png and .webp also work
```

---

## Design

| Token           | Hex       | Use                                       |
|-----------------|-----------|-------------------------------------------|
| `ground`        | `#101317` | Base — near-black, lifted just off pure    |
| `surface`       | `#1A1E24` | Cards, sheets, raised rows                 |
| `surfaceRaised` | `#242A32` | Steppers, chips, controls on a surface     |
| `line`          | `#2A2F36` | Hairline dividers, borders                 |
| `ink`           | `#F5F5F0` | Primary type                               |
| `inkMuted`      | `#8B939E` | Labels, units, secondary data              |
| `live`          | `#FF5C1A` | Active set, running timer, primary action  |

`live` is the only warm accent and appears in exactly one place per screen. Plate colours are
used solely inside the plate stack. Progression state is never encoded in colour alone — always
paired with an icon or label.

**Type:** IBM Plex Sans Condensed for numerals (tabular figures mandatory), IBM Plex Sans for
UI, IBM Plex Mono for logs. All bundled — loading a font over the network would break the
offline guarantee.

**Motion:** restrained. One orchestrated moment — the plate stack animating as weight changes.
Everything else is a 120 ms fade or nothing.

---

## Scope

**In:** set logging, program generation and progression, equipment-aware exercise selection,
history and PR detection, per-muscle volume tracking, exercise guidance, backup and export.

**Out, deliberately:** social feed, program sharing, hosted video, cloud sync, LLM coaching,
Wear OS. Also out: BMI, body-fat targets, calorie counting, goal weight. This is a training
app. Progress is measured in load, reps and consistency.

Redplate coaches training. It does not diagnose, prescribe rehab, or evaluate anyone's body.
Where an injury is flagged the correct behaviour is to exclude the movement and suggest seeing
a professional.

---

## Project documents

| File | What it governs |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | The contract — non-negotiables, architecture, design system, ergonomics |
| [`COACHING.md`](COACHING.md) | The engine — intake, equipment model, program generation, progression |

If a change conflicts with `CLAUDE.md`, the contract wins — say so before writing the code.

## Contributing

1. Keep it offline-only. Adding a networking dependency means something has gone wrong — stop
   and ask.
2. Never destructive-migrate Room. Write a real migration.
3. Export must keep working. It is the only thing standing between a bug and a lost training log.
4. No dead controls. A button that does nothing is worse than no button.
5. Commit after every green build.

## Licence

Personal project, not distributed. Bundled third-party assets: IBM Plex (OFL 1.1),
`free-exercise-db` imagery (public domain).
