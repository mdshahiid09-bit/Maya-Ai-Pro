package com.example.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.MayaDatabase
import com.example.data.pref.SecurePreferences
import com.example.service.MayaWakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = SecurePreferences.getInstance(context)

            // 1. Reschedule all pending alarms from Room database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = MayaDatabase.getInstance(context)
                    val activeReminders = db.reminderDao().getActiveReminders()
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                    val now = System.currentTimeMillis()
                    for (rem in activeReminders) {
                        if (rem.triggerAtMillis > now) {
                            val rIntent = Intent(context, ReminderReceiver::class.java).apply {
                                putExtra("reminder_id", rem.id)
                                putExtra("reminder_title", rem.title)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                rem.id.toInt(),
                                rIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rem.triggerAtMillis, pendingIntent)
                            } else {
                                alarmManager.setExact(AlarmManager.RTC_WAKEUP, rem.triggerAtMillis, pendingIntent)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }

            // 2. Android 14+ restricts background microphone service launch from boot receiver.
            // Show notification prompting user to open Maya to restore hands-free wake word listening.
            if (prefs.wakeWordEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                    showRebootOpenAppNotification(context)
                } else {
                    try {
                        val serviceIntent = Intent(context, MayaWakeWordService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (_: Exception) {
                        showRebootOpenAppNotification(context)
                    }
                }
            }
        }
    }

    private fun showRebootOpenAppNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "maya_boot_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Maya Startup",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Maya Voice Assistant")
            .setContentText("Phone restarted. Tap once to activate 'Hey Maya' hands-free listening.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(3001, notification)
    }
}
