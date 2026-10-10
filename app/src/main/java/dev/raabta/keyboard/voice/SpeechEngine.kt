package dev.raabta.keyboard.voice

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface SpeechEngine {
    fun start(language: String)
    fun stop()
    fun cancel()
    fun destroy()

    interface Listener {
        fun speechStarted() = Unit
        fun partial(text: String?) = Unit
        fun level(rms:Float) = Unit
        fun processing()
        fun result(text: String?)
        fun error(error: VoiceError)
    }
}

fun interface SpeechEngineFactory {
    fun create(listener: SpeechEngine.Listener): SpeechEngine
}

enum class VoiceError { PERMISSION, UNAVAILABLE, NETWORK, NO_SPEECH, BUSY, LANGUAGE, AUDIO, TIMEOUT, INTERNAL }

class DeviceSpeechEngine(private val context: Context, listener: SpeechEngine.Listener) : SpeechEngine {
    private val recognizer: SpeechRecognizer
    private val onDeviceOnly = VoiceSettings.onDeviceOnly(context)

    init {
        recognizer = if (onDeviceOnly) {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                throw IllegalStateException("On-device recognition unavailable")
            }
        } else {
            check(SpeechRecognizer.isRecognitionAvailable(context)) { "No system speech recognizer" }
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = listener.speechStarted()
            override fun onRmsChanged(rmsdB: Float) = listener.level(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = listener.processing()
            override fun onError(error: Int) = listener.error(mapError(error))
            override fun onResults(results: Bundle?) {
                listener.result(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                listener.partial(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    override fun start(language: String) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission is required")
        }
        recognizer.startListening(request(language, onDeviceOnly, VoiceSettings.mixedPrimary(context), VoiceSettings.pauseMillis(context)))
    }
    override fun stop() = recognizer.stopListening()
    override fun cancel() = recognizer.cancel()
    override fun destroy() = recognizer.destroy()

    companion object {
        fun request(language: String, onDeviceOnly: Boolean = false, mixedPrimary: String = VoiceSettings.URDU, pauseMillis: Long = 8_000) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == VoiceSettings.MIXED) mixedPrimary else language)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDeviceOnly)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (pauseMillis == 0L) 30_000L else pauseMillis)
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
            if (Build.VERSION.SDK_INT >= 34 && language == VoiceSettings.MIXED) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION)
                val languages = arrayListOf(VoiceSettings.ENGLISH, VoiceSettings.URDU)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, languages)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, languages)
            }
        }

        fun mapError(code: Int): VoiceError = when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> VoiceError.NETWORK
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_SPEECH
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceError.BUSY
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceError.LANGUAGE
            SpeechRecognizer.ERROR_AUDIO -> VoiceError.AUDIO
            else -> VoiceError.INTERNAL
        }
    }
}
