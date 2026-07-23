# Redplate — Coaching Engine Spec

Companion to `CLAUDE.md`. Defines how the app acts as a coach: what it asks, what it prescribes,
and how it adapts. Everything here runs **locally and deterministically**. No LLM, no network.

The engine must always be able to answer "why am I doing this?" in plain language. A prescription
the user can't interrogate is a black box, and black boxes are what they're paying other apps for.

---

## 1. Intake

Ask only for inputs that change a prescription. Everything below has a job; anything not listed
here does not get asked.

### Required
| Field | Why it changes the plan |
|---|---|
| Training age (months of consistent lifting) | Drives starting volume, progression rate, exercise complexity |
| Days per week available (2–6) | Determines split structure and per-session volume |
| Session length ceiling (30/45/60/75/90+ min) | Caps exercises per session and rest budget |
| Primary goal (strength / hypertrophy / general) | Sets rep ranges, intensity, rest, volume distribution |
| Bodyweight | Loading for bodyweight movements, e1RM context, progression increments |
| Equipment inventory | Filters the entire exercise pool (see §2) |

### Optional, high value
| Field | Use |
|---|---|
| Known 1RMs or recent top sets | Seeds starting loads instead of a ramp-up week |
| Injury / movement restrictions (free text + common toggles) | Hard-excludes exercises and patterns |
| Muscle priorities (up to 2) | Specialization: priority muscles run toward MRV, others held at MEV |
| Preferred / disliked exercises | Selection weighting |

### Deliberately NOT collected
- **Height.** It does not change any prescription in this engine. Do not ask for it.
- **Body-fat %, goal weight, calorie targets, BMI.** This is a training app, not a diet app.
  Do not display BMI, do not set weight-loss targets, do not gamify body composition.
  Progress is measured in load, reps and consistency.
- Age and sex are optional and used only to widen default recovery assumptions. Never used to
  scale down prescriptions by default.

### Readiness screening
Before the first generated plan, show a short screen: chest pain, dizziness, joint injury,
recent surgery, pregnancy, medically supervised conditions. If any are flagged, the app still
works, but the plan opens with a plain, non-alarming note recommending clearance from a doctor
or physio, and heavy loading (<5 rep work) is not auto-prescribed. This is a one-time screen,
not a recurring nag.

---

## 2. Equipment model

The differentiator most apps get wrong. Do not model equipment as a flat checkbox list.

```kotlin
data class Equipment(
    val id: String,
    val category: EquipmentCategory,   // BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND...
    val loadingScheme: LoadingScheme,  // PLATE_LOADED, FIXED_INCREMENT, PIN_STACK, BODYWEIGHT
    val availableLoads: List<Double>,  // actual dumbbells / stack increments the user owns
    val minIncrement: Double
)
```

Why this matters: if the user's dumbbells jump 2.5 kg → 5 kg → 7.5 kg, the engine must never
prescribe 6 kg, and progression must step by what actually exists. If their cable stack moves in
5 kg pins, double progression has to account for a jump that may be 15% of the working load.
**This is the single most common failure in generated programs and the thing that will make your
app feel smarter than paid ones.**

### Exercise selection
Each exercise carries: movement pattern, primary muscles, secondary muscles (0.5 set credit),
required equipment set, complexity tier, and fatigue cost.

Selection is a constrained pick:
1. Filter pool by available equipment ∩ non-excluded (injury, disliked)
2. Cover every required movement pattern for the split
3. Prefer compound-first ordering within a session
4. Respect complexity tier vs. training age
5. Every exercise must have ranked substitutes computed from the same equipment set — the user
   must always be able to swap when a rack is occupied, without leaving the session

---

## 3. Program generation

### Split selection by days/week
| Days | Structure |
|---|---|
| 2 | Full body A/B |
| 3 | Full body A/B/C, or Push/Pull/Legs |
| 4 | Upper/Lower ×2 |
| 5 | Upper/Lower/Push/Pull/Legs |
| 6 | PPL ×2 |

Frequency target: each muscle trained **2× per week minimum**. Volume is distributed across
sessions, never dumped into one.

### Volume landmarks (weekly hard sets per muscle)
Defaults, adjusted by training age and then by observed response:

| Muscle | MV | MEV | MAV | MRV (start) |
|---|---|---|---|---|
| Chest | 6 | 8 | 12–18 | 20 |
| Back | 8 | 10 | 14–20 | 24 |
| Quads | 6 | 8 | 12–18 | 20 |
| Hamstrings | 4 | 6 | 10–16 | 18 |
| Glutes | 4 | 6 | 10–16 | 18 |
| Shoulders (side/rear) | 6 | 8 | 12–20 | 24 |
| Biceps | 4 | 6 | 10–16 | 20 |
| Triceps | 4 | 6 | 10–16 | 20 |
| Calves | 6 | 8 | 12–16 | 20 |
| Abs | 0 | 4 | 8–16 | 20 |

Beginners (<12 months): start at MEV, progress slowly, cap at low MAV.
A set counts toward volume only if logged at **0–3 RIR**. Secondary muscles get 0.5 credit.

### Mesocycle structure
- Block length 4–6 weeks: week 1 at MEV, add 1–2 sets per muscle per week, approach MRV in the
  final week, then a deload week at MV with load reduced ~10% and RIR raised to 4–5.
- Deload is triggered by whichever comes first: end of block, two consecutive sessions of
  performance regression on a primary lift, or sustained recovery-flag input.

### Rep ranges and rest by goal
| Goal | Compounds | Isolation | Rest (compound) | Rest (isolation) |
|---|---|---|---|---|
| Strength | 3–6 @ 1–3 RIR | 6–10 | 180–300 s | 90–120 s |
| Hypertrophy | 6–10 @ 1–3 RIR | 10–15 | 120–180 s | 60–120 s |
| General | 5–10 | 10–15 | 120–180 s | 60–90 s |

