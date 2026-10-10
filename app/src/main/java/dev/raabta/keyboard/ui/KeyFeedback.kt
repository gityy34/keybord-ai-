package dev.raabta.keyboard.ui

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

enum class FeedbackKey { STANDARD, SPACE, DELETE, ENTER }

interface FeedbackDevice {
    fun vibrate(view: View)
    fun sound(key: FeedbackKey)
}

private class AndroidFeedbackDevice(context: Context) : FeedbackDevice {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val resolver = context.contentResolver

    override fun vibrate(view: View) {
        // Android honors device haptic settings; no VIBRATE permission or forced feedback.
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun sound(key: FeedbackKey) {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            Settings.System.getInt(resolver, Settings.System.SOUND_EFFECTS_ENABLED, 0) == 0) return
        audio.playSoundEffect(when (key) {
            FeedbackKey.STANDARD -> AudioManager.FX_KEYPRESS_STANDARD
            FeedbackKey.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            FeedbackKey.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            FeedbackKey.ENTER -> AudioManager.FX_KEYPRESS_RETURN
        })
    }
}

/** Feedback failures never interrupt editing; preferences are captured once per keyboard view. */
class KeyFeedback(
    context: Context,
    private val preferences: KeyboardPreferences,
    private val device: FeedbackDevice = AndroidFeedbackDevice(context),
) {
    private var hapticsAvailable = true
    private var soundAvailable = true

    fun perform(view: View, key: FeedbackKey = FeedbackKey.STANDARD) {
        if (preferences.vibration && hapticsAvailable) {
            try { device.vibrate(view) }
            catch (_: RuntimeException) { hapticsAvailable = false }
        }
        if (preferences.sound && soundAvailable) {
            try { device.sound(key) }
            catch (_: RuntimeException) { soundAvailable = false }
        }
    }
}
