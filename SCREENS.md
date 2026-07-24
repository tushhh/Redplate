# Redplate — Screen Specs

Companion to `CLAUDE.md` (design system, ergonomics) and `COACHING.md` (engine).
This file defines **what each screen is**. Without it, screens default to flat lists.

All layout obeys CLAUDE.md §4: read-only above 370 dp, controls below 490 dp,
64 dp minimum targets, 88 dp full-width primary action, portrait only.

---

## Screen inventory

| Screen | Purpose | Status |
|---|---|---|
| Active Session | Log the current set | built |
| **Exercise Browser** | Find and pick an exercise | **flat list — rebuild** |
| Exercise Guidance | How to perform it | not built |
| Session Overview | Today's plan, all slots | not built |
| History | Past sessions, PRs, charts | not built |
| Program Builder | Edit mesocycle | not built |
| Intake | Profile + equipment | not built |

---

## Exercise Browser — full spec

**The problem:** 873 seeded exercises. A flat list is unusable. In practice a user rotates
through 20–40 exercises; the rest is an archive they search, not a catalogue they scroll.

### Structure — three tiers, not one list

**Tier 1 — "Yours" (default view, no scrolling required)**
Opens here. Never shows the full catalogue first.
- **In this session** — slots from today's template, if a session is active
- **Frequent** — top 12 by set count in the last 90 days
- **Recent** — last 8 distinct exercises logged
Rendered as a 2-column card grid. If the user has no history, fall back to Tier 2.

**Tier 2 — Body map (browse)**
An anatomical front/back SVG with tappable muscle regions. This is the signature
interaction of the screen — the equivalent of the plate stack on the logging screen.
- Front/back toggle, 64 dp, bottom-right of the map
- **Visual regions: 16, fine-grained** (per-muscle) — confirmed via granularity test and
  verified against the actual exported geometry (`Body_Map_Front.svg` / `Body_Map_Back.svg`,
  viewBox 280×560, one named `<path>` per MuscleGroup). Raw gap analysis between every
  region and its nearest different-muscle neighbor shows the real picture is better than
  the abstract test implied: **most regions reach 64dp through padded hit-rects alone**;
  only a handful of genuinely tight clusters need the two-stage popover.

  | Verdict | Regions | Technique |
  |---|---|---|
  | **Passes natively** | CHEST (88×64), ABS (64×88) | None needed — both sit exactly at the 64dp floor with zero margin; add a few dp of buffer in the polish pass regardless. |
  | **Padding-rescue** | NECK, BICEPS, FOREARMS, QUADS, CALVES, LATS, TRICEPS, HAMSTRINGS, TRAPS | Hit-rect padded outward from the visual shape into genuine open canvas — never into a neighbor's own visible area. Each has a specific unblocked direction (see below). Single tap, no UI change. |
  | **Two-stage cluster** | {FRONT_DELTS, SIDE_DELTS} · {REAR_DELTS, SIDE_DELTS} · {QUADS↔ADDUCTORS midline} · {TRAPS↔UPPER_BACK↔LATS seam} · {LOWER_BACK↔GLUTES seam} | Neighbors touch at 0–2dp gap on the only sides that could fix the deficit. First tap opens an anchored 2–3 chip popover. |
  | **Cannot be rescued** | OBLIQUES (16dp wide, blocked by ABS at 2dp and CHEST at 8dp on every side — even claiming 100% of both gaps only reaches ~42dp) | Not a direct body-map target. Reachable via search and the "Yours" tier; exposed as a normal TalkBack action regardless. |

  **Padding directions** (the specific unblocked side each region grows into):
  - NECK, TRAPS — upward, into open canvas above the collar (TRAPS' padded zone will
    geometrically overlap NECK's own — resolve with **priority-order hit-testing**: check
    NECK's zone first, since it's the more specific target; fall through to TRAPS otherwise)
  - BICEPS, FOREARMS (both views), TRICEPS — laterally, toward the canvas edge
  - QUADS, CALVES (both views), HAMSTRINGS — outward only, away from the body midline
    (the midline side is where their tight neighbor sits — never pad into it)
  - LATS — inward, toward the spine gap (the ~24dp gap between left and right lats)

  **The QUADS/ADDUCTORS case** doesn't need a separate cluster trigger. QUADS resolves
  standalone by padding outward; ADDUCTORS is sandwiched at 0dp on both sides with no
  legitimate padding direction at all. Rather than a distinct popover zone, split QUADS'
  own (already-enlarged) hit-rect by tap position: taps in the outer majority go straight
  to the Quads sheet; taps within ~20dp of the body midline trigger a 2-chip
  "Quads / Adductors" popover instead. Same physical zone, position-dependent behaviour.

  **The UPPER_BACK and LOWER_BACK cases** are truly boxed in on both sides (2dp gaps to
  TRAPS/LATS and LATS/GLUTES respectively) even after TRAPS, LATS, GLUTES and HAMSTRINGS
  each resolve independently elsewhere. Treat these as narrow **fuzzy trigger bands**: a
  tap anywhere in that visible strip is inherently ambiguous between the 2–3 nearby
  muscles at real thumb width, so the popover is the correct accommodation rather than a
  fallback — the trigger zone doesn't itself need to hit 64dp, because every plausible
  tap in that neighbourhood produces the same helpful outcome.

  **Bilateral pairs share one destination, not two.** MuscleGroup has no left/right
  variant, so tapping either side of a symmetric pair (e.g. either bicep) already routes
  to the same exercise sheet. Popovers for clustered pairs never need "left" vs "right"
  chips — only the distinct muscles (e.g. "Front Delts / Side Delts"), which keeps every
  popover to 2–3 options rather than 4.