Rest timer auto-starts on set completion, pre-loaded with the prescribed interval and freely
adjustable. Evidence basis: rests under 60 s consistently blunt strength gains and reduce load
maintained across sets; beyond ~3 min the returns for hypertrophy are marginal.

---

## 4. In-session coaching

The app tells the user what to do, one thing at a time.

- **Current set is the whole screen.** Exercise, target load, target reps, target RIR, set N of M.
- **Prescribed load comes from history**, not a formula: last session's performance on this
  exercise, adjusted by the progression rule.
- On set completion → haptic → rest timer auto-starts at the prescribed interval → next set is
  pre-filled → timer end fires haptic + optional sound.
- **Deviation is first-class.** If the user logs 6 reps against a target of 10, the engine adjusts
  the next set immediately and says why in one line ("Dropping to 60 kg — last set fell 4 reps short").
- **Swap must be one tap** from the set screen, showing equipment-valid substitutes ranked by
  muscle overlap.
- Between exercises: a single line naming what's next and why it's there.

### Exercise guidance ("how do I do this?")

Machine exercises have a diagram on the machine. Free-weight work has nothing, which is exactly
where a coach is needed and where most trackers give up.

**Trigger**
- Auto-opens the first time an exercise is ever programmed for the user. Once. Never again
  unless requested — flag it on the exercise row (`hasBeenIntroduced`).
- Thereafter reachable from a guidance affordance beside the exercise name.

**Placement — the one sanctioned exception to CLAUDE.md §4**
The guidance button sits in the **read-only top zone**, next to the exercise name, at 64 dp.
This is deliberate: it requires a grip shift to reach, which prevents accidental taps mid-set.
Guidance is a pre-set decision, never an in-set one. Do not place it in the bottom control zone.

**Presentation — bottom sheet, never a centred dialog**
On a 162 mm device a centred dialog puts its content *and* its dismiss control above the thumb
arc. The sheet rises from the bottom edge, is dismissed by a downward swipe or a full-width
close bar, and is scrollable one-handed.

**Content, in order**
1. Exercise name, primary and secondary muscles
2. Step-by-step text from the bundled dataset
3. Start/end position images, if bundled for this exercise
4. The user's own attached form video, if one exists
5. "Watch form videos" — launches an external intent (see below)
6. Equipment-valid substitutes, one tap to swap

**Media architecture — decoupled from source**

This is a personal, sideloaded build that is never distributed, so media provenance is the
user's call. The app must therefore be **agnostic about where media comes from**:

- Media is **not bundled in the APK**. It lives in the app's external files directory,
  populated once via `adb push`. Keeps the APK a few MB, keeps Gradle loops fast while the
  agent is iterating, keeps binaries out of git, and makes swapping the media set a file copy
  rather than a rebuild.
- Lookup is by convention: `<mediaDir>/<exerciseId>.webp` (animation),
  `<exerciseId>_start.jpg` / `<exerciseId>_end.jpg` (stills). Missing files are normal —
  the sheet degrades to text-only without an error state.
- **Prefer animated WebP or short muted MP4 over GIF.** GIF is a very inefficient container;
  the same animation as WebP is typically 60–90% smaller, MP4 smaller again. Coil renders
  animated WebP, ExoPlayer renders MP4.
- A `MediaResolver` interface sits between the UI and the filesystem so the source can change
  without touching the guidance sheet.

If the project is ever distributed, the bundled media set becomes the thing to revisit —
which the decoupling above makes a swap rather than a rewrite. Text instructions from
`free-exercise-db` are public domain and are safe to bundle in either case.

**External video — the single network exception**
A "watch form videos" action may fire an `ACTION_VIEW` intent to a search URL for the exercise
name. This launches another app; Redplate itself still makes no network calls and every other
feature works in airplane mode. The button must be visibly an external hand-off, and must
degrade silently if no handler exists.

### Progression rules (per exercise, user-overridable)
- **Double progression** (default for hypertrophy): hit top of rep range on all sets at ≤2 RIR →
  add the smallest increment the equipment allows → reset to bottom of range.
- **Load progression** (default for strength compounds): fixed increment per session while
  RIR target is met; two consecutive misses → 10% deload on that lift, ramp back.
- **RIR autoregulation:** working load is scaled by the gap between prescribed and reported RIR.
- **Plateau detection:** no e1RM improvement across 3 consecutive sessions → flag, offer a swap,
  a load deload, or a volume adjustment. Do not silently keep prescribing the same thing.

---

## 5. Data sources to pull from

**Use (data):**
- `yuhonas/free-exercise-db` — ~800 exercises, public domain, JSON, equipment + muscle fields.
  Primary seed.
- Secondary datasets with animated GIFs and 6-language instructions exist on GitHub — evaluate
  licence carefully before bundling; only include if the licence permits redistribution.

**Read for structure, do not copy (logic):**
- Open-source equipment-aware generators that filter an annotated exercise pool by available
  equipment per location — the right architectural shape, but far simpler than what's specced here.

**Do not use:**
- Any generator that calls an LLM API to produce the plan. It breaks the $0 constraint, breaks
  offline operation, and produces non-reproducible prescriptions that can't be explained or
  debugged. The engine in this document is deterministic by design.

**Licence discipline:** every bundled dataset needs its licence recorded in `licenses/` and
surfaced in an in-app attributions screen, even for a personal build.

---

## 6. Scope boundary

The app coaches training. It does not diagnose, does not prescribe rehab for injuries, does not
give nutrition targets, and does not evaluate anyone's body. Where a user flags pain or injury,
the correct behaviour is to exclude the affected movements and suggest seeing a professional —
not to program around it cleverly.