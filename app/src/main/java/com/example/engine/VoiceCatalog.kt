package com.example.engine

data class VoiceInfo(
    val name: String,
    val gender: String,
    val styleTag: String,
    val pitch: Float = 1.0f,
    val rate: Float = 1.0f,
    val previewSampleText: String = "Hello! I am ready to assist you."
)

object VoiceCatalog {
    val ALL_VOICES: List<VoiceInfo> = listOf(
        VoiceInfo("Zephyr", "Female", "Breezy & Cheerful", 1.1f, 1.05f),
        VoiceInfo("Puck", "Male", "Playful & Dynamic", 1.15f, 1.1f),
        VoiceInfo("Charon", "Male", "Calm, Sharp & Resolute (Jarvis default)", 0.85f, 0.95f),
        VoiceInfo("Kore", "Female", "Gentle & Empathetic", 1.05f, 0.95f),
        VoiceInfo("Fenrir", "Male", "Deep & Commanding", 0.75f, 0.9f),
        VoiceInfo("Leda", "Female", "Youthful & Expressive", 1.2f, 1.05f),
        VoiceInfo("Orus", "Male", "Steady & Authoritative", 0.88f, 1.0f),
        VoiceInfo("Aoede", "Female", "Melodic & Thoughtful", 1.0f, 0.95f),
        VoiceInfo("Callirrhoe", "Female", "Crisp & Professional", 1.05f, 1.0f),
        VoiceInfo("Autonoe", "Female", "Warm & Casual", 1.0f, 1.0f),
        VoiceInfo("Enceladus", "Male", "Resonant & Calm", 0.82f, 0.95f),
        VoiceInfo("Iapetus", "Male", "Sophisticated & Articulate", 0.92f, 1.0f),
        VoiceInfo("Umbriel", "Neutral", "Balanced & Analytical", 0.95f, 1.0f),
        VoiceInfo("Algieba", "Female", "Vibrant & Direct", 1.1f, 1.05f),
        VoiceInfo("Despina", "Female", "Soft & Reassuring", 0.95f, 0.95f),
        VoiceInfo("Erinome", "Female", "Cheerful & Friendly", 1.15f, 1.0f),
        VoiceInfo("Algenib", "Male", "Confident & Tech-forward", 0.9f, 1.05f),
        VoiceInfo("Rasalgethi", "Male", "Deep & Philosophical", 0.8f, 0.9f),
        VoiceInfo("Laomedeia", "Female", "Light & Conversational", 1.05f, 1.05f),
        VoiceInfo("Achernar", "Male", "Crisp & Energetic", 1.0f, 1.1f),
        VoiceInfo("Alnilam", "Neutral", "Smooth & Focused", 0.95f, 1.0f),
        VoiceInfo("Schedar", "Male", "Rich & Engaging", 0.85f, 1.0f),
        VoiceInfo("Gacrux", "Male", "Grounded & Reliable", 0.8f, 0.95f),
        VoiceInfo("Pulcherrima", "Female", "Refined & Polished", 1.08f, 0.98f),
        VoiceInfo("Achird", "Male", "Friendly & Approchable", 0.95f, 1.02f),
        VoiceInfo("Zubenelgenubi", "Neutral", "Inquisitive & Bright", 1.0f, 1.0f),
        VoiceInfo("Vindemiatrix", "Female", "Clear & Direct", 1.02f, 1.02f),
        VoiceInfo("Sadachbia", "Male", "Subtle & Mellow", 0.88f, 0.95f),
        VoiceInfo("Sadaltager", "Neutral", "Neutral & Modern", 0.98f, 1.0f),
        VoiceInfo("Sulafat", "Female", "Warm, Loving & Playful (Girlfriend default)", 1.1f, 1.0f)
    )

    fun findVoice(name: String): VoiceInfo {
        return ALL_VOICES.find { it.name.equals(name, ignoreCase = true) }
            ?: VoiceInfo(name, "Neutral", "Custom Voice")
    }
}
