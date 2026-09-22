package dev.sharno.due

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillStatsTest {
    private val guitar = Skill("guitar", "Guitar", 0xFFE65100.toInt(), 0)
    private val focus = Skill("focus", "Focus", 0xFF1565C0.toInt(), 0)

    private fun completion(id: String, todoId: String, atMillis: Long, prompted: Long? = atMillis) =
        TaskCompletion(id, todoId, "Practise", 0, atMillis, prompted)

    private fun rating(completionId: String, skillId: String, value: Int, atMillis: Long = 0) =
        SkillRating(completionId, skillId, value, atMillis)

    @Test
    fun averageBestAndWorstComeFromTheRatings() {
        val graph = SkillGraph(
            skills = listOf(guitar),
            links = listOf(TaskSkillLink("todo", guitar.id)),
            completions = listOf(
                completion("c1", "todo", 100),
                completion("c2", "todo", 200),
                completion("c3", "todo", 300),
            ),
            ratings = listOf(
                rating("c1", guitar.id, 4),
                rating("c2", guitar.id, 8),
                rating("c3", guitar.id, 6),
            ),
        )

        val stats = SkillStatsCalculator.summarise(graph).single()
        assertEquals(3, stats.ratedSessions)
        assertEquals(6.0, stats.average!!, 0.0001)
        assertEquals(8, stats.best)
        assertEquals(4, stats.worst)
        assertEquals(300L, stats.lastPractisedAtMillis)
        assertEquals(listOf(4, 8, 6), stats.recent)
    }

    @Test
    fun aSkillWithNoRatingsReportsNullsRatherThanCrashing() {
        val stats = SkillStatsCalculator.summarise(SkillGraph(skills = listOf(guitar))).single()
        assertEquals(0, stats.ratedSessions)
        assertNull(stats.average)
        assertNull(stats.best)
        assertNull(stats.trend)
        assertNull(stats.lastPractisedAtMillis)
        assertTrue(stats.recent.isEmpty())
    }

    @Test
    fun asingleRatingHasAnAverageButNoTrend() {
        val graph = SkillGraph(
            skills = listOf(guitar),
            completions = listOf(completion("c1", "todo", 100)),
            ratings = listOf(rating("c1", guitar.id, 7)),
        )
        val stats = SkillStatsCalculator.summarise(graph).single()
        assertEquals(7.0, stats.average!!, 0.0001)
        assertNull(stats.trend)
    }

    @Test
    fun trendComparesTheLatestSessionsAgainstTheOnesBefore() {
        val ratings = listOf(3, 3, 3, 6, 6, 6)
        val graph = SkillGraph(
            skills = listOf(guitar),
            completions = ratings.indices.map { completion("c$it", "todo", it * 100L) },
            ratings = ratings.mapIndexed { index, value -> rating("c$index", guitar.id, value) },
        )
        assertEquals(3.0, SkillStatsCalculator.summarise(graph).single().trend!!, 0.0001)
    }

    /** The regression test for "deleting a task keeps its ratings". */
    @Test
    fun ratingsOutliveTheTaskTheyCameFrom() {
        val graph = SkillGraph(
            skills = listOf(guitar),
            links = emptyList(), // the task was deleted, so its links cascaded away
            completions = listOf(completion("c1", "deleted-todo", 100)),
            ratings = listOf(rating("c1", guitar.id, 9)),
        )
        val stats = SkillStatsCalculator.summarise(graph).single()
        assertEquals(1, stats.ratedSessions)
        assertEquals(9.0, stats.average!!, 0.0001)
    }

    @Test
    fun aLateRatingIsOrderedBySessionTimeNotByWhenItWasTyped() {
        val graph = SkillGraph(
            skills = listOf(guitar),
            completions = listOf(
                completion("early", "todo", 100),
                completion("late", "todo", 200),
            ),
            ratings = listOf(
                // The earlier session was rated last, long after the later one.
                rating("early", guitar.id, 2, atMillis = 9_000),
                rating("late", guitar.id, 9, atMillis = 300),
            ),
        )
        assertEquals(listOf(2, 9), SkillStatsCalculator.summarise(graph).single().recent)
    }

    @Test
    fun sessionsWithoutARatingAreCountedSeparately() {
        val graph = SkillGraph(
            skills = listOf(guitar),
            links = listOf(TaskSkillLink("todo", guitar.id)),
            completions = listOf(
                completion("c1", "todo", 100),
                completion("c2", "todo", 200, prompted = null),
            ),
            ratings = listOf(rating("c1", guitar.id, 5)),
        )
        val stats = SkillStatsCalculator.summarise(graph).single()
        assertEquals(1, stats.ratedSessions)
        assertEquals(1, stats.unratedSessions)
        assertEquals(1, graph.unratedCompletions().size)
        assertEquals("c2", graph.unratedCompletions().single().id)
    }

    @Test
    fun aSkippedSessionDoesNotNagButAnUnpromptedOneDoes() {
        val graph = SkillGraph(
            skills = listOf(guitar),
            completions = listOf(
                completion("skipped", "todo", 100, prompted = 100), // sheet shown, user skipped
                completion("notification", "todo", 200, prompted = null), // completed with no UI
            ),
        )
        assertEquals(listOf("notification"), graph.unratedCompletions().map(TaskCompletion::id))
    }

    @Test
    fun eachSkillIsSummarisedIndependently() {
        val graph = SkillGraph(
            skills = listOf(guitar, focus),
            links = listOf(TaskSkillLink("todo", guitar.id), TaskSkillLink("todo", focus.id)),
            completions = listOf(completion("c1", "todo", 100)),
            ratings = listOf(rating("c1", guitar.id, 10), rating("c1", focus.id, 2)),
        )
        val byName = SkillStatsCalculator.summarise(graph).associateBy { it.skill.name }
        assertEquals(10.0, byName.getValue("Guitar").average!!, 0.0001)
        assertEquals(2.0, byName.getValue("Focus").average!!, 0.0001)
        assertEquals(1, byName.getValue("Guitar").taskCount)
    }

    @Test
    fun skillsAreGroupedBackOntoTheirTasks() {
        val graph = SkillGraph(
            skills = listOf(guitar, focus),
            links = listOf(TaskSkillLink("todo", focus.id), TaskSkillLink("todo", guitar.id)),
        )
        assertEquals(listOf("Focus", "Guitar"), graph.skillsByTodoId().getValue("todo").map(Skill::name))
    }

    @Test
    fun ratingRangeIsEnforced() {
        for (invalid in listOf(0, 11, -1)) {
            runCatching { SkillRating("c", "s", invalid, 0) }
                .onSuccess { throw AssertionError("rating $invalid should have been rejected") }
        }
        SkillRating("c", "s", 1, 0)
        SkillRating("c", "s", 10, 0)
    }

    @Test
    fun skillNamesAreNormalizedForUniqueness() {
        assertEquals("guitar practice", Skill.normalizeName("  Guitar   Practice "))
        assertEquals(Skill.normalizeName("CAFÉ"), Skill.normalizeName("café"))
        // Locale.ROOT keeps the key device-independent even on a Turkish locale.
        assertEquals("ilias", Skill.normalizeName("ILIAS"))
    }
}
