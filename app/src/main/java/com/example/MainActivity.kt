package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.service.MayaWakeWordService
import com.example.ui.screens.ActionLogScreen
import com.example.ui.screens.ApiKeySetupScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MayaTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var autoStartTalk = false
    private var pendingConfirmationDeferred: CompletableDeferred<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show over lock screen for visual feedback on wake-word
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        autoStartTalk = intent?.getBooleanExtra("EXTRA_AUTO_START_TALK", false) ?: false

        setContent {
            MayaAppRoot(
                onCheckPermissions = { checkAndRequestPermissions() },
                autoStartTalk = autoStartTalk
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("EXTRA_AUTO_START_TALK", false)) {
            val app = application as? MayaApplication
            app?.audioPipeline?.playChime()
            app?.geminiLiveClient?.startSession()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 101)
        } else {
            startWakeWordServiceIfEnabled()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startWakeWordServiceIfEnabled()
    }

    private fun startWakeWordServiceIfEnabled() {
        val app = application as? MayaApplication ?: return
        if (app.securePreferences.wakeWordEnabled &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                val serviceIntent = Intent(this, MayaWakeWordService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (_: Exception) { }
        }
    }

    @Composable
    private fun MayaAppRoot(
        onCheckPermissions: () -> Unit,
        autoStartTalk: Boolean
    ) {
        val app = application as MayaApplication
        val securePrefs = app.securePreferences
        val db = app.database
        val liveClient = app.geminiLiveClient
        val audioPipe = app.audioPipeline

        var isOnboardingDone by remember { mutableStateOf(securePrefs.isOnboardingDone) }
        var hasKey by remember { mutableStateOf(securePrefs.hasApiKey()) }
        var currentMode by remember { mutableStateOf(securePrefs.currentMode) }
        var currentScreen by remember { mutableStateOf("main") }

        // Confirmation dialog state
        var confirmationState by remember { mutableStateOf<Pair<String, String>?>(null) }

        LaunchedEffect(Unit) {
            onCheckPermissions()
            if (autoStartTalk && hasKey) {
                audioPipe.playChime()
                liveClient.startSession()
            }
        }

        // Wire tool confirmation callback to UI
        DisposableEffect(Unit) {
            app.toolExecutor.onRequestConfirmation = { actionTitle, details ->
                val deferred = CompletableDeferred<Boolean>()
                pendingConfirmationDeferred = deferred
                confirmationState = Pair(actionTitle, details)
                deferred.await()
            }
            app.toolExecutor.onModeSwitched = { newMode ->
                currentMode = newMode
                liveClient.reconnectWithMode(newMode)
            }
            onDispose {
                app.toolExecutor.onRequestConfirmation = null
                app.toolExecutor.onModeSwitched = null
            }
        }

        val isGirlfriend = currentMode == "girlfriend"

        MayaTheme(isGirlfriendMode = isGirlfriend) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                val modifier = Modifier.padding(innerPadding)

                if (!isOnboardingDone) {
                    ApiKeySetupScreen(
                        onApiKeySaved = { key ->
                            securePrefs.saveApiKey(key)
                            securePrefs.isOnboardingDone = true
                            hasKey = true
                            isOnboardingDone = true
                            startWakeWordServiceIfEnabled()
                        },
                        onSkip = {
                            securePrefs.isOnboardingDone = true
                            isOnboardingDone = true
                            startWakeWordServiceIfEnabled()
                        }
                    )
                } else {
                    when (currentScreen) {
                        "main" -> {
                            MainScreen(
                                currentMode = currentMode,
                                onModeToggle = {
                                    val nextMode = if (currentMode == "jarvis") "girlfriend" else "jarvis"
                                    currentMode = nextMode
                                    securePrefs.currentMode = nextMode
                                    liveClient.reconnectWithMode(nextMode)
                                },
                                geminiLiveClient = liveClient,
                                audioPipeline = audioPipe,
                                hasApiKey = hasKey,
                                onApiKeyUpdated = { newKey ->
                                    securePrefs.saveApiKey(newKey)
                                    hasKey = true
                                    liveClient.startSession()
                                },
                                onNavigateMemory = { currentScreen = "memory" },
                                onNavigateReminders = { currentScreen = "reminders" },
                                onNavigateLogs = { currentScreen = "logs" },
                                onNavigateSettings = { currentScreen = "settings" },
                                confirmationState = confirmationState,
                                onConfirmResult = { result ->
                                    confirmationState = null
                                    pendingConfirmationDeferred?.complete(result)
                                    pendingConfirmationDeferred = null
                                }
                            )
                        }

                        "memory" -> {
                            BackHandler { currentScreen = "main" }
                            MemoryScreen(
                                memoryDao = db.memoryDao(),
                                accentColor = if (isGirlfriend) com.example.ui.theme.GirlfriendRose else com.example.ui.theme.JarvisNeonCyan,
                                onBack = { currentScreen = "main" }
                            )
                        }

                        "reminders" -> {
                            BackHandler { currentScreen = "main" }
                            RemindersScreen(
                                reminderDao = db.reminderDao(),
                                accentColor = if (isGirlfriend) com.example.ui.theme.GirlfriendRose else com.example.ui.theme.JarvisNeonCyan,
                                onBack = { currentScreen = "main" }
                            )
                        }

                        "logs" -> {
                            BackHandler { currentScreen = "main" }
                            ActionLogScreen(
                                actionLogDao = db.actionLogDao(),
                                accentColor = if (isGirlfriend) com.example.ui.theme.GirlfriendRose else com.example.ui.theme.JarvisNeonCyan,
                                onBack = { currentScreen = "main" }
                            )
                        }

                        "settings" -> {
                            BackHandler { currentScreen = "main" }
                            SettingsScreen(
                                securePrefs = securePrefs,
                                accentColor = if (isGirlfriend) com.example.ui.theme.GirlfriendRose else com.example.ui.theme.JarvisNeonCyan,
                                onBack = {
                                    hasKey = securePrefs.hasApiKey()
                                    currentScreen = "main"
                                },
                                onModeChanged = { newMode ->
                                    currentMode = newMode
                                    liveClient.reconnectWithMode(newMode)
                                },
                                onDeleteAllData = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        db.memoryDao().clearAll()
                                        db.reminderDao().clearAll()
                                        db.actionLogDao().clearAll()
                                        securePrefs.clearAllData()
                                        hasKey = false
                                        isOnboardingDone = false
                                        currentScreen = "main"
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
