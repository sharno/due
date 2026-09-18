package dev.sharno.due

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TaskScheduler.ACTION_NOTIFICATION_DISMISSED,
            TaskScheduler.ACTION_TASK_DUE,
            TaskScheduler.ACTION_WATCHDOG,
            TaskScheduler.ACTION_COMPLETE_TASK -> Unit
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TodoRepository(context)
                if (intent.action == TaskScheduler.ACTION_COMPLETE_TASK) {
                    val taskId = requireNotNull(intent.getStringExtra(TaskScheduler.EXTRA_TASK_ID)) {
                        "Complete action did not include a task id"
                    }
                    repository.setCompleted(taskId, true)
                }
                TaskScheduler.synchronize(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
