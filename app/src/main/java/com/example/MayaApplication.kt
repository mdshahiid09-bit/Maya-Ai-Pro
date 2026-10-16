package com.example

import android.app.Application
import com.example.agent.AppSkills
import com.example.agent.MayaNotificationListenerService
import com.example.agent.OverlayManager
import com.example.data.local.MayaDatabase
import com.example.data.pref.SecurePreferences
import com.example.engine.AudioPipeline
import com.example.engine.GeminiLiveClient
import com.example.tools.ToolExecutor
import com.example.wakeword.OpenWakeWordEngine
import com.example.wakeword.WakeWordEngine
import okhttp3.OkHttpClient

class MayaApplication : Application() {

    lateinit var database: MayaDatabase
        private set

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var wakeWordEngine: WakeWordEngine
        private set

    lateinit var audioPipeline: AudioPipeline
        private set

    lateinit var toolExecutor: ToolExecutor
        private set

    lateinit var geminiLiveClient: GeminiLiveClient
        private set

    lateinit var appSkills: AppSkills
        private set

    lateinit var overlayManager: OverlayManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = MayaDatabase.getInstance(this)
        securePreferences = SecurePreferences.getInstance(this)
        wakeWordEngine = OpenWakeWordEngine(this, securePreferences.wakeWordModel).apply {
            val sens = when (securePreferences.wakeWordSensitivity) {
                "Low" -> 0.35f
                "High" -> 0.75f
                else -> 0.5f
            }
            setSensitivity(sens)
            initialize()
        }

        audioPipeline = AudioPipeline(this, wakeWordEngine)
        val httpClient = OkHttpClient()
        toolExecutor = ToolExecutor(this, database, securePreferences, httpClient)
        geminiLiveClient = GeminiLiveClient(securePreferences, database, audioPipeline, toolExecutor)
        appSkills = AppSkills(this)
        overlayManager = OverlayManager(this)

        overlayManager.onStopClicked = {
            geminiLiveClient.disconnect()
            audioPipeline.flushPlayback()
        }

        // Notification announcement hook
        MayaNotificationListenerService.onMessageReceived = { messageInfo ->
            if (securePreferences.announceIncomingMessages) {
                geminiLiveClient.sendTextMessage(
                    "Incoming message from ${messageInfo.sender} on ${messageInfo.appName}: \"${messageInfo.text}\". Announce this to the user and ask if they'd like to reply."
                )
            }
        }
    }
}
