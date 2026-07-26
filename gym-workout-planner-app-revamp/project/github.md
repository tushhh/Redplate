repo: tushhh/Redplate
branch: master

## Last sync
date: 2026-07-25T00:00:00Z

### Updated in this project
- Round 9 closes out round 8's backlog: You/settings, backup & export, stall-and-deload state, day-one empty Today.
- Backup screen states last-backup time, size and session count; restore merges by date.
- Deload state shows six weeks of e1RM plus the deload in kilos, with swap and push-on alternatives.
- Round 10 moved the full 11-group balance chart below the fold on the Plan tab, with 4-week-average ticks and a settled over-cap bar state.
- Finalised: retired the unchosen direction studies, body-map artwork explorations and the static round-7 screens their animated versions replaced. 25 screens remain, all current.

### Updated in this project
- Read the spec set (CLAUDE.md, COACHING.md, SCREENS.md), theme files, set-logging screen and plate stack.
- Copied design/Body Map Front.svg + Back.svg and used the front geometry for the muscle picker.
- Designed 15 revamped screens across three rounds in the "Coach" direction.
- Preset plan doses grounded in the ACSM 2026 resistance-training position stand.
- Added animation windows on the set, guidance, browser and swap screens (drop targets, no bundled media).
- Media source settled on wger (CC BY-SA): round 8 windows use real wger start/end stills, cross-faded.
- Checked ExerciseDB/exercisedb-api and wger-project/wger for bundled media: neither repo ships images.

## Screen map
| Project screen | Repo files |
|---|---|
| Redplate Plan.dc.html | CLAUDE.md, COACHING.md, SCREENS.md, ui/theme/Color.kt, ui/theme/Type.kt, ui/components/PlateStack.kt, workout/SetLoggingScreen.kt, MainActivity.kt |
| Redplate Screens.dc.html — 1a/1b/1c set screens | workout/SetLoggingScreen.kt, workout/SetLoggingUiState.kt, ui/components/PlateStack.kt, ui/theme/Color.kt, ui/theme/Type.kt |
| Redplate Screens.dc.html — 2a/2b Today + rest | workout/SetLoggingUiState.kt (RestState), COACHING.md §3–4, data/VolumeDao.kt |
| Redplate Screens.dc.html — 2c/2d/2e intake | COACHING.md §1–2, data/GymEquipmentSeed.kt |
| Redplate Screens.dc.html — 3a/3b plan choice + presets | COACHING.md §3, ACSM 2026 position stand (external) |
| Redplate Screens.dc.html — 3c/3d body map + generated session | design/Body Map Front.svg, SCREENS.md, COACHING.md §2 |
| Redplate Screens.dc.html — 9a/9b settings + backup | SCREENS.md, data/GymEquipmentSeed.kt, ui/components/PlateStack.kt |
| Redplate Screens.dc.html — 9c/9d deload + first run | COACHING.md §3–5, data/VolumeDao.kt |
| Redplate Screens.dc.html — 10a/10b balance chart | data/VolumeDao.kt, COACHING.md §4, ACSM 2026 position stand (external) |
