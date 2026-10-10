package dev.raabta.keyboard.voice

import android.speech.RecognizerIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class MixedVoiceTest {
    @Test fun mixedModeRequestsBothLanguagesAndAutomaticSwitchingWithoutForcingOffline() {
        val context = RuntimeEnvironment.getApplication()
        VoiceSettings.setLanguage(context, VoiceSettings.MIXED)
        assertEquals(VoiceSettings.MIXED, VoiceSettings.language(context))
        val request = DeviceSpeechEngine.request(VoiceSettings.MIXED)
        assertFalse(request.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        assertTrue(request.getBooleanExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, false))
        assertEquals(RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION, request.getStringExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH))
        assertEquals(VoiceSettings.URDU, request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(VoiceSettings.ENGLISH, DeviceSpeechEngine.request(VoiceSettings.MIXED, mixedPrimary = VoiceSettings.ENGLISH).getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(arrayListOf(VoiceSettings.ENGLISH, VoiceSettings.URDU), request.getStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES))
        assertEquals(arrayListOf(VoiceSettings.ENGLISH, VoiceSettings.URDU), request.getStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES))
    }
}
