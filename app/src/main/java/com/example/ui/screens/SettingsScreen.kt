package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.MayaAccessibilityService
import com.example.agent.MayaNotificationListenerService
import com.example.data.pref.SecurePreferences
import com.example.ui.components.VoicePickerSheet
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.StatusSuccess

@Composable
fun SettingsScreen(
    securePrefs: SecurePreferences,
    accentColor: Color,
    onBack: () -> Unit,
    onDeleteAllData: () -> Unit,
    onModeChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var userName by remember { mutableStateOf(securePrefs.userName) }
    var currentMode by remember { mutableStateOf(securePrefs.currentMode) }
    var voiceJarvis by remember { mutableStateOf(securePrefs.voiceJarvis) }
    var voiceGirlfriend by remember { mutableStateOf(securePrefs.voiceGirlfriend) }
    var hasApiKey by remember { mutableStateOf(securePrefs.hasApiKey()) }
    var wakeWordEnabled by remember { mutableStateOf(securePrefs.wakeWordEnabled) }
    var wakeWordPhrase by remember { mutableStateOf(securePrefs.wakeWordModel) }
    var wakeWordSensitivity by remember { mutableStateOf(securePrefs.wakeWordSensitivity) }
    var confirmActions by remember { mutableStateOf(securePrefs.askConfirmationBeforeActions) }
    var announceMessages by remember { mutableStateOf(securePrefs.announceIncomingMessages) }
    var liveScreenMode by remember { mutableStateOf(securePrefs.liveScreenMode) }

    // Voice picker modal state
    var pickingVoiceForMode by remember { mutableStateOf<String?>(null) } // "jarvis" or "girlfriend"

    // Dialogs
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var newApiKeyInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Maya Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Profile & Mode
            SectionHeader("PERSONALITY & MODES")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1522)),
                border = BorderStroke(1.dp, Color(0xFF1E2838))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Name", color = Color(0xFF90A4AE), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            securePrefs.userName = it
                        },
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

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Active Assistant Persona", color = Color(0xFF90A4AE), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModeCard(
                            title = "Jarvis Mode",
                            desc = "Calm, sharp, witty & concise",
                            isSelected = currentMode == "jarvis",
                            activeColor = JarvisNeonCyan,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentMode = "jarvis"
                                securePrefs.currentMode = "jarvis"
                                onModeChanged("jarvis")
                            }
                        )
                        ModeCard(
                            title = "Girlfriend Mode",
                            desc = "Warm, loving & playful companion",
                            isSelected = currentMode == "girlfriend",
                            activeColor = Color(0xFFFF4081),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentMode = "girlfriend"
                                securePrefs.currentMode = "girlfriend"
                                onModeChanged("girlfriend")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingNavRow(
                        title = "Jarvis Voice",
                        subtitle = voiceJarvis,
                        onClick = { pickingVoiceForMode = "jarvis" }
                    )

                    SettingNavRow(
                        title = "Girlfriend Voice",
                        subtitle = voiceGirlfriend,
                        onClick = { pickingVoiceForMode = "girlfriend" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Wake Word Section
            SectionHeader("OFFLINE WAKE WORD")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1522)),
                border = BorderStroke(1.dp, Color(0xFF1E2838))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Always Listening (Wake Word)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Works with screen off and locked", color = Color(0xFF78909C), fontSize = 12.sp)
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = {
                                wakeWordEnabled = it
                                securePrefs.wakeWordEnabled = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF131A26))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Engine: openWakeWord (TFLite Offline)",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Target Phrase", color = Color(0xFF90A4AE), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val app = context.applicationContext as? MayaApplication
                        listOf("Hey Maya", "Hey Jarvis").forEach { phrase ->
                            val selected = wakeWordPhrase == phrase
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) accentColor else Color(0xFF1C2534))
                                    .clickable {
                                        wakeWordPhrase = phrase
                                        securePrefs.wakeWordModel = phrase
                                        app?.wakeWordEngine?.setPhrase(phrase)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = phrase,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Detection Sensitivity: $wakeWordSensitivity", color = Color(0xFF90A4AE), fontSize = 12.sp)
                    var sliderPos by remember {
                        mutableFloatStateOf(
                            when (wakeWordSensitivity) {
                                "Low" -> 0.25f
                                "High" -> 0.85f
                                else -> 0.5f
                            }
                        )
                    }
                    Slider(
                        value = sliderPos,
                        onValueChange = {
                            sliderPos = it
                            val level = when {
                                it < 0.4f -> "Low"
                                it > 0.7f -> "High"
                                else -> "Medium"
                            }
                            wakeWordSensitivity = level
                            securePrefs.wakeWordSensitivity = level
                            val app = context.applicationContext as? MayaApplication
                            val sensVal = when (level) {
                                "Low" -> 0.35f
                                "High" -> 0.75f
                                else -> 0.5f
                            }
                            app?.wakeWordEngine?.setSensitivity(sensVal)
                        },
                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Safety & Phone Protection
            SectionHeader("SAFETY & PROTECTIONS")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1522)),
                border = BorderStroke(1.dp, Color(0xFF1E2838))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Confirm before calls / SMS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Displays confirmation card before sending", color = Color(0xFF78909C), fontSize = 12.sp)
                        }
                        Switch(
                            checked = confirmActions,
                            onCheckedChange = {
                                confirmActions = it
                                securePrefs.askConfirmationBeforeActions = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Banking Protection Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A1826))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = "Shield", tint = StatusSuccess, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Banking & Payment Shield Active", color = StatusSuccess, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Maya strictly refuses interaction with banking apps, UPI, passwords, and OTPs.", color = Color(0xFF90A4AE), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Announce Incoming Messages", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Speaks WhatsApp/Telegram notifications aloud", color = Color(0xFF78909C), fontSize = 12.sp)
                        }
                        Switch(
                            checked = announceMessages,
                            onCheckedChange = {
                                announceMessages = it
                                securePrefs.announceIncomingMessages = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Game / Live Screen Mode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Streams ~1 fps frames for screen assistance", color = Color(0xFF78909C), fontSize = 12.sp)
                        }
                        Switch(
                            checked = liveScreenMode,
                            onCheckedChange = {
                                liveScreenMode = it
                                securePrefs.liveScreenMode = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Permissions Checklist
            SectionHeader("SYSTEM PERMISSIONS & SERVICES")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1522)),
                border = BorderStroke(1.dp, Color(0xFF1E2838))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val accessibilityOn = MayaAccessibilityService.isServiceRunning()
                    val notificationListenerOn = MayaNotificationListenerService.isServiceRunning()
                    val overlayOn = Settings.canDrawOverlays(context)

                    PermissionRow(
                        icon = Icons.Default.AccessibilityNew,
                        title = "Accessibility Phone Agent",
                        subtitle = if (accessibilityOn) "Active (automation enabled)" else "Disabled (tap to open settings)",
                        isGranted = accessibilityOn,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        }
                    )

                    PermissionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notification Listener Access",
                        subtitle = if (notificationListenerOn) "Active (messages & quick reply ready)" else "Disabled (tap to open settings)",
                        isGranted = notificationListenerOn,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        }
                    )

                    PermissionRow(
                        icon = Icons.Default.Layers,
                        title = "Display Over Other Apps",
                        subtitle = if (overlayOn) "Active (floating stop bubble)" else "Disabled (tap to grant)",
                        isGranted = overlayOn,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        }
                    )

                    PermissionRow(
                        icon = Icons.Default.BatteryAlert,
                        title = "Battery Optimization Exemption",
                        subtitle = "Prevents Android from killing wake word service",
                        isGranted = isBatteryOptimizationIgnored(context),
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. API Key & Data
            SectionHeader("GEMINI API KEY & DATA")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1522)),
                border = BorderStroke(1.dp, Color(0xFF1E2838))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingNavRow(
                        title = "Gemini API Key",
                        subtitle = if (hasApiKey) "•••••••••••••••• (Active)" else "Not Configured (Tap to add)",
                        onClick = { showApiKeyDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete", tint = Color(0xFFFF5252))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All My Data", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Voice Picker Sheet
        if (pickingVoiceForMode != null) {
            val targetMode = pickingVoiceForMode ?: "jarvis"
            val currentVoice = if (targetMode == "girlfriend") voiceGirlfriend else voiceJarvis
            val title = if (targetMode == "girlfriend") "Girlfriend Mode" else "Jarvis Mode"

            VoicePickerSheet(
                targetModeTitle = title,
                selectedVoiceName = currentVoice,
                accentColor = if (targetMode == "girlfriend") Color(0xFFFF4081) else JarvisNeonCyan,
                onVoiceSelected = { newVoice ->
                    if (targetMode == "girlfriend") {
                        voiceGirlfriend = newVoice
                        securePrefs.voiceGirlfriend = newVoice
                    } else {
                        voiceJarvis = newVoice
                        securePrefs.voiceJarvis = newVoice
                    }
                },
                onDismiss = { pickingVoiceForMode = null }
            )
        }

        // API Key Dialog
        if (showApiKeyDialog) {
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                containerColor = Color(0xFF121824),
                title = { Text("Update Gemini API Key", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter your Google Gemini API key:", color = Color(0xFF90A4AE), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newApiKeyInput,
                            onValueChange = { newApiKeyInput = it },
                            placeholder = { Text("Paste key here...", color = Color(0xFF64748B)) },
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
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newApiKeyInput.isNotBlank()) {
                                securePrefs.saveApiKey(newApiKeyInput.trim())
                                hasApiKey = true
                                showApiKeyDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Save Key", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showApiKeyDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }

        // Delete All Data Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                containerColor = Color(0xFF161C28),
                title = { Text("Delete All Data?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently clear all memories, scheduled reminders, action logs, and local preferences on this phone.", color = Color(0xFFB0BEC5)) },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteAllData()
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Delete Everything", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF78909C),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ModeCard(
    title: String,
    desc: String,
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) activeColor.copy(alpha = 0.15f) else Color(0xFF141C28))
            .border(BorderStroke(1.dp, if (isSelected) activeColor else Color(0xFF1E2838)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Text(text = title, color = if (isSelected) activeColor else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, color = Color(0xFF90A4AE), fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun SettingNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF90A4AE), fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color(0xFF546E7A))
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isGranted) StatusSuccess.copy(alpha = 0.15f) else Color(0xFF1E2838)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else icon,
                contentDescription = title,
                tint = if (isGranted) StatusSuccess else Color(0xFF90A4AE),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = if (isGranted) StatusSuccess else Color(0xFF78909C), fontSize = 11.sp)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = "Configure", tint = Color(0xFF546E7A))
    }
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
}
