package dev.sharno.due

import org.json.JSONArray
import org.json.JSONObject

object TodoBackup {
    const val FILE_NAME = "due-todos.json"
    const val MIME_TYPE = "application/json"

    private const val FORMAT = "dev.sharno.due.todos"
    private const val VERSION = 1

    fun encode(todos: List<Todo>): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("todos", JSONArray().apply { todos.forEach { put(it.toJson()) } })
        .toString(2)

    fun decode(raw: String): List<Todo> {
        val root = JSONObject(raw)
        require(root.getString("format") == FORMAT) { "This is not a Due todo backup" }
        require(root.getInt("version") == VERSION) { "Unsupported Due backup version" }
        return decodeArray(root.getJSONArray("todos"))
    }

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
            )
            require(todo.id.isNotBlank()) { "A todo backup contains an empty id" }
            require(todo.title.isNotBlank()) { "A todo backup contains an empty title" }
            require(ids.add(todo.id)) { "A todo backup contains duplicate ids" }
            todo
        }
    }

    private fun Todo.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("dueAtMillis", dueAtMillis)
        .put("completed", completed)
}
