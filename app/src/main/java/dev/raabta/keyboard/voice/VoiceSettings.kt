package dev.raabta.keyboard.voice

import androidx.core.content.edit
import android.content.Context

object VoiceSettings {
    private const val PREFERENCES = "voice_settings"
    private const val LANGUAGE = "language"
    const val ENGLISH = "en-US"
    const val URDU = "ur-PK"
    const val MIXED = "auto-en-ur"

    fun finalWaitMillis(context: Context): Long =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong("final_wait", 800).takeIf { it == 800L || it == 3_000L } ?: 800

    fun setFinalWaitMillis(context: Context, millis: Long) {
        require(millis == 800L || millis == 3_000L)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putLong("final_wait", millis) }
    }

    fun pauseMillis(context: Context): Long =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong("pause_millis", 8_000).takeIf { it in listOf(0L, 8_000L, 12_000L) } ?: 8_000

    fun setPauseMillis(context: Context, millis: Long) {
        require(millis in listOf(0L, 8_000L, 12_000L))
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putLong("pause_millis", millis) }
    }

    fun mixedPrimary(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("mixed_primary", URDU).takeIf { it == ENGLISH || it == URDU } ?: URDU

    fun setMixedPrimary(context: Context, language: String) {
        require(language == ENGLISH || language == URDU)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putString("mixed_primary", language) }
    }

    fun onDeviceOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean("on_device_only", false)

    fun setOnDeviceOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putBoolean("on_device_only", enabled) }
    }

    fun language(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(LANGUAGE, ENGLISH)
            .takeIf { it == ENGLISH || it == URDU || it == MIXED } ?: ENGLISH

    fun setLanguage(context: Context, language: String) {
        require(language == ENGLISH || language == URDU || language == MIXED)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putString(LANGUAGE, language) }
    }
}
