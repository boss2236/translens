package com.example.data.model

import android.graphics.RectF

data class DetectedTextBlock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalText: String,
    val translatedText: String = "",
    val detectedLanguage: String = "auto",
    val boundingBox: RectF, // Normalized 0f..1f (left, top, right, bottom)
    val originalPixelBox: RectF? = null,
    val backgroundColor: Long = 0xDD111827, // AR backdrop patch color
    val textColor: Long = 0xFFFFFFFF,
    val isTranslating: Boolean = false,
    /** True when [boundingBox] is a speech bubble's interior; drawn as an oval. */
    val isBubble: Boolean = false,
    val confidence: Float = 1.0f
)

enum class TranslationEngine(val displayName: String) {
    OFFLINE("Offline"),
    ONLINE_FREE("Online (free)")
}

enum class OcrScriptMode(val displayName: String) {
    AUTO("Auto / All Scripts"),
    JAPANESE("Japanese (Kanji/Kana)"),
    CHINESE("Chinese (Hanzi)"),
    KOREAN("Korean (Hangul)"),
    DEVANAGARI("Hindi / Marathi / Nepali (Devanagari)"),
    LATIN("Latin (English, Spanish, French, etc.)")
}
