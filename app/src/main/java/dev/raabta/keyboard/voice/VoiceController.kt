package dev.raabta.keyboard.voice

import android.os.Handler
import android.os.Looper

enum class VoiceState { IDLE, LISTENING, PROCESSING }

/** A recognizer belongs to one session. Late callbacks from cancelled sessions are ignored. */
class VoiceController(
    private val factory: SpeechEngineFactory,
    private val onState: (VoiceState) -> Unit,
    private val onError: (VoiceError) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var engine: SpeechEngine? = null
    var state = VoiceState.IDLE
        private set
    val active: Boolean get() = state != VoiceState.IDLE
    private val timeout = Runnable { finish(VoiceError.TIMEOUT) }

    fun start(language: String, onText: (String) -> Unit) {
        cancel()
        val session = ++generation
        try {
            engine = factory.create(object : SpeechEngine.Listener {
                override fun processing() {
                    if (session == generation && active) {
                        changeState(VoiceState.PROCESSING)
                        scheduleTimeout(15_000)
                    }
                }
                override fun result(text: String?) {
                    if (session != generation || !active) return
                    val result = text?.trim()
                    if (result.isNullOrEmpty()) finish(VoiceError.NO_SPEECH)
                    else { finish(); onText(result) }
                }
                override fun error(error: VoiceError) {
                    if (session == generation && active) finish(error)
                }
            })
            changeState(VoiceState.LISTENING)
            scheduleTimeout(45_000)
            engine?.start(language)
        } catch (_: SecurityException) {
            finish(VoiceError.PERMISSION)
        } catch (_: IllegalStateException) {
            finish(VoiceError.UNAVAILABLE)
        } catch (_: RuntimeException) {
            finish(VoiceError.INTERNAL)
        }
    }

    fun stop() {
        if (state != VoiceState.LISTENING) return
        changeState(VoiceState.PROCESSING)
        scheduleTimeout(15_000)
        try { engine?.stop() }
        catch (_: SecurityException) { finish(VoiceError.PERMISSION) }
        catch (_: IllegalStateException) { finish(VoiceError.INTERNAL) }
        catch (_: RuntimeException) { finish(VoiceError.INTERNAL) }
    }

    fun cancel() { if (active || engine != null) finish() }

    private fun scheduleTimeout(delay: Long) {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, delay)
    }

    private fun changeState(state: VoiceState) {
        this.state = state
        onState(state)
    }

    private fun finish(error: VoiceError? = null) {
        ++generation
        handler.removeCallbacks(timeout)
        val previous = engine
        engine = null
        if (active) changeState(VoiceState.IDLE)
        var failure = error
        try { previous?.cancel() }
        catch (_: RuntimeException) { if (failure == null) failure = VoiceError.INTERNAL }
        finally {
            try { previous?.destroy() }
            catch (_: RuntimeException) { if (failure == null) failure = VoiceError.INTERNAL }
        }
        failure?.let(onError)
    }
}
