package dev.sharno.due

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DueSnapshotTest {
    private val todo = Todo("todo", "Practise", 1_000, false)
    private val skill = Skill("skill", "Guitar", 0xFFE65100.toInt(), 0)
    private val completion = TaskCompletion("c1", todo.id, "Practise", 0, 1_000, 1_000)

    @Test
    fun linksToMissingRowsAreDropped() {
        val pruned = DueSnapshot(
            todos = listOf(todo),
            skills = listOf(skill),
            taskSkills = listOf(
                TaskSkillLink(todo.id, skill.id),
                TaskSkillLink("ghost-todo", skill.id),
                TaskSkillLink(todo.id, "ghost-skill"),
            ),
        ).prune()

        assertEquals(listOf(TaskSkillLink(todo.id, skill.id)), pruned.taskSkills)
    }

    @Test
    fun ratingsWithoutTheirSessionOrSkillAreDropped() {
        val pruned = DueSnapshot(
            skills = listOf(skill),
            completions = listOf(completion),
            ratings = listOf(
                SkillRating(completion.id, skill.id, 7, 0),
                SkillRating("ghost-completion", skill.id, 7, 0),
                SkillRating(completion.id, "ghost-skill", 7, 0),
            ),
        ).prune()

        assertEquals(1, pruned.ratings.size)
        assertEquals(completion.id, pruned.ratings.single().completionId)
    }

    /** A completion whose task was deleted is normal history, not corruption. */
    @Test
    fun completionsSurviveATaskThatNoLongerExists() {
        val pruned = DueSnapshot(
            todos = emptyList(),
            skills = listOf(skill),
            completions = listOf(completion),
            ratings = listOf(SkillRating(completion.id, skill.id, 9, 0)),
        ).prune()

        assertEquals(listOf(completion), pruned.completions)
        assertEquals(1, pruned.ratings.size)
    }

    @Test
    fun duplicatesAreCollapsed() {
        val pruned = DueSnapshot(
            todos = listOf(todo, todo.copy(title = "Duplicate id")),
            skills = listOf(skill, skill.copy(name = "Duplicate id")),
            taskSkills = listOf(TaskSkillLink(todo.id, skill.id), TaskSkillLink(todo.id, skill.id)),
            completions = listOf(completion, completion.copy(taskTitle = "Duplicate id")),
            ratings = listOf(
                SkillRating(completion.id, skill.id, 3, 0),
                SkillRating(completion.id, skill.id, 8, 0),
            ),
        ).prune()

        assertEquals(1, pruned.todos.size)
        assertEquals(1, pruned.skills.size)
        assertEquals(1, pruned.taskSkills.size)
        assertEquals(1, pruned.completions.size)
        assertEquals(1, pruned.ratings.size)
    }

    @Test
    fun anEmptySnapshotPrunesToNothingWithoutFailing() {
        assertTrue(DueSnapshot().prune().todos.isEmpty())
    }
}
