package dev.sharno.due

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DeviceStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }
        TaskScheduler.synchronize(context, TodoRepository(context).all())
    }
}
