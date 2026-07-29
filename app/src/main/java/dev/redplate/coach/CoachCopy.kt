package dev.redplate.coach

/**
 * The coach's voice, in one place.
 *
 * Every UI string in this app is hardcoded in Kotlin — a defensible trade for a
 * single-user, single-language app, and `strings.xml` would add a layer of indirection
 * over text nobody is going to translate. What is *not* defensible is the coaching lines
 * being scattered across five view models, because tone is the one thing that has to be
 * consistent and the one thing you cannot review when it is spread that thin.
 *
 * So: the lines the app says *as a coach* live here. Labels, units and numbers stay where
 * they are rendered. The test for whether a string belongs in this file is whether
 * changing it would change how the app sounds.
 *
 * Rules the copy holds to, from CLAUDE.md §6:
 * - Empty states are invitations to act, never apologies.
 * - Errors say what happened and how to fix it.
 * - Button labels are active voice and keep the same verb through a flow.
 */
object CoachCopy {

    // ── Today ───────────────────────────────────────────────────────

    object Today {
        const val FIRST_SESSION_HEADLINE = "First one. Go light on purpose."

        const val FIRST_SESSION_BODY =
            "Pick a weight you could manage two more reps with. Today sets the baseline — " +
                "every number after this is built off it."

        const val FIRST_SESSION_VOLUME = "Fills in as you log. Trends need three sessions."

        const val REST_DAY_HEADLINE = "Rest day. You've earned it."

        const val REST_DAY_NOTHING_LEFT = "No more sessions scheduled this week."

        const val UNSCHEDULED_SESSION_HEADLINE = "Not a scheduled day. Trained anyway."

        const val UNSCHEDULED_SESSION_VOLUME = "That work counts toward the week either way."

        const val SAME_PLAN = "Same plan as last time — stay focused on form."

        fun nextSession(label: String) = "Next session is $label."

        fun volumeShort(muscle: String) = "$muscle is light this week — later sessions cover it."

        const val VOLUME_ON_TRACK = "Volume is on track this week."
    }

    // ── Session summary ─────────────────────────────────────────────

    object Summary {
        const val NOTHING_LOGGED_HEADLINE = "Nothing logged this time."

        const val LOGGED_HEADLINE = "Logged. That's the work done."

        fun bestsHeadline(prs: Int) = "Logged, and $prs of those were bests."

        const val NOTHING_LOGGED_BODY =
            "No sets went in, so nothing changes. Start the session again when you're ready."

        const val PR_BODY =
            "New bests are the signal to keep the weight climbing. Next session starts from " +
                "these numbers, not the old ones."

        const val CONSISTENCY_BODY =
            "Consistency is what moves the numbers. This session is now part of what the " +
                "next prescription is built from."

        const val NO_VOLUME_YET =
            "Log a set at 3 reps in reserve or harder and it counts here."

        fun volumeShort(muscle: String) =
            "$muscle is still short of target this week — later sessions cover it."

        const val VOLUME_MET = "Every muscle you trained today is at or above target for the week."
    }

    // ── Setup and failure ───────────────────────────────────────────

    object Setup {
        const val SEEDING_HEADLINE = "Building your exercise library."

        const val SEEDING_BODY =
            "A few hundred movements, written to the phone once. This only happens on the " +
                "first launch."

        const val SEED_FAILED_HEADLINE = "The exercise library didn't load."

        /** Says what happened and what to do about it, per CLAUDE.md §6. */
        const val SEED_FAILED_BODY =
            "The exercise library didn't load, so there's nothing to build a plan from yet. " +
                "Try again — if it keeps failing, reinstalling rebuilds the library and " +
                "leaves your training history alone."

        const val SEED_RETRY = "Try again"
    }

    // ── Changing the plan ───────────────────────────────────────────

    object Plan {
        const val HEADLINE = "Change your mind."

        const val INTRO =
            "These are the answers your program is built from. Nothing changes until you " +
                "confirm at the bottom."

        const val NO_PROFILE = "Finish setting up and your plan settings appear here."

        const val REBUILD_WARNING =
            "Changing this rebuilds the rest of your block. Your logged sessions and PRs are " +
                "kept — the plan ahead of you changes. Every lift starts at the weight you " +
                "last used it for."

        const val REBUILT =
            "Rebuilt. Your logged sessions and PRs are untouched, and every lift kept the " +
                "weight you last used."

        const val SETTINGS_ONLY = "Saved. Your block is unchanged."

        const val ALREADY_FITTED = "Saved. Every session already fitted."

        /**
         * Says what moved and what was re-shaped, because "saved" on its own is what let
         * a weekday change look like it had worked when nothing had been rescheduled.
         */
        fun adjusted(daysMoved: Int, templatesRefitted: Int): String {
            val moved = when (daysMoved) {
                0 -> null
                1 -> "one session moved to the day you picked"
                else -> "$daysMoved sessions moved to the days you picked"
            }
            val refitted = when (templatesRefitted) {
                0 -> null
                1 -> "one session was re-fitted to the new length"
                else -> "$templatesRefitted sessions were re-fitted to the new length"
            }
            val clauses = listOfNotNull(moved, refitted)
            if (clauses.isEmpty()) return ALREADY_FITTED
            return "Saved. ${clauses.joinToString(" and ").replaceFirstChar { it.uppercaseChar() }}."
        }

        const val NO_PROFILE_TO_CHANGE = "There's no profile to change yet."

        const val STARTS_TODAY = "Your block starts today."

        fun startsOn(date: java.time.LocalDate): String {
            val day = date.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.FULL,
                java.util.Locale.getDefault(),
            )
            return "Your block starts $day ${date.dayOfMonth} " +
                date.month.getDisplayName(
                    java.time.format.TextStyle.FULL,
                    java.util.Locale.getDefault(),
                ) + "."
        }

        fun weekStartsOn(weekdayIndex: Int): String {
            val day = java.time.DayOfWeek.of(weekdayIndex + 1).getDisplayName(
                java.time.format.TextStyle.FULL,
                java.util.Locale.getDefault(),
            )
            return "Your week now runs from $day."
        }
    }
}
