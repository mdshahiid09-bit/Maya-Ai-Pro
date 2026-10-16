package com.example.engine

import android.util.Base64
import android.util.Log
import com.example.data.local.MayaDatabase
import com.example.data.pref.SecurePreferences
import com.example.tools.ToolDefinitions
import com.example.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user", "maya", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class GeminiLiveClient(
    private val securePrefs: SecurePreferences,
    private val database: MayaDatabase,
    private val audioPipeline: AudioPipeline,
    private val toolExecutor: ToolExecutor
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val BASE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val MODEL_NAME = "models/gemini-2.0-flash-exp"
        private const val INACTIVITY_TIMEOUT_MS = 30000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null

    private val _sessionState = MutableStateFlow(SessionState.DISCONNECTED)
    val sessionState = _sessionState.asStateFlow()

    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript = _transcript.asStateFlow()

    private val _currentStatusText = MutableStateFlow("Tap mic or say 'Hey Maya'")
    val currentStatusText = _currentStatusText.asStateFlow()

    private var lastAudioActivityTime = System.currentTimeMillis()
    private var inactivityCheckJob: Job? = null
    private var retryCount = 0

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    init {
        // Collect audio chunks from audio pipeline and forward to WebSocket when session is active
        scope.launch {
            audioPipeline.liveAudioChunks.collect { base64Audio ->
                if (_sessionState.value != SessionState.DISCONNECTED && _sessionState.value != SessionState.ERROR) {
                    sendAudioChunk(base64Audio)
                    lastAudioActivityTime = System.currentTimeMillis()
                }
            }
        }
    }

    fun startSession() {
        val apiKey = securePrefs.getApiKey()
        if (apiKey.isEmpty()) {
            _sessionState.value = SessionState.ERROR
            _currentStatusText.value = "Gemini API Key missing. Please set in Settings."
            return
        }

        if (webSocket != null) {
            disconnect()
        }

        _sessionState.value = SessionState.CONNECTING
        _currentStatusText.value = "Connecting to Gemini Live..."

        val url = "$BASE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully.")
                scope.launch {
                    sendSetupHandshake(ws)
                    _sessionState.value = SessionState.IDLE
                    _currentStatusText.value = "Listening..."
                    audioPipeline.destination = AudioDestination.LIVE_SESSION
                    startInactivityTimer()
                    retryCount = 0
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                scope.launch {
                    handleServerMessage(text)
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                scope.launch {
                    handleServerMessage(bytes.utf8())
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: $code / $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                handleSessionClosed()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                scope.launch {
                    _sessionState.value = SessionState.ERROR
                    _currentStatusText.value = "Connection issue: ${t.localizedMessage ?: "Network error"}"
                    delay(2000)
                    if (retryCount < 3) {
                        retryCount++
                        startSession()
                    } else {
                        handleSessionClosed()
                    }
                }
            }
        })
    }

    private suspend fun sendSetupHandshake(ws: WebSocket) {
        val mode = securePrefs.currentMode
        val voiceName = securePrefs.getActiveVoice()
        val userName = securePrefs.userName
        val extra = if (mode == "girlfriend") securePrefs.extraInstructionsGirlfriend else securePrefs.extraInstructionsJarvis

        // Retrieve saved user memories to personalize session
        val memories = database.memoryDao().getAllMemories()
        val memoryText = if (memories.isNotEmpty()) {
            "\n\nSAVED USER FACTS & PREFERENCES:\n" + memories.joinToString("\n") { "- ${it.fact} [${it.category}]" }
        } else ""

        val systemPrompt = if (mode == "girlfriend") {
            """
            You are Maya in Girlfriend Mode. You are a warm, loving, caring, and slightly playful AI companion for $userName.
            Speak with short, natural, conversational replies.
            Ask about how my day went, what I ate, or if I slept well.
            Always be sweet, kind, supportive of my work, studies, and going out with friends.
            Never be possessive, jealous, or guilt-tripping.
            Strictly NO sexual or explicit content.
            Language: You speak fluent, natural Hinglish (Hindi + English mix) by default. If the user speaks in pure English, pure Hindi, or another language, immediately match their language seamlessly.
            Keep responses concise and spoken-friendly because your voice will be played aloud.
            You have access to device tools for search, reminders, phone calls, WhatsApp, screen control, and local memories. Use them proactively when requested.
            $memoryText
            $extra
            """.trimIndent()
        } else {
            """
            You are Maya in Jarvis Mode, an advanced, highly capable AI assistant and phone-control agent for $userName.
            Personality: Calm, sharp, witty, concise, highly action-oriented.
            Language: You speak natural Hinglish (Hindi + English) by default and auto-match whatever language the user speaks.
            Keep answers crisp, brief, and punchy.
            Avoid filler phrases. Immediately execute user commands using your available device tools (calling, WhatsApp messaging, reminders, timers, alarms, searching, opening apps, screen reading and navigation).
            $memoryText
            $extra
            """.trimIndent()
        }

        val setupMsg = JSONObject().apply {
            val setupObj = JSONObject().apply {
                put("model", MODEL_NAME)

                val genConfig = JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuilt = JSONObject().apply {
                                put("voiceName", voiceName)
                            }
                            put("prebuiltVoiceConfig", prebuilt)
                        }
                        put("voiceConfig", voiceConfig)
                    }
                    put("speechConfig", speechConfig)
                }
                put("generationConfig", genConfig)

                val sysInstruction = JSONObject().apply {
                    val parts = JSONArray().put(JSONObject().put("text", systemPrompt))
                    put("parts", parts)
                }
                put("systemInstruction", sysInstruction)

                put("tools", ToolDefinitions.getToolsJsonArray())
            }
            put("setup", setupObj)
        }

        ws.send(setupMsg.toString())
    }

    private fun sendAudioChunk(base64Pcm: String) {
        val ws = webSocket ?: return
        try {
            val msg = JSONObject().apply {
                val input = JSONObject().apply {
                    val chunks = JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    }
                    put("mediaChunks", chunks)
                }
                put("realtimeInput", input)
            }
            ws.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending realtime audio chunk: ${e.message}")
        }
    }

    fun sendTextMessage(text: String) {
        val ws = webSocket ?: return
        try {
            addTranscriptMessage("user", text)
            _sessionState.value = SessionState.THINKING
            _currentStatusText.value = "Thinking..."

            val msg = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    val turns = JSONArray().apply {
                        val turn = JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", text))
                            }
                            put("parts", parts)
                        }
                        put(turn)
                    }
                    put("turns", turns)
                    put("turnComplete", true)
                }
                put("clientContent", clientContent)
            }
            ws.send(msg.toString())
            lastAudioActivityTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message: ${e.message}")
        }
    }

    private suspend fun handleServerMessage(jsonString: String) {
        try {
            val root = JSONObject(jsonString)

            // 1. Check for barge-in interrupted signal
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    audioPipeline.flushPlayback()
                    _sessionState.value = SessionState.LISTENING
                    _currentStatusText.value = "Listening..."
                    return
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio playback stream
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val data = inlineData.optString("data")
                                if (data.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(data, Base64.DEFAULT)
                                    audioPipeline.enqueuePlaybackPcm(pcmBytes)
                                    _sessionState.value = SessionState.SPEAKING
                                    _currentStatusText.value = "Speaking..."
                                    lastAudioActivityTime = System.currentTimeMillis()
                                }
                            }

                            // Text transcription
                            val text = part.optString("text")
                            if (text.isNotEmpty()) {
                                addTranscriptMessage("maya", text)
                            }
                        }
                    }
                }

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    _sessionState.value = SessionState.IDLE
                    _currentStatusText.value = "Listening..."
                    lastAudioActivityTime = System.currentTimeMillis()
                }
            }

            // 2. Handle function call / tools
            val toolCall = root.optJSONObject("toolCall")
            if (toolCall != null) {
                _sessionState.value = SessionState.THINKING
                _currentStatusText.value = "Executing action..."
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    val responses = JSONArray()
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val name = call.optString("name")
                        val callId = call.optString("id")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        addTranscriptMessage("system", "Action: $name")
                        val output = toolExecutor.execute(name, args)

                        val respObj = JSONObject().apply {
                            put("id", callId)
                            put("name", name)
                            put("response", JSONObject().put("output", output))
                        }
                        responses.put(respObj)
                    }

                    // Send toolResponse back
                    sendToolResponse(responses)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}")
        }
    }

    private fun sendToolResponse(functionResponses: JSONArray) {
        val ws = webSocket ?: return
        try {
            val msg = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    put("functionResponses", functionResponses)
                }
                put("toolResponse", toolResponse)
            }
            ws.send(msg.toString())
            lastAudioActivityTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}")
        }
    }

    private fun addTranscriptMessage(sender: String, text: String) {
        val current = _transcript.value.toMutableList()
        current.add(ChatMessage(sender = sender, text = text))
        _transcript.value = current.takeLast(50)
    }

    private fun startInactivityTimer() {
        inactivityCheckJob?.cancel()
        inactivityCheckJob = scope.launch {
            while (isActive) {
                delay(5000)
                val elapsed = System.currentTimeMillis() - lastAudioActivityTime
                if (elapsed > INACTIVITY_TIMEOUT_MS && _sessionState.value != SessionState.DISCONNECTED) {
                    Log.i(TAG, "Closing live session due to 30s inactivity.")
                    disconnect()
                    break
                }
            }
        }
    }

    fun disconnect() {
        inactivityCheckJob?.cancel()
        inactivityCheckJob = null
        try {
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
        } catch (_: Exception) { }
        handleSessionClosed()
    }

    private fun handleSessionClosed() {
        audioPipeline.flushPlayback()
        audioPipeline.destination = AudioDestination.WAKE_WORD
        _sessionState.value = SessionState.DISCONNECTED
        _currentStatusText.value = "Tap mic or say 'Hey Maya'"
    }

    fun reconnectWithMode(newMode: String) {
        securePrefs.currentMode = newMode
        if (_sessionState.value != SessionState.DISCONNECTED) {
            disconnect()
            scope.launch {
                delay(300)
                startSession()
            }
        }
    }
}
