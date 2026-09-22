package dev.sharno.due

import org.json.JSONArray
import org.json.JSONObject

object TodoBackup {
    const val FILE_NAME = "due-todos.json"
    const val MIME_TYPE = "application/json"

    private const val FORMAT = "dev.sharno.due.todos"

    /**
     * Never bump this.
     *
     * [decode] checks the version with strict equality, so raising it would make every backup file
     * already on a user's device unreadable. New data is added as optional keys instead, read
     * tolerantly — the same way recurrence was added, and pinned by `oldBackupsDefaultToNonRepeating`.
     * An older build simply ignores the keys it does not know.
     */
    private const val VERSION = 1

    fun encode(snapshot: DueSnapshot): String {
        val ratingsByCompletion = snapshot.ratings.groupBy(SkillRating::completionId)
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("todos", JSONArray().apply { snapshot.todos.forEach { put(it.toJson()) } })
            .put("skills", JSONArray().apply { snapshot.skills.forEach { put(it.toJson()) } })
            .put(
                "taskSkills",
                JSONArray().apply { snapshot.taskSkills.forEach { put(it.toJson()) } },
            )
            .put(
                "completions",
                JSONArray().apply {
                    snapshot.completions.forEach {
                        put(it.toJson(ratingsByCompletion[it.id].orEmpty()))
                    }
                },
            )
            .toString(2)
    }

    fun decodeSnapshot(raw: String): DueSnapshot {
        val root = JSONObject(raw)
        require(root.getString("format") == FORMAT) { "This is not a Due todo backup" }
        require(root.getInt("version") == VERSION) { "Unsupported Due backup version" }

        val completionsJson = root.optJSONArray("completions")
        return DueSnapshot(
            todos = decodeArray(root.getJSONArray("todos")),
            skills = decodeSkills(root.optJSONArray("skills")),
            taskSkills = decodeTaskSkills(root.optJSONArray("taskSkills")),
            completions = decodeCompletions(completionsJson),
            ratings = decodeRatings(completionsJson),
        ).prune()
    }

    /** Todos alone, for callers that genuinely only need the task list. */
    fun decode(raw: String): List<Todo> = decodeSnapshot(raw).todos

    internal fun decodeLegacy(raw: String): List<Todo> = decodeArray(JSONArray(raw))

    private fun decodeArray(values: JSONArray): List<Todo> {
        val ids = mutableSetOf<String>()
        return List(values.length()) { index ->
            val value = values.getJSONObject(index)
            val todo = Todo(
                id = value.getString("id"),
                title = value.getString("title").trim(),
                dueAtMillis = value.getLong("dueAtMillis"),
                completed = value.getBoolean("completed"),
                recurrence = value.optJSONObject("recurrence")?.let(RecurrenceRuleCodec::decode),
                occurrencesCompleted = value.optInt("occurrencesCompleted", 0),
            )
            require(todo.id.isNotBlank()) { "A todo backup contains an empty id" }
            require(todo.title.isNotBlank()) { "A todo backup contains an empty title" }
            require(ids.add(todo.id)) { "A todo backup contains duplicate ids" }
            todo
        }
    }

    private fun decodeSkills(values: JSONArray?): List<Skill> {
        if (values == null) return emptyList()
        return List(values.length()) { index ->
            val value = values.getJSONObject(index)
            Skill(
                id = value.getString("id"),
                name = value.getString("name").trim(),
                colorArgb = value.optInt("colorArgb", DEFAULT_SKILL_COLOR),
                createdAtMillis = value.optLong("createdAtMillis", 0),
                archivedAtMillis = if (value.isNull("archivedAtMillis")) {
                    null
                } else {
                    value.optLong("archivedAtMillis")
                },
            ).also {
                require(it.id.isNotBlank()) { "A skill in the backup has an empty id" }
            }
        }
    }

    private fun decodeTaskSkills(values: JSONArray?): List<TaskSkillLink> {
        if (values == null) return emptyList()
        return List(values.length()) { index ->
            val value = values.getJSONObject(index)
            TaskSkillLink(
                todoId = value.getString("todoId"),
                skillId = value.getString("skillId"),
            )
        }
    }

    private fun decodeCompletions(values: JSONArray?): List<TaskCompletion> {
        if (values == null) return emptyList()
        return List(values.length()) { index ->
            val value = values.getJSONObject(index)
            TaskCompletion(
                id = value.getString("id"),
                todoId = value.getString("todoId"),
                taskTitle = value.optString("taskTitle"),
                occurrenceIndex = value.optInt("occurrenceIndex", 0),
                completedAtMillis = value.optLong("completedAtMillis", 0),
                ratingPromptedAtMillis = if (value.isNull("ratingPromptedAtMillis")) {
                    null
                } else {
                    value.optLong("ratingPromptedAtMillis")
                },
            )
        }
    }

    private fun decodeRatings(values: JSONArray?): List<SkillRating> {
        if (values == null) return emptyList()
        val ratings = mutableListOf<SkillRating>()
        for (index in 0 until values.length()) {
            val completion = values.getJSONObject(index)
            val completionId = completion.getString("id")
            val nested = completion.optJSONArray("ratings") ?: continue
            for (ratingIndex in 0 until nested.length()) {
                val value = nested.getJSONObject(ratingIndex)
                ratings += SkillRating(
                    completionId = completionId,
                    skillId = value.getString("skillId"),
                    rating = value.getInt("rating"),
                    ratedAtMillis = value.optLong("ratedAtMillis", 0),
                )
            }
        }
        return ratings
    }

    private fun Todo.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("dueAtMillis", dueAtMillis)
        .put("completed", completed)
        .put("recurrence", recurrence?.let(RecurrenceRuleCodec::encode) ?: JSONObject.NULL)
        .put("occurrencesCompleted", occurrencesCompleted)

    private fun Skill.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("colorArgb", colorArgb)
        .put("createdAtMillis", createdAtMillis)
        .put("archivedAtMillis", archivedAtMillis ?: JSONObject.NULL)

    private fun TaskSkillLink.toJson(): JSONObject = JSONObject()
        .put("todoId", todoId)
        .put("skillId", skillId)

    // Ratings nest inside their completion so the parent/child relationship cannot be violated.
    private fun TaskCompletion.toJson(ratings: List<SkillRating>): JSONObject = JSONObject()
        .put("id", id)
        .put("todoId", todoId)
        .put("taskTitle", taskTitle)
        .put("occurrenceIndex", occurrenceIndex)
        .put("completedAtMillis", completedAtMillis)
        .put("ratingPromptedAtMillis", ratingPromptedAtMillis ?: JSONObject.NULL)
        .put(
            "ratings",
            JSONArray().apply {
                ratings.forEach {
                    put(
                        JSONObject()
                            .put("skillId", it.skillId)
                            .put("rating", it.rating)
                            .put("ratedAtMillis", it.ratedAtMillis),
                    )
                }
            },
        )

    private const val DEFAULT_SKILL_COLOR = 0xFF1565C0.toInt()
}
