package dev.sharno.due.ui

/**
 * The app's destinations.
 *
 * Deliberately an enum switched on in [DueApp] rather than a navigation library: there are no deep
 * links, no nested graphs and no route arguments, so a `when` plus `BackHandler` covers it in a
 * fraction of the code. Enums are `Serializable`, which is what lets `rememberSaveable` keep the
 * current screen across rotation and process death.
 */
internal enum class DueScreen {
    TASKS,
    SETTINGS,
    APPEARANCE,
    SKILLS,
    SKILL_STATS,
}
