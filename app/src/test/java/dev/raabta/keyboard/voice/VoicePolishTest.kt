package dev.raabta.keyboard.voice

import android.os.Looper
import android.speech.RecognizerIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[34])
class VoicePolishTest {
    @Test fun recognizerIsStartedInTheTapCallAndOnlyCurrentReadinessEventsAreShown() {
        val listeners=mutableListOf<SpeechEngine.Listener>(); var starts=0; var ready=0
        val controller=VoiceController(SpeechEngineFactory { listener ->
            listeners.add(listener)
            object:SpeechEngine { override fun start(language:String) { starts++ }; override fun stop()=Unit; override fun cancel()=Unit; override fun destroy()=Unit }
        },{}, {},onReady={ ready++ })
        controller.start(VoiceSettings.ENGLISH) {}; assertEquals(1,starts); assertEquals(0,ready)
        listeners[0].ready(); assertEquals(1,ready)
        listeners[0].result("one"); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(149)); assertEquals(1,starts)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); assertEquals(2,starts)
        listeners[0].ready(); assertEquals(1,ready)
        listeners[1].ready(); assertEquals(2,ready); controller.cancel(); listeners[1].ready(); assertEquals(2,ready)
    }
    @Test fun formattingModeUsesFastPreferenceWithoutChangingLanguagesOrPrivacy() {
        val fast=DeviceSpeechEngine.request(VoiceSettings.URDU,fastFormatting=true)
        val quality=DeviceSpeechEngine.request(VoiceSettings.URDU,fastFormatting=false)
        assertEquals(RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY,fast.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING))
        assertEquals(RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY,quality.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING))
        assertEquals(VoiceSettings.URDU,fast.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)); assertFalse(fast.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true))
    }
    @Test fun waveformStartsStopsAndCannotStayAnimatingAfterCancellation() {
        val activity=Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val meter=VoiceLevelView(RuntimeEnvironment.getApplication()); activity.get().setContentView(meter)
        meter.setListening(true); meter.setLevel(7f); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); assertTrue(meter.animating)
        meter.setListening(false); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); assertFalse(meter.animating)
        activity.pause().stop().destroy()
    }
}