- **Accessibility is independent of hit-zone merging.** All 16 muscles are exposed as
  discrete TalkBack actions on the map itself — a screen-reader user must never be
  routed through the two-stage popover just because the region is visually thin.
- Regions are **shaded by trained volume this week**, using the calibrated palette:
  unfilled = below MEV, green = MEV–MAV, yellow = approaching MRV, red = at/over MRV.
  This turns navigation into a status display — the user sees what is undertrained
  *while* choosing what to train. No other free app does this.
- Tap a region → bottom sheet of exercises for that muscle, sorted by equipment
  availability then by user history

**Tier 3 — Search (the archive)**
Persistent search field pinned in the bottom control zone, never at the top —
a top-anchored search bar on a 162 mm phone is out of thumb reach.
- Fuzzy match on name and muscle
- Results as the same card grid
- Empty state offers "create custom exercise", never an apology

### Filter chips
Single horizontal scrolling row directly above the primary action, 64 dp tall:
`Available equipment` (default ON) · `Compound` · `Isolation` · modality chips
(`Lifting`, `Cardio`, `Bodyweight`, `Mobility`).

"Available equipment" defaults ON and is the most important filter in the app — it is
what stops the browser suggesting a hack squat the user's gym does not have.

### Cards
2-column grid, each card:
- Start-position image, 1:1, `surface` background, subtle `line` border
- Name, 2 lines max, `exerciseName` style
- Equipment chip, `label` style, `inkMuted`
- If unavailable with current equipment: 40% opacity + "no equipment" tag,
  sorted last, **not hidden** — the user may want to substitute or note it for later
- Tap → guidance sheet. Long-press → add directly to session (with a visible
  "Add" affordance too; no gesture-only actions)

### Interaction rules
- Grid scroll position and active filters survive navigation and process death
- Selecting an exercise returns to the session immediately — never a confirm dialog
- Body map region shading recomputes from `VolumeSnapshotEntity` on resume

---

## Media

Card and guidance imagery uses the `images` array already present in `exercises.json`
(`<Exercise_Id>/0.jpg` = start, `/1.jpg` = end).

Per CLAUDE.md, media is **not bundled in the APK**. It lives in the app's external
files directory, pushed with `adb`, resolved through `MediaResolver` by convention:

```
<mediaDir>/exercises/<Exercise_Id>/0.jpg
<mediaDir>/exercises/<Exercise_Id>/1.jpg
```

Missing files are normal. Cards fall back to a muscle-group glyph on `surface` —
never a broken-image icon, never a spinner that never resolves.

Use Coil with an explicit memory/disk cache; a 2-column grid over 873 items will
thrash without one.
