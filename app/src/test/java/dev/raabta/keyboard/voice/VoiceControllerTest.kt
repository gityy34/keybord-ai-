package dev.raabta.keyboard.voice

import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceControllerTest {
    private class Engine(val listener: SpeechEngine.Listener) : SpeechEngine {
        var language = ""
        var stopped = false
        var cancelled = false
        var destroyed = false
        var failCleanup = false
        override fun start(language: String) { this.language = language }
        override fun stop() { stopped = true }
        override fun cancel() { cancelled = true; if (failCleanup) throw IllegalStateException("service disconnected") }
        override fun destroy() { destroyed = true }
    }

    @Test fun resultIsCommittedOnceAndRecognizerIsDestroyed() {
        lateinit var engine: Engine
        val texts = mutableListOf<String>()
        val states = mutableListOf<VoiceState>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, states::add, { fail("Unexpected error: $it") })
        controller.start(VoiceSettings.URDU, texts::add)
        assertEquals(VoiceSettings.URDU, engine.language)
        engine.listener.processing()
        engine.listener.result("  سلام  ")
        engine.listener.result("duplicate")
        assertEquals(listOf("سلام"), texts)
        assertEquals(listOf(VoiceState.LISTENING, VoiceState.PROCESSING, VoiceState.IDLE), states)
        assertTrue(engine.destroyed)
        assertFalse(controller.active)
    }

    @Test fun cancelledAndPreviousSessionsCannotInsertLateResults() {
        val engines = mutableListOf<Engine>()
        val texts = mutableListOf<String>()
        val errors = mutableListOf<VoiceError>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also(engines::add) }, {}, errors::add)
        controller.start(VoiceSettings.ENGLISH, texts::add)
        controller.cancel()
        controller.start(VoiceSettings.ENGLISH, texts::add)
        engines[0].listener.result("stale")
        engines[0].listener.error(VoiceError.NETWORK)
        engines[1].listener.result("current")
        assertEquals(listOf("current"), texts)
        assertTrue(errors.isEmpty())
    }

    @Test fun silenceAndServiceErrorsReturnToIdle() {
        lateinit var engine: Engine
        val errors = mutableListOf<VoiceError>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, errors::add)
        controller.start(VoiceSettings.ENGLISH) { fail("No text expected") }
        engine.listener.result(" ")
        assertEquals(listOf(VoiceError.NO_SPEECH), errors)
        controller.start(VoiceSettings.ENGLISH) { fail("No text expected") }
        engine.listener.error(VoiceError.LANGUAGE)
        assertEquals(listOf(VoiceError.NO_SPEECH, VoiceError.LANGUAGE), errors)
        assertFalse(controller.active)
    }

    @Test fun stalledRecordingAndProcessingTimeOut() {
        lateinit var engine: Engine
        val errors = mutableListOf<VoiceError>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, errors::add)
        controller.start(VoiceSettings.ENGLISH) { fail("No result expected") }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45))
        assertEquals(listOf(VoiceError.TIMEOUT), errors)
        assertTrue(engine.destroyed)
        controller.start(VoiceSettings.ENGLISH) { fail("No result expected") }
        controller.stop()
        assertTrue(engine.stopped)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
        assertEquals(listOf(VoiceError.TIMEOUT, VoiceError.TIMEOUT), errors)
    }

    @Test fun permissionRevocationAndMissingServiceDoNotCrash() {
        val errors = mutableListOf<VoiceError>()
        val denied = VoiceController(SpeechEngineFactory { throw SecurityException("permission revoked") }, {}, errors::add)
        denied.start(VoiceSettings.ENGLISH) { fail("No result expected") }
        val missing = VoiceController(SpeechEngineFactory { throw IllegalStateException("no service") }, {}, errors::add)
        missing.start(VoiceSettings.ENGLISH) { fail("No result expected") }
        assertEquals(listOf(VoiceError.PERMISSION, VoiceError.UNAVAILABLE), errors)
        assertFalse(denied.active)
        assertFalse(missing.active)
    }

    @Test fun cleanupFailureIsReportedAndDoesNotPreventDestroyOrTyping() {
        lateinit var engine: Engine
        val errors = mutableListOf<VoiceError>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, errors::add)
        controller.start(VoiceSettings.ENGLISH) {}
        engine.failCleanup = true
        controller.cancel()
        assertTrue(engine.destroyed)
        assertFalse(controller.active)
        assertEquals(listOf(VoiceError.INTERNAL), errors)
    }

    @Test fun passwordPrivateAndNonTextFieldsBlockVoice() {
        fun allows(type: Int, flags: Int = 0) = VoicePolicy.allows(EditorInfo().apply { inputType = type; imeOptions = flags })
        assertTrue(allows(InputType.TYPE_CLASS_TEXT))
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
            assertFalse(allows(InputType.TYPE_CLASS_TEXT or variation))
        }
        assertFalse(allows(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(allows(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(allows(InputType.TYPE_NULL))
    }

    @Test fun recognizerRequestContainsLanguageAndOfflinePreferenceWithoutTypedContext() {
        val request = DeviceSpeechEngine.request(VoiceSettings.URDU)
        assertEquals(VoiceSettings.URDU, request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(request.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertFalse(request.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
        assertEquals(setOf(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.EXTRA_LANGUAGE,
            RecognizerIntent.EXTRA_PREFER_OFFLINE, RecognizerIntent.EXTRA_PARTIAL_RESULTS, RecognizerIntent.EXTRA_MAX_RESULTS), request.extras!!.keySet())
        assertEquals(VoiceError.NO_SPEECH, DeviceSpeechEngine.mapError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(VoiceError.NETWORK, DeviceSpeechEngine.mapError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(VoiceError.LANGUAGE, DeviceSpeechEngine.mapError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
    }
}
