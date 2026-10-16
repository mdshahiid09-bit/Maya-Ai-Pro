package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AudioPipeline
import com.example.engine.ChatMessage
import com.example.engine.GeminiLiveClient
import com.example.engine.SessionState
import com.example.ui.components.AudioWaveform
import com.example.ui.components.ConfirmationCard
import com.example.ui.components.GlowingOrb
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.GirlfriendPink
import com.example.ui.theme.GirlfriendRose
import com.example.ui.theme.JarvisNeonBlue
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.StatusWarning

@Composable
fun MainScreen(
    currentMode: String,
    onModeToggle: () -> Unit,
    geminiLiveClient: GeminiLiveClient,
    audioPipeline: AudioPipeline,
    hasApiKey: Boolean,
    onApiKeyUpdated: (String) -> Unit,
    onNavigateMemory: () -> Unit,
    onNavigateReminders: () -> Unit,
    onNavigateLogs: () -> Unit,
    onNavigateSettings: () -> Unit,
    confirmationState: Pair<String, String>?,
    onConfirmResult: (Boolean) -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val sessionState by geminiLiveClient.sessionState.collectAsState()
    val transcript by geminiLiveClient.transcript.collectAsState()
    val statusText by geminiLiveClient.currentStatusText.collectAsState()

    val inputAudioLevel by audioPipeline.inputAudioLevel.collectAsState()
    val outputAudioLevel by audioPipeline.outputAudioLevel.collectAsState()
    val activeAudioLevel = maxOf(inputAudioLevel, outputAudioLevel)

    val isGirlfriendMode = currentMode == "girlfriend"
    val accentColor = if (isGirlfriendMode) GirlfriendRose else JarvisNeonCyan
    val secondaryAccent = if (isGirlfriendMode) GirlfriendPink else JarvisNeonBlue

    val listState = rememberLazyListState()

    var showApiKeyPromptDialog by remember { mutableStateOf(false) }
    var quickKeyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Maya",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !hasApiKey -> StatusWarning
                                        sessionState == SessionState.DISCONNECTED -> Color(0xFF546E7A)
                                        sessionState == SessionState.ERROR -> Color(0xFFFF1744)
                                        sessionState == SessionState.CONNECTING || sessionState == SessionState.THINKING -> Color(0xFFFFD600)
                                        else -> accentColor
                                    }
                                )
                        )
                    }

                    if (isGirlfriendMode) {
                        Text(
                            text = "AI Companion",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GirlfriendPink
                        )
                    } else {
                        Text(
                            text = "Jarvis Engine",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = JarvisNeonBlue
                        )
                    }
                }

                // Mode Toggle Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101724))
                        .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onModeToggle()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGirlfriendMode) Icons.Default.Favorite else Icons.Default.SmartToy,
                            contentDescription = "Mode",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGirlfriendMode) "Girlfriend" else "Jarvis",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Notice if API key was skipped
            if (!hasApiKey) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F1A10))
                        .border(BorderStroke(1.dp, StatusWarning.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                        .clickable { showApiKeyPromptDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Key, contentDescription = "Key", tint = StatusWarning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "API Key not set. Tap to add key & start voice AI.",
                            color = Color(0xFFFFF176),
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "ADD KEY",
                        color = StatusWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center Animated Glowing Orb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                GlowingOrb(
                    sessionState = sessionState,
                    audioLevel = activeAudioLevel,
                    isGirlfriendMode = isGirlfriendMode,
                    size = 190.dp,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (!hasApiKey) {
                            showApiKeyPromptDialog = true
                        } else if (sessionState == SessionState.DISCONNECTED || sessionState == SessionState.ERROR) {
                            geminiLiveClient.startSession()
                        } else {
                            geminiLiveClient.disconnect()
                        }
                    }
                )
            }

            // Audio Waveform
            AudioWaveform(
                audioLevel = activeAudioLevel,
                barColor = accentColor,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status message
            Text(
                text = if (!hasApiKey) "Tap orb or mic to add Gemini API Key" else statusText,
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Live Transcript Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C111C)),
                border = BorderStroke(1.dp, Color(0xFF1E2838))
            ) {
                if (transcript.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Ready",
                                tint = Color(0xFF263238),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (!hasApiKey) "Gemini Key can be added in Settings anytime" else "Speak in English or Hindi",
                                color = Color(0xFF78909C),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Device tools and offline features are fully active",
                                color = Color(0xFF546E7A),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transcript, key = { it.id }) { msg ->
                            TranscriptBubble(msg = msg, accentColor = accentColor)
                        }
                    }
                }
            }

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChip("Read my screen", accentColor) {
                    if (!hasApiKey) showApiKeyPromptDialog = true
                    else geminiLiveClient.sendTextMessage("Please read my screen and summarize what is visible.")
                }
                QuickChip("What's on my schedule?", accentColor) {
                    if (!hasApiKey) onNavigateReminders()
                    else geminiLiveClient.sendTextMessage("Check my active reminders and upcoming schedule.")
                }
                QuickChip(if (isGirlfriendMode) "Switch to Jarvis" else "Switch to Girlfriend", accentColor) {
                    onModeToggle()
                }
                if (!hasApiKey) {
                    QuickChip("Settings > Add API Key", StatusWarning) {
                        onNavigateSettings()
                    }
                }
            }

            // Bottom Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateMemory) {
                    Icon(Icons.Default.Psychology, contentDescription = "Memory Hub", tint = Color(0xFFB0BEC5))
                }

                IconButton(onClick = onNavigateReminders) {
                    Icon(Icons.Default.Alarm, contentDescription = "Reminders", tint = Color(0xFFB0BEC5))
                }

                // Big Central Glowing Mic Button
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor, secondaryAccent)
                            )
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            if (!hasApiKey) {
                                showApiKeyPromptDialog = true
                            } else if (sessionState == SessionState.DISCONNECTED || sessionState == SessionState.ERROR) {
                                audioPipeline.playChime()
                                geminiLiveClient.startSession()
                            } else {
                                geminiLiveClient.disconnect()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (!hasApiKey) {
                            Icons.Default.Key
                        } else if (sessionState == SessionState.DISCONNECTED || sessionState == SessionState.ERROR) {
                            Icons.Default.Mic
                        } else {
                            Icons.Default.Stop
                        },
                        contentDescription = "Toggle Mic",
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(onClick = onNavigateLogs) {
                    Icon(Icons.Default.History, contentDescription = "Audit Logs", tint = Color(0xFFB0BEC5))
                }

                IconButton(onClick = onNavigateSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFFB0BEC5))
                }
            }
        }

        // Safety Confirmation Card
        AnimatedVisibility(
            visible = confirmationState != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            confirmationState?.let { (title, details) ->
                ConfirmationCard(
                    actionTitle = title,
                    detailsText = details,
                    accentColor = accentColor,
                    onConfirm = { onConfirmResult(true) },
                    onCancel = { onConfirmResult(false) }
                )
            }
        }

        // Quick API Key Dialog
        if (showApiKeyPromptDialog) {
            AlertDialog(
                onDismissRequest = {
                    showApiKeyPromptDialog = false
                    keyError = null
                },
                containerColor = Color(0xFF121824),
                title = {
                    Text(
                        text = "Add Gemini API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "To enable real-time voice streaming with Maya, enter your Gemini API key below. You can also configure it in Settings.",
                            color = Color(0xFF90A4AE),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = quickKeyInput,
                            onValueChange = {
                                quickKeyInput = it
                                keyError = null
                            },
                            placeholder = { Text("Paste Gemini API Key...", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0B1019),
                                unfocusedContainerColor = Color(0xFF0B1019),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = accentColor
                            )
                        )
                        if (keyError != null) {
                            Text(
                                text = keyError ?: "",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = quickKeyInput.trim()
                            if (trimmed.length < 20) {
                                keyError = "Please enter a valid API key."
                            } else {
                                onApiKeyUpdated(trimmed)
                                showApiKeyPromptDialog = false
                                quickKeyInput = ""
                                keyError = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Save Key", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showApiKeyPromptDialog = false
                                onNavigateSettings()
                            }
                        ) {
                            Text("Open Settings", color = Color(0xFFCFD8DC))
                        }
                        OutlinedButton(
                            onClick = {
                                showApiKeyPromptDialog = false
                                keyError = null
                            }
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TranscriptBubble(msg: ChatMessage, accentColor: Color) {
    val isUser = msg.sender == "user"
    val isSystem = msg.sender == "system"

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = when {
        isUser -> Color(0xFF14243B)
        isSystem -> Color(0xFF1B1B26)
        else -> Color(0xFF121824)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = when {
                        isUser -> "You"
                        isSystem -> "System Action"
                        else -> "Maya"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) accentColor else Color(0xFF90A4AE)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.text,
                    fontSize = 13.sp,
                    color = Color.White,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, accentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111722))
            .border(BorderStroke(1.dp, Color(0xFF222E42)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = Color(0xFFCFD8DC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
