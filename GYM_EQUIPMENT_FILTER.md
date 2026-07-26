# Equipment-Based Exercise Filtering

## Overview
Your Redplate app now intelligently filters exercises to **only show those you can perform with the equipment in your gym**. This is the differentiator that makes the coaching engine work — no more "hack squat" suggestions when you don't have a hack squat machine.

## Your Equipment

Your gym contains **29 items** across 5 categories:

### Cardio Machines (6)
1. Stairmill
2. Treadmill
3. Crosstrainer
4. Concept2 Rower
5. Airbike
6. SkiErg

### Pin-Stack Selectorized Machines (7)
7. Dual Adjustable Pulley
8. Hip Adductor/Abductor
9. Pec Fly / Rear Delt
10. Chest Press Machine
11. Shoulder Press Machine
12. Leg Curl Machine
13. 4-Station Multi-Gym

### Benches & Fixtures (5)
14. Deadlift Platform
15. Half Racks (Power Racks)
16. Decline Bench
17. Flat/Incline Bench
18. Back Extension Bench

### Plate-Loaded Machines (3)
19. Incline Chest Press Machine
20. Leg Press
21. Glute Drive

### Free Weights & Racks (8)
22. Smith Machine
23. Dumbbells (2.5–50kg, 2.5kg increments)
24. Barbell (Olympic, 20kg bar)
25. Bumper Plates
26. Power Sled
27. Kettlebells (Rox Zone) — *requires inventory confirmation*
28. Resistance Bands (Rox Zone) — *requires inventory confirmation*
29. Wall Ball Target (Rox Zone) — *requires inventory confirmation*

## How Filtering Works

### Repository Methods

**`observeExercisesWithAvailableEquipment(): Flow<List<ExerciseEntity>>`**
- Returns all exercises in the database that can be performed with available equipment
- Filters out exercises requiring equipment you don't have
- Updates in real-time if equipment availability changes

**`observeExercisesByMuscleWithAvailableEquipment(muscle: MuscleGroup): Flow<List<ExerciseEntity>>`**
- Returns only exercises for a specific muscle group that you can actually perform
- Used by the body map when selecting a muscle
- Ensures the Exercise Browser never suggests impossible substitutes

### Exercise Availability Logic

An exercise is considered **available** if:
1. It requires no equipment (bodyweight exercises), OR
2. At least one of its required equipment items is available in your gym AND marked `isAvailable = true`

### Safe-Close Design

Equipment marked `isAvailable = false` is deliberately excluded from the filter:
- **Kettlebells, Bands, Wall Ball** (Rox Zone items) — until you confirm exact inventory
- This prevents the app suggesting exercises that might not actually be doable

To enable these, update `GymEquipmentSeed.kt` and confirm:
- Kettlebell sizes and count
- Band resistances and count
- Wall ball weight(s)

## Integration Points

### Today Screen (`TodayScreen.kt`)
- Exercise suggestions are now filtered to available equipment only
- Shows only variants you can actually perform

### Exercise Browser (`ExerciseBrowserScreen.kt`)
- Tier 2 (body map) → Exercise list is filtered by muscle + available equipment
- "Available equipment" filter chip is **ON by default** and respected

### History Screen (`HistoryScreen.kt`)
- Historical exercises are shown; new suggestions respect equipment constraints

## Usage Notes

### For Development
```kotlin
// In a ViewModel or Composable:
@Inject val repository: WorkoutRepository

// Get filtered exercises for a muscle
val exercisesForMuscle = repository.observeExercisesByMuscleWithAvailableEquipment(MuscleGroup.CHEST)

// Get all available exercises
val allAvailable = repository.observeExercisesWithAvailableEquipment()
```

### Future Expansion
To add new equipment:
1. Add an `EquipmentEntity` to `GymEquipmentSeed.seed()`
2. Map exercises in `GymEquipmentSeed.looseEquipmentMapping` or hand-tag them
3. Set `isAvailable = true` when confirmed
4. Exercises requiring that equipment will immediately appear in the browser

## Equipment Assumptions & TODOs

### HIGH-STAKES (confirm against hardware)
- **Dumbbells**: Assumed 2.5–50kg, 2.5kg increments
- **Barbell plates**: Assumed commercial pool (25/20/15/10/5/2.5/1.25 kg)
- **Smith Machine**: Counterbalanced weight is NOT zero — check console
- **Pin-stack increments**: Assumed 2.5kg, 5–100kg range

### FAIL-CLOSED (intentionally disabled)
- **Kettlebells**: No inventory listed → `isAvailable = false`
- **Bands**: No inventory listed → `isAvailable = false`
- **Wall Ball**: Only the target shown → `isAvailable = false`

### Next Step
Walk through each machine console and confirm the assumptions. Once verified, these can be manually enabled in the seed or a GYM.md document can be created to codify them for reproducibility.

