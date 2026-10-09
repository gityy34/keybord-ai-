package dev.raabta.keyboard.voice

import android.content.Context

object VoiceSettings {
    private const val PREFERENCES = "voice_settings"
    private const val LANGUAGE = "language"
    const val ENGLISH = "en-US"
    const val URDU = "ur-PK"

    fun language(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(LANGUAGE, ENGLISH)
            .takeIf { it == ENGLISH || it == URDU } ?: ENGLISH

    fun setLanguage(context: Context, language: String) {
        require(language == ENGLISH || language == URDU)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(LANGUAGE, language).apply()
    }
}
