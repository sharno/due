package dev.sharno.due

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = TodoRepository(context)
        val todos = repository.all().toMutableList()

        if (intent.action == TaskScheduler.ACTION_COMPLETE_TASK) {
            val taskId = requireNotNull(intent.getStringExtra(TaskScheduler.EXTRA_TASK_ID)) {
                "Complete action did not include a task id"
            }
            val index = todos.indexOfFirst { it.id == taskId }
            if (index >= 0) {
                todos[index] = todos[index].copy(completed = true)
                repository.save(todos)
                TaskScheduler.cancelAlarm(context, taskId)
            }
        }

        TaskScheduler.synchronize(context, todos)
    }
}
