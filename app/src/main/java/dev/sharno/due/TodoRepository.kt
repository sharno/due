package dev.sharno.due

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TodoRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun all(): List<Todo> {
        val raw = preferences.getString(TODOS_KEY, "[]") ?: error("Todo storage is unavailable")
        val values = JSONArray(raw)
        return List(values.length()) { index ->
            values.getJSONObject(index).toTodo()
        }
    }

    fun save(todos: List<Todo>) {
        val values = JSONArray()
        todos.forEach { todo -> values.put(todo.toJson()) }
        check(preferences.edit().putString(TODOS_KEY, values.toString()).commit()) {
            "Unable to save todos"
        }
    }

    private fun Todo.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("dueAtMillis", dueAtMillis)
        .put("completed", completed)

    private fun JSONObject.toTodo(): Todo = Todo(
        id = getString("id"),
        title = getString("title"),
        dueAtMillis = getLong("dueAtMillis"),
        completed = getBoolean("completed"),
    )

    private companion object {
        const val PREFERENCES_NAME = "todos"
        const val TODOS_KEY = "items"
    }
}
