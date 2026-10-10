package dev.raabta.keyboard.voice

import android.content.Context

object VoiceSettings {
    private const val PREFERENCES = "voice_settings"
    private const val LANGUAGE = "language"
    const val ENGLISH = "en-US"
    const val URDU = "ur-PK"
    const val MIXED = "auto-en-ur"

    fun onDeviceOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean("on_device_only", false)

    fun setOnDeviceOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean("on_device_only", enabled).apply()
    }

    fun language(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(LANGUAGE, ENGLISH)
            .takeIf { it == ENGLISH || it == URDU || it == MIXED } ?: ENGLISH

    fun setLanguage(context: Context, language: String) {
        require(language == ENGLISH || language == URDU || language == MIXED)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(LANGUAGE, language).apply()
    }
}
