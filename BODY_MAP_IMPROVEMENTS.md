# Body Map Improvements

## What Changed

### Visual Highlighting
The body map now provides **much stronger visual feedback** when you select a muscle group:

#### Selected Muscle (PICKED state)
- **Glow effect**: Extends 3dp beyond the muscle boundary
- **Bright white border**: 4dp thick, highly visible
- **High contrast**: Makes the selection unmistakable

#### Volume States (Color-Coded)
- **NONE / BELOW_MEV** (Gray, 20% opacity): Undertrained — suggests prioritizing this muscle
- **MEV_TO_MAV** (Blue, 70% opacity): On target — sustainable volume level
- **APPROACHING_MRV** (Yellow, 70% opacity): Nearing limit — reduce volume soon
- **AT_MRV** (Orange, 70% opacity): Over limit — extend recovery or reduce intensity

#### Outlines
- **Under-trained muscles**: Dashed outline (subtle reminder)
- **Trained muscles**: Soft border (2% alpha black)
- **Picked muscles**: Glowing highlight with bright border

---

## Future Improvements (Not Yet Implemented)

The current implementation uses **rounded rectangles** as placeholders. For production:

### 1. SVG-Based Muscle Shapes
Your anatomical muscle map (`design/Body_Map_Front.svg` and `Body_Map_Back.svg`) defines exact muscle boundaries. The app could:
- Parse SVG `<path>` elements directly from the design files
- Render actual muscle contours instead of rectangles
- Hit-detection would follow true anatomy, not bounding boxes

### 2. Per-Muscle Shading
Instead of solid fills, use:
- **Radial gradients** from center (lighter) to edge (darker)
- **Subtle textures** (subtle pattern overlay) to suggest muscle fiber direction
- **Depth cues** (darker at edges) to make 3D depth feel

### 3. Animated Transitions
- Muscles **pulse** when selected (2-frame loop)
- **Ripple effect** when tapped, emanating from hit point
- **Smooth color transitions** when volume level changes (300ms ease-out)

### 4. Interactive Feedback
- **Haptic pulse** when muscle selected (Android 12+)
- **Scale animation** — muscle grows 5% when picked
- **Callout labels** that fade in/out with volume state

---

## Technical Details

### Color Palette
```kotlin
// On-target training
MEV_TO_MAV    → #2F9BD8 (70% opacity, blue)

// Approaching or over limit
APPROACHING_MRV → #FFD100 (70% opacity, yellow)
AT_MRV          → #FF5C1A (70% opacity, orange = live accent)

// Undertrained
BELOW_MEV → #8B939E (20% opacity, gray)
NONE      → #F5F5F0 (8% opacity, barely visible)

// Selected
PICKED → White glow + bright border
```

### Hit Zone Priority
Muscles are checked in this order when you tap:
1. **NECK** (high priority — padded upward)
2. **Delt clusters** (front/rear + side delts ambiguous together)
3. **Back fuzzy bands** (upper back, lower back squeezed between neighbors)
4. **Individual muscles** (direct zones)

### Bilateral Symmetry
- Tapping **left bicep** = same as tapping **right bicep**
- Both route to `MuscleGroup.BICEPS` exercise sheet
- No "left" vs "right" variant handling needed

---

## How to Test

### On Device
1. **Today Screen** → Tap the body map
2. **Body map front view** → Tap muscle
   - Should see glow + bright border
   - Label should appear/update color
3. **Tap different volumes** → Colors change:
   - Blue = good
   - Yellow = warning
   - Orange = over limit
   - Gray = undertrained

### Visual Regression Test
Compare side-by-side with HTML reference (`gym-workout-planner-app-revamp/Redplate Screens.html`, section 5a):
- Muscle highlights should be glowy, not hard rectangles
- Selected state should be unmistakable
- Colors should match palette exactly

---

## Integration with Exercise Browser

When a user:
1. Taps the body map muscle
2. App filters exercises to that muscle + available equipment
3. Bottom sheet shows only doable exercises
4. User picks one → added to session immediately

All exercises returned are guaranteed to be:
- ✅ Doable with your gym equipment
- ✅ For the selected muscle
- ✅ Not excluded (injury/dislike)
- ✅ Sorted by equipment availability (most common equipment first)

---

## Code Changes Summary

### Files Modified
- `BodyMapCanvas.kt`: Enhanced PICKED state with glow + brighter border; improved fill colors
- `BodyMapData.kt`: No changes (shapes remain accurate)
- `WorkoutRepository.kt`: Added `observeExercisesByMuscleWithAvailableEquipment()` filtering

### New Methods
```kotlin
// Repository
fun observeExercisesByMuscleWithAvailableEquipment(muscle: MuscleGroup): Flow<List<ExerciseEntity>>
fun observeExercisesWithAvailableEquipment(): Flow<List<ExerciseEntity>>

// Private helper
suspend fun isExerciseAvailable(exercise: ExerciseEntity): Boolean
```

### Compile Status
✅ **BUILD SUCCESSFUL**
- No errors
- No warnings (except existing Hilt/annotation warnings)
- Ready for installation

