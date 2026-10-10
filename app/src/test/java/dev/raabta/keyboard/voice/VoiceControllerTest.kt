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
        assertTrue(texts.isEmpty())
        controller.stop()
        assertEquals(listOf("سلام"), texts)
        assertEquals(listOf(VoiceState.LISTENING, VoiceState.IDLE), states)
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
        controller.stop()
        assertEquals(listOf("current"), texts)
        assertTrue(errors.isEmpty())
    }

    @Test fun silenceAndServiceErrorsReturnToIdle() {
        lateinit var engine: Engine
        val errors = mutableListOf<VoiceError>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, errors::add)
        controller.start(VoiceSettings.ENGLISH) { fail("No text expected") }
        engine.listener.result(" ")
        controller.stop()
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
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

    @Test fun recognizerRequestUsesSystemServiceByDefaultWithoutForcingOfflineOrSendingTypedContext() {
        val request = DeviceSpeechEngine.request(VoiceSettings.URDU)
        assertEquals(VoiceSettings.URDU, request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertFalse(request.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        assertTrue(DeviceSpeechEngine.request(VoiceSettings.URDU, true).getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertTrue(request.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertEquals(setOf(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.EXTRA_LANGUAGE,
            RecognizerIntent.EXTRA_PREFER_OFFLINE, RecognizerIntent.EXTRA_PARTIAL_RESULTS, RecognizerIntent.EXTRA_MAX_RESULTS,
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS), request.extras!!.keySet())
        assertEquals(VoiceError.NO_SPEECH, DeviceSpeechEngine.mapError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(VoiceError.NETWORK, DeviceSpeechEngine.mapError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(VoiceError.LANGUAGE, DeviceSpeechEngine.mapError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
    }

    @Test fun shortPausesBufferUtterancesAndRestartWithoutInserting() {
        val engines = mutableListOf<Engine>()
        val texts = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also(engines::add) }, {}, { fail("Unexpected error") })
        controller.start(VoiceSettings.URDU, texts::add)
        engines[0].listener.speechStarted()
        engines[0].listener.processing()
        engines[0].listener.result("آج")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(2, engines.size)
        assertTrue(texts.isEmpty())
        assertEquals(VoiceState.LISTENING, controller.state)
        engines[1].listener.speechStarted()
        engines[1].listener.result("موسم اچھا ہے")
        controller.stop()
        assertEquals(listOf("آج موسم اچھا ہے"), texts)
    }

    @Test fun stopUsesLiveDraftWithinOneSecondWhenProviderFinalIsSlow() {
        lateinit var engine: Engine
        val texts = mutableListOf<String>()
        val previews = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, { fail("Unexpected error") }, previews::add)
        controller.start(VoiceSettings.ENGLISH, texts::add)
        engine.listener.speechStarted()
        engine.listener.partial("hello world")
        assertTrue(texts.isEmpty())
        assertEquals("hello world", previews.last())
        controller.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertEquals(listOf("hello world"), texts)
        engine.listener.result("late duplicate")
        assertEquals(1, texts.size)
        assertTrue(engine.destroyed)
        assertEquals("", previews.last())
    }

    @Test fun finalResultCanRefinePartialDuringStopWithoutDuplicateInsertion() {
        lateinit var engine: Engine
        val texts = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, { fail("Unexpected error") })
        controller.start(VoiceSettings.ENGLISH, texts::add)
        engine.listener.partial("hello")
        controller.stop()
        engine.listener.result("hello world")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(listOf("hello world"), texts)
    }

    @Test fun manualModeNeverInsertsForAnOrdinaryLongPause() {
        val engines = mutableListOf<Engine>()
        val texts = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also(engines::add) }, {}, {}, pauseMillis = { 0 })
        controller.start(VoiceSettings.ENGLISH, texts::add)
        engines[0].listener.result("draft")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(25))
        assertTrue(texts.isEmpty())
        assertEquals(VoiceState.LISTENING, controller.state)
        controller.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertEquals(listOf("draft"), texts)
    }

    @Test fun configuredLongSilenceInsertsOnceButShortSilenceDoesNot() {
        val engines = mutableListOf<Engine>()
        val texts = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also(engines::add) }, {}, { fail("Unexpected error") })
        controller.start(VoiceSettings.ENGLISH, texts::add)
        engines[0].listener.speechStarted()
        engines[0].listener.processing()
        engines[0].listener.result("draft")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(7))
        assertTrue(texts.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1800))
        assertEquals(listOf("draft"), texts)
        assertFalse(controller.active)
    }

    @Test fun cancellationClearsDraftAndCancelsScheduledRestart() {
        val engines = mutableListOf<Engine>()
        val previews = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also(engines::add) }, {}, {}, previews::add)
        controller.start(VoiceSettings.ENGLISH) { fail("Cancelled text must not insert") }
        engines[0].listener.result("discard this")
        controller.cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(1, engines.size)
        assertEquals("", previews.last())
        assertFalse(controller.active)
    }

    @Test fun networkFailureKeepsDraftForExplicitStopWithoutContinuingRecording() {
        lateinit var engine: Engine
        val texts = mutableListOf<String>()
        val errors = mutableListOf<VoiceError>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, errors::add)
        controller.start(VoiceSettings.ENGLISH, texts::add)
        engine.listener.partial("keep this")
        engine.listener.error(VoiceError.NETWORK)
        assertEquals(VoiceState.PAUSED, controller.state)
        assertTrue(engine.destroyed)
        assertTrue(texts.isEmpty())
        assertEquals(listOf(VoiceError.NETWORK), errors)
        controller.stop()
        assertEquals(listOf("keep this"), texts)
    }

    @Test fun qualityModeAllowsDelayedFinalToReplacePartialWithoutEarlyInsertion() {
        lateinit var engine: Engine
        val texts = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, {}, finalWaitMillis = { 3_000 })
        controller.start(VoiceSettings.URDU, texts::add)
        engine.listener.partial("غلط")
        controller.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(texts.isEmpty())
        engine.listener.result("درست الفاظ")
        assertEquals(listOf("درست الفاظ"), texts)
    }

    @Test fun sessionLimitPausesManualModeAndPreservesDraftUntilExplicitStop() {
        lateinit var engine: Engine
        val texts = mutableListOf<String>()
        val controller = VoiceController(SpeechEngineFactory { Engine(it).also { e -> engine = e } }, {}, {}, pauseMillis = { 0 })
        controller.start(VoiceSettings.ENGLISH, texts::add)
        engine.listener.speechStarted()
        engine.listener.partial("preserved")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
        assertEquals(VoiceState.PAUSED, controller.state)
        assertTrue(texts.isEmpty())
        assertTrue(engine.destroyed)
        controller.stop()
        assertEquals(listOf("preserved"), texts)
    }
}
