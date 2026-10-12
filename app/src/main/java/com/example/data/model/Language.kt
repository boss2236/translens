package com.example.data.model

data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
    val flagEmoji: String
)

object SupportedLanguages {
    val ALL = listOf(
        Language("en", "English", "English", "🇺🇸"),
        Language("es", "Spanish", "Español", "🇪🇸"),
        Language("fr", "French", "Français", "🇫🇷"),
        Language("de", "German", "Deutsch", "🇩🇪"),
        Language("ja", "Japanese", "日本語", "🇯🇵"),
        Language("zh", "Chinese", "中文", "🇨🇳"),
        Language("ko", "Korean", "한국어", "🇰🇷"),
        Language("ar", "Arabic", "العربية", "🇸🇦"),
        Language("ru", "Russian", "Русский", "🇷🇺"),
        Language("pt", "Portuguese", "Português", "🇧🇷"),
        Language("it", "Italian", "Italiano", "🇮🇹"),
        Language("hi", "Hindi", "हिन्दी", "🇮🇳"),
        Language("id", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
        Language("tr", "Turkish", "Türkçe", "🇹🇷"),
        Language("vi", "Vietnamese", "Tiếng Việt", "🇻🇳"),
        Language("th", "Thai", "ไทย", "🇹🇭"),
        Language("nl", "Dutch", "Nederlands", "🇳🇱"),
        Language("pl", "Polish", "Polski", "🇵🇱"),
        Language("uk", "Ukrainian", "Українська", "🇺🇦"),
        Language("bn", "Bengali", "বাংলা", "🇧🇩"),
        Language("ur", "Urdu", "اردو", "🇵🇰"),
        Language("fa", "Persian", "فارسی", "🇮🇷"),
        Language("he", "Hebrew", "עברית", "🇮🇱"),
        Language("el", "Greek", "Ελληνικά", "🇬🇷"),
        Language("mr", "Marathi", "मराठी", "🇮🇳"),
        Language("ne", "Nepali", "नेपाली", "🇳🇵"),
        Language("ms", "Malay", "Bahasa Melayu", "🇲🇾"),
        Language("tl", "Tagalog", "Tagalog", "🇵🇭"),
        Language("sv", "Swedish", "Svenska", "🇸🇪")
    )

    fun find(code: String): Language {
        return ALL.firstOrNull { it.code.equals(code, ignoreCase = true) || it.code.startsWith(code, ignoreCase = true) }
            ?: Language(code, code.uppercase(), code.uppercase(), "🌐")
    }
}
