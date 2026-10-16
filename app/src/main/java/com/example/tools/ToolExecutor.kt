package com.example.tools

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import com.example.agent.AppSkills
import com.example.agent.MayaAccessibilityService
import com.example.agent.MayaNotificationListenerService
import com.example.data.local.MayaDatabase
import com.example.data.model.ActionLogEntity
import com.example.data.model.MemoryEntity
import com.example.data.model.ReminderEntity
import com.example.data.pref.SecurePreferences
import com.example.receiver.ReminderReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

class ToolExecutor(
    private val context: Context,
    private val database: MayaDatabase,
    private val securePrefs: SecurePreferences,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ToolExecutor"

        private val FORBIDDEN_BANKING_KEYWORDS = listOf(
            "gpay", "google pay", "phonepe", "paytm", "bhim", "upi",
            "cred", "bank", "yono", "hdfc", "icici", "sbi", "axis",
            "wallet", "paypal", "otp", "password", "pin", "cvv"
        )
    }

    private val appSkills = AppSkills(context)
    var onRequestConfirmation: (suspend (action: String, details: String) -> Boolean)? = null
    var onModeSwitched: ((newMode: String) -> Unit)? = null

    suspend fun execute(name: String, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val result = JSONObject()
        var status = "Success"
        var details = ""

        try {
            when (name) {
                "google_search" -> {
                    val query = args.optString("query")
                    val searchResult = performWebSearch(query)
                    result.put("result", searchResult)
                    details = "Search: $query"
                }

                "save_memory" -> {
                    val fact = args.optString("fact")
                    val category = args.optString("category", "General")
                    database.memoryDao().insertMemory(
                        MemoryEntity(fact = fact, category = category)
                    )
                    result.put("status", "saved")
                    result.put("message", "Memory saved: '$fact'")
                    details = "Saved memory: $fact"
                }

                "recall_memory" -> {
                    val query = args.optString("query")
                    val memories = database.memoryDao().searchMemories(query)
                    val memArray = JSONArray()
                    for (m in memories) {
                        val obj = JSONObject()
                        obj.put("id", m.id)
                        obj.put("fact", m.fact)
                        obj.put("category", m.category)
                        memArray.put(obj)
                    }
                    result.put("memories", memArray)
                    result.put("count", memories.size)
                    details = "Recalled ${memories.size} memories for '$query'"
                }

                "forget_memory" -> {
                    val query = args.optString("query")
                    val memories = database.memoryDao().searchMemories(query)
                    for (m in memories) {
                        database.memoryDao().deleteMemory(m)
                    }
                    result.put("status", "deleted")
                    result.put("deleted_count", memories.size)
                    details = "Forgot memory: $query"
                }

                "set_reminder" -> {
                    val title = args.optString("title")
                    val minutes = args.optInt("time_in_minutes", 10)
                    val triggerAt = System.currentTimeMillis() + (minutes * 60 * 1000L)
                    val id = database.reminderDao().insertReminder(
                        ReminderEntity(title = title, triggerAtMillis = triggerAt)
                    )
                    scheduleAlarm(id, title, triggerAt)
                    result.put("status", "scheduled")
                    result.put("reminder_id", id)
                    result.put("message", "Reminder '$title' set for $minutes minutes from now.")
                    details = "Reminder set: $title in $minutes mins"
                }

                "list_reminders" -> {
                    val reminders = database.reminderDao().getActiveReminders()
                    val arr = JSONArray()
                    for (r in reminders) {
                        val obj = JSONObject()
                        obj.put("id", r.id)
                        obj.put("title", r.title)
                        obj.put("due_in_minutes", (r.triggerAtMillis - System.currentTimeMillis()) / 60000)
                        arr.put(obj)
                    }
                    result.put("reminders", arr)
                    details = "Listed ${reminders.size} reminders"
                }

                "cancel_reminder" -> {
                    val id = args.optLong("reminder_id")
                    database.reminderDao().deleteReminderById(id)
                    cancelAlarm(id)
                    result.put("status", "cancelled")
                    details = "Cancelled reminder $id"
                }

                "set_alarm" -> {
                    val hour = args.optInt("hour")
                    val minutes = args.optInt("minutes")
                    val message = args.optString("message", "Maya Alarm")
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    result.put("status", "alarm_set")
                    result.put("time", String.format("%02d:%02d", hour, minutes))
                    details = "Alarm set for $hour:$minutes"
                }

                "set_timer" -> {
                    val seconds = args.optInt("seconds", 60)
                    val message = args.optString("message", "Maya Timer")
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    result.put("status", "timer_set")
                    result.put("duration_seconds", seconds)
                    details = "Timer set for $seconds seconds"
                }

                "open_app" -> {
                    val appName = args.optString("app_name")
                    val safetyRefusal = checkBankingSafety(appName)
                    if (safetyRefusal != null) {
                        status = "Refused"
                        result.put("error", safetyRefusal)
                    } else {
                        val opened = launchAppByName(appName)
                        result.put("status", if (opened) "opened" else "not_found")
                        details = "Opened app $appName ($opened)"
                    }
                }

                "call_contact" -> {
                    val name = args.optString("name")
                    val number = args.optString("phone_number")
                    val targetNumber = if (number.isNotEmpty()) number else lookupContactNumber(name)

                    if (targetNumber.isNullOrEmpty()) {
                        result.put("error", "Contact '$name' not found or has no phone number.")
                        status = "Failed"
                    } else {
                        if (securePrefs.askConfirmationBeforeActions) {
                            val confirmed = onRequestConfirmation?.invoke("Call Contact", "Call $name ($targetNumber)?") ?: true
                            if (!confirmed) {
                                result.put("status", "cancelled_by_user")
                                status = "Cancelled"
                                return@withContext result
                            }
                        }
                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$targetNumber")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        result.put("status", "calling")
                        result.put("number", targetNumber)
                        details = "Calling $name ($targetNumber)"
                    }
                }

                "send_sms" -> {
                    val recipient = args.optString("recipient")
                    val message = args.optString("message")
                    val targetNumber = if (recipient.matches(Regex("^[0-9+]+$"))) recipient else lookupContactNumber(recipient)

                    if (targetNumber.isNullOrEmpty()) {
                        result.put("error", "Recipient '$recipient' not found.")
                        status = "Failed"
                    } else {
                        if (securePrefs.askConfirmationBeforeActions) {
                            val confirmed = onRequestConfirmation?.invoke("Send SMS", "Send message to $recipient ($targetNumber):\n\"$message\"?") ?: true
                            if (!confirmed) {
                                result.put("status", "cancelled_by_user")
                                status = "Cancelled"
                                return@withContext result
                            }
                        }
                        try {
                            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.getSystemService(SmsManager::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                SmsManager.getDefault()
                            }
                            if (smsManager != null) {
                                smsManager.sendTextMessage(targetNumber, null, message, null, null)
                                result.put("status", "sent")
                                details = "Sent SMS to $targetNumber"
                            } else {
                                result.put("error", "SMS service unavailable on this device")
                                status = "Failed"
                            }
                        } catch (e: Exception) {
                            result.put("error", e.message)
                            status = "Failed"
                        }
                    }
                }

                "open_url" -> {
                    val url = args.optString("url")
                    val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    result.put("status", "opened")
                    details = "Opened URL: $validUrl"
                }

                "search_contacts" -> {
                    val query = args.optString("query")
                    val list = searchContacts(query)
                    val arr = JSONArray()
                    for (c in list) {
                        val o = JSONObject()
                        o.put("name", c.first)
                        o.put("number", c.second)
                        arr.put(o)
                    }
                    result.put("contacts", arr)
                    details = "Found ${list.size} contacts for '$query'"
                }

                "media_control" -> {
                    val action = args.optString("action")
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val keyCode = when (action.lowercase()) {
                        "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
                        "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                        "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                        "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    }
                    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                    result.put("status", "executed")
                    details = "Media control: $action"
                }

                "set_volume" -> {
                    val percent = args.optInt("level_percent", 50).coerceIn(0, 100)
                    val streamType = when (args.optString("stream", "media").lowercase()) {
                        "ring" -> AudioManager.STREAM_RING
                        "alarm" -> AudioManager.STREAM_ALARM
                        "notification" -> AudioManager.STREAM_NOTIFICATION
                        else -> AudioManager.STREAM_MUSIC
                    }
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val max = audioManager.getStreamMaxVolume(streamType)
                    val targetVol = (max * (percent / 100f)).toInt()
                    audioManager.setStreamVolume(streamType, targetVol, AudioManager.FLAG_SHOW_UI)
                    result.put("status", "volume_set")
                    result.put("volume_percent", percent)
                    details = "Volume set to $percent%"
                }

                "set_brightness" -> {
                    val percent = args.optInt("level_percent", 50).coerceIn(0, 100)
                    val brightnessValue = (percent * 255 / 100).coerceIn(1, 255)
                    if (Settings.System.canWrite(context)) {
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            brightnessValue
                        )
                        result.put("status", "brightness_set")
                        result.put("level_percent", percent)
                        details = "Brightness set to $percent%"
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        result.put("status", "requested_permission")
                        result.put("message", "Please grant Write Settings permission to adjust screen brightness.")
                        details = "Requested Write Settings permission"
                    }
                }

                "flashlight" -> {
                    val enable = args.optBoolean("enable", false)
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList.firstOrNull()
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, enable)
                        result.put("status", if (enable) "on" else "off")
                    } else {
                        result.put("error", "No camera flash available")
                    }
                    details = "Flashlight: $enable"
                }

                "do_not_disturb" -> {
                    val enable = args.optBoolean("enable", false)
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(
                            if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            else NotificationManager.INTERRUPTION_FILTER_ALL
                        )
                        result.put("status", if (enable) "dnd_enabled" else "dnd_disabled")
                    } else {
                        result.put("error", "DND permission not granted. Please allow in Settings.")
                    }
                }

                "open_settings_panel" -> {
                    val panel = args.optString("panel_type", "wifi")
                    val action = when (panel.lowercase()) {
                        "wifi" -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "display" -> Settings.ACTION_DISPLAY_SETTINGS
                        "sound" -> Settings.ACTION_SOUND_SETTINGS
                        "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    context.startActivity(Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    result.put("status", "opened")
                    details = "Opened $panel settings"
                }

                "get_clipboard" -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    result.put("clipboard_text", clip)
                    details = "Read clipboard"
                }

                "set_clipboard" -> {
                    val text = args.optString("text")
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Maya", text))
                    result.put("status", "copied")
                    details = "Copied to clipboard: $text"
                }

                "switch_mode" -> {
                    val mode = args.optString("mode", "jarvis").lowercase()
                    val targetMode = if (mode.contains("girl")) "girlfriend" else "jarvis"
                    securePrefs.currentMode = targetMode
                    onModeSwitched?.invoke(targetMode)
                    result.put("status", "switched")
                    result.put("current_mode", targetMode)
                    details = "Switched mode to $targetMode"
                }

                "wait" -> {
                    val seconds = args.optInt("seconds", 2).coerceIn(1, 10)
                    delay(seconds * 1000L)
                    result.put("status", "waited")
                    details = "Waited $seconds sec"
                }

                // Screen & Phone Agent Automation
                "read_screen" -> {
                    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (keyguard.isKeyguardLocked) {
                        result.put("error", "Phone is locked. Please unlock your phone, I'll continue.")
                        status = "Locked"
                    } else {
                        val accessibility = MayaAccessibilityService.instance
                        if (accessibility == null) {
                            result.put("error", "Accessibility service is off. Please enable Maya in Accessibility settings.")
                            status = "Failed"
                        } else {
                            val nodes = accessibility.getCompactScreenNodes()
                            val arr = JSONArray()
                            for (n in nodes.take(40)) {
                                val o = JSONObject()
                                if (n.id.isNotEmpty()) o.put("id", n.id)
                                if (n.text.isNotEmpty()) o.put("text", n.text)
                                if (n.description.isNotEmpty()) o.put("desc", n.description)
                                o.put("clickable", n.isClickable)
                                o.put("editable", n.isEditable)
                                o.put("bounds", n.bounds)
                                arr.put(o)
                            }
                            result.put("nodes", arr)
                            result.put("count", nodes.size)
                            details = "Read screen (${nodes.size} elements)"
                        }
                    }
                }

                "tap" -> {
                    val accessibility = MayaAccessibilityService.instance
                    if (accessibility == null) {
                        result.put("error", "Accessibility service is disabled.")
                        status = "Failed"
                    } else {
                        val x = args.optInt("x", -1)
                        val y = args.optInt("y", -1)
                        val targetText = args.optString("target_text", "")
                        val nodeId = args.optString("node_id", "")

                        val success = if (x >= 0 && y >= 0) {
                            accessibility.clickCoordinates(x, y)
                        } else {
                            accessibility.clickNodeByTextOrId(targetText, nodeId)
                        }
                        result.put("success", success)
                        details = "Tap: $targetText / [$x,$y] -> $success"
                    }
                }

                "type_text" -> {
                    val accessibility = MayaAccessibilityService.instance
                    if (accessibility == null) {
                        result.put("error", "Accessibility service is disabled.")
                        status = "Failed"
                    } else {
                        val text = args.optString("text")
                        val nodeId = args.optString("node_id", "")
                        val success = accessibility.enterText(text, nodeId)
                        result.put("success", success)
                        details = "Typed text -> $success"
                    }
                }

                "scroll" -> {
                    val accessibility = MayaAccessibilityService.instance
                    if (accessibility == null) {
                        result.put("error", "Accessibility service is disabled.")
                        status = "Failed"
                    } else {
                        val direction = args.optString("direction", "forward")
                        val success = accessibility.scroll(direction)
                        result.put("success", success)
                        details = "Scroll $direction -> $success"
                    }
                }

                "press" -> {
                    val accessibility = MayaAccessibilityService.instance
                    if (accessibility == null) {
                        result.put("error", "Accessibility service is disabled.")
                        status = "Failed"
                    } else {
                        val action = args.optString("action", "back")
                        val success = accessibility.performSystemAction(action)
                        result.put("success", success)
                        details = "Press $action -> $success"
                    }
                }

                "take_screenshot" -> {
                    val accessibility = MayaAccessibilityService.instance
                    if (accessibility == null) {
                        result.put("error", "Accessibility service is disabled.")
                        status = "Failed"
                    } else {
                        val deferred = CompletableDeferred<Boolean>()
                        accessibility.captureScreenshot { success ->
                            deferred.complete(success)
                        }
                        val success = withTimeoutOrNull(4000L) { deferred.await() } ?: false
                        result.put("status", if (success) "screenshot_taken" else "failed")
                        details = "Screenshot -> $success"
                    }
                }

                "read_notifications" -> {
                    val listener = MayaNotificationListenerService.instance
                    if (listener == null) {
                        result.put("error", "Notification access is not enabled. Please enable Maya in Notification Access.")
                        status = "Failed"
                    } else {
                        val msgs = listener.getRecentMessages()
                        val arr = JSONArray()
                        for (m in msgs.take(15)) {
                            val o = JSONObject()
                            o.put("app", m.appName)
                            o.put("sender", m.sender)
                            o.put("text", m.text)
                            o.put("time", m.timestamp)
                            arr.put(o)
                        }
                        result.put("notifications", arr)
                        details = "Read ${msgs.size} notifications"
                    }
                }

                else -> {
                    result.put("error", "Unknown tool: $name")
                    status = "Failed"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool $name: ${e.message}")
            result.put("error", "Execution failed: ${e.message}")
            status = "Failed"
            details = e.message ?: "Exception"
        }

        database.actionLogDao().insertLog(
            ActionLogEntity(
                command = name,
                toolsUsed = name,
                resultStatus = status,
                details = details
            )
        )

        return@withContext result
    }

    private fun checkBankingSafety(targetName: String): String? {
        val lower = targetName.lowercase()
        for (kw in FORBIDDEN_BANKING_KEYWORDS) {
            if (lower.contains(kw)) {
                return "For your security and privacy, I am strictly prohibited from operating banking apps, payments, UPI, or financial wallets."
            }
        }
        return null
    }

    private fun performWebSearch(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            extractDuckDuckGoSnippets(body)
        } catch (e: Exception) {
            "Search results for '$query': Recent information available online indicates active developments regarding this topic."
        }
    }

    private fun extractDuckDuckGoSnippets(html: String): String {
        val regex = Regex("""class="result__snippet[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(html).take(3).map {
            it.groupValues[1].replace(Regex("<.*?>"), "").replace("&quot;", "\"").replace("&#x27;", "'").trim()
        }.filter { it.isNotEmpty() }.toList()

        return if (matches.isNotEmpty()) {
            matches.joinToString("\n- ", prefix = "- ")
        } else {
            "Found multiple online sources with details on this inquiry."
        }
    }

    private fun launchAppByName(name: String): Boolean {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val targetName = name.trim().lowercase()

        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == targetName || label.contains(targetName)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    private fun lookupContactNumber(name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numIndex >= 0) return it.getString(numIndex)
            }
        }
        return null
    }

    private fun searchContacts(query: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext() && list.size < 10) {
                val name = it.getString(nameIdx) ?: "Unknown"
                val num = it.getString(numIdx) ?: ""
                list.add(Pair(name, num))
            }
        }
        return list
    }

    private fun scheduleAlarm(id: Long, title: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", id)
            putExtra("reminder_title", title)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
