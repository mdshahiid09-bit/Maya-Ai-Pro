package com.example.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.VoiceCatalog
import com.example.engine.VoiceInfo
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePickerSheet(
    targetModeTitle: String, // "Jarvis Mode" or "Girlfriend Mode"
    selectedVoiceName: String,
    accentColor: Color,
    onVoiceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Android TTS for voice preview
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val filteredVoices = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            VoiceCatalog.ALL_VOICES
        } else {
            VoiceCatalog.ALL_VOICES.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.styleTag.contains(searchQuery, ignoreCase = true) ||
                        it.gender.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D121B),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Voice for $targetModeTitle",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "30 prebuilt Gemini Live neural voices",
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search voices by name or style...", color = Color(0xFF78909C)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF141C2B),
                    unfocusedContainerColor = Color(0xFF141C2B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = accentColor,
                    unfocusedIndicatorColor = Color(0xFF263238)
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredVoices, key = { it.name }) { voice ->
                    val isSelected = voice.name.equals(selectedVoiceName, ignoreCase = true)

                    VoiceItemRow(
                        voice = voice,
                        isSelected = isSelected,
                        accentColor = accentColor,
                        onSelect = {
                            onVoiceSelected(voice.name)
                            onDismiss()
                        },
                        onPreview = {
                            if (isTtsReady && tts != null) {
                                tts?.setPitch(voice.pitch)
                                tts?.setSpeechRate(voice.rate)
                                tts?.speak(
                                    "Hello, I am ${voice.name}. How can I help you today?",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "preview_${voice.name}"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceItemRow(
    voice: VoiceInfo,
    isSelected: Boolean,
    accentColor: Color,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    val borderColor = if (isSelected) accentColor else Color(0xFF1F2937)
    val bgColor = if (isSelected) Color(0xFF152233) else Color(0xFF101622)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentColor else Color(0xFF1E2838)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = voice.name.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = voice.name,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF263238))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = voice.gender,
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }
            }
            Text(
                text = voice.styleTag,
                color = if (isSelected) accentColor else Color(0xFF78909C),
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        IconButton(
            onClick = onPreview,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C2534))
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Preview voice",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
