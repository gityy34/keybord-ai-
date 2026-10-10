package dev.raabta.keyboard.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

enum class VoiceState { IDLE, LISTENING, PROCESSING, PAUSED }

/** Utterances stay in memory until Stop or the chosen long pause; editor text is never read. */
class VoiceController(
    private val factory: SpeechEngineFactory,
    private val onState: (VoiceState) -> Unit,
    private val onError: (VoiceError) -> Unit,
    private val onPreview: (String) -> Unit = {},
    private val pauseMillis: () -> Long = { 8_000 },
    private val finalWaitMillis: () -> Long = { 800 },
    private val onLevel: (Float) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var segment = 0
    private var engine: SpeechEngine? = null
    private var language = ""
    private var insert: ((String) -> Unit)? = null
    private val confirmed = StringBuilder()
    private var partial = ""
    private var pauseDelay = 8_000L
    private var finalWait = 800L
    private var quietSince = -1L
    private var busyRetries = 0
    var state = VoiceState.IDLE
        private set
    val active: Boolean get() = state != VoiceState.IDLE
    private val quietTimeout = Runnable { if (state == VoiceState.LISTENING) stop() }
    private val resultTimeout = Runnable { complete(if (draft().isEmpty()) VoiceError.TIMEOUT else null) }
    private val segmentTimeout = Runnable { fail(VoiceError.TIMEOUT) }
    private val sessionTimeout = Runnable { fail(VoiceError.TIMEOUT) }
    private var restart: Runnable? = null

    fun start(language: String, onText: (String) -> Unit) {
        cancel()
        ++generation
        this.language = language
        insert = onText
        pauseDelay = pauseMillis().coerceIn(0, 30_000)
        finalWait = finalWaitMillis().coerceIn(800, 3_000)
        busyRetries = 0
        quietSince = SystemClock.uptimeMillis()
        changeState(VoiceState.LISTENING)
        onPreview("")
        scheduleQuiet()
        // Keep a stalled/manual session bounded without inserting its draft automatically.
        handler.postDelayed(sessionTimeout, 300_000)
        beginSegment()
    }

    private fun beginSegment() {
        if (state != VoiceState.LISTENING) return
        val session = generation
        val utterance = ++segment
        fun current() = session == generation && utterance == segment && active
        try {
            engine = factory.create(object : SpeechEngine.Listener {
                override fun level(rms:Float) { if(current() && state==VoiceState.LISTENING) onLevel(rms) }
                override fun speechStarted() {
                    if (!current() || state != VoiceState.LISTENING) return
                    quietSince = -1
                    handler.removeCallbacks(quietTimeout)
                }
                override fun partial(text: String?) {
                    if (!current()) return
                    val value = text?.trim().orEmpty()
                    if (value.isNotEmpty() && value != partial) {
                        partial = value
                        onPreview(draft())
                        if (quietSince >= 0L) { quietSince = SystemClock.uptimeMillis(); scheduleQuiet() }
                    }
                }
                override fun processing() {
                    if (!current()) return
                    if (state == VoiceState.LISTENING) {
                        if (quietSince < 0L) quietSince = SystemClock.uptimeMillis()
                        scheduleQuiet()
                        handler.removeCallbacks(segmentTimeout)
                        handler.postDelayed(segmentTimeout, 15_000)
                    }
                }
                override fun result(text: String?) {
                    if (!current()) return
                    append(text?.trim().takeUnless { it.isNullOrEmpty() } ?: partial)
                    partial = ""
                    val stopping = state == VoiceState.PROCESSING
                    val cleanupFailed = releaseEngine()
                    onPreview(draft())
                    if (stopping) complete()
                    else if (cleanupFailed) changeState(VoiceState.PAUSED)
                    else {
                        busyRetries = 0
                        if (quietSince < 0L) quietSince = SystemClock.uptimeMillis()
                        scheduleQuiet()
                        restartAfter(300)
                    }
                }
                override fun error(error: VoiceError) {
                    if (!current()) return
                    if (state == VoiceState.PROCESSING) {
                        complete(if (draft().isEmpty()) error else null)
                    } else if (error == VoiceError.NO_SPEECH || (error == VoiceError.BUSY && ++busyRetries <= 2)) {
                        append(partial)
                        partial = ""
                        val cleanupFailed = releaseEngine()
                        if (cleanupFailed) { changeState(VoiceState.PAUSED); return }
                        if (quietSince < 0L) quietSince = SystemClock.uptimeMillis()
                        scheduleQuiet()
                        restartAfter(if (error == VoiceError.BUSY) 800 else 500)
                    } else fail(error)
                }
            })
            engine?.start(language)
        } catch (_: SecurityException) { fail(VoiceError.PERMISSION) }
        catch (_: IllegalStateException) { fail(VoiceError.UNAVAILABLE) }
        catch (_: RuntimeException) { fail(VoiceError.INTERNAL) }
    }

    fun stop() {
        if (state == VoiceState.PAUSED || (state == VoiceState.LISTENING && engine == null)) { complete(); return }
        if (state != VoiceState.LISTENING) return
        changeState(VoiceState.PROCESSING)
        handler.removeCallbacks(quietTimeout)
        handler.removeCallbacks(segmentTimeout)
        restart?.let(handler::removeCallbacks)
        // Give the provider a short chance to refine the live draft, then use text already available.
        handler.postDelayed(resultTimeout, if (draft().isNotEmpty()) finalWait else 3_000)
        try { engine?.stop() }
        catch (_: SecurityException) { complete(if (draft().isEmpty()) VoiceError.PERMISSION else null) }
        catch (_: RuntimeException) { complete(if (draft().isEmpty()) VoiceError.INTERNAL else null) }
    }

    fun cancel() { if (active || engine != null) finish() }

    private fun append(text: String) {
        if (text.isEmpty()) return
        if (confirmed.isNotEmpty()) confirmed.append(' ')
        confirmed.append(text)
    }

    private fun draft(): String = when {
        partial.isEmpty() -> confirmed.toString()
        confirmed.isEmpty() -> partial
        else -> "$confirmed $partial"
    }

    private fun scheduleQuiet() {
        handler.removeCallbacks(quietTimeout)
        if (pauseDelay > 0 && quietSince >= 0L && state == VoiceState.LISTENING) {
            handler.postDelayed(quietTimeout, (pauseDelay - (SystemClock.uptimeMillis() - quietSince)).coerceAtLeast(0))
        }
    }

    private fun restartAfter(delay: Long) {
        restart?.let(handler::removeCallbacks)
        val session = generation
        restart = Runnable { if (generation == session && state == VoiceState.LISTENING) beginSegment() }
            .also { handler.postDelayed(it, delay) }
    }

    private fun complete(error: VoiceError? = null) {
        val text = draft().trim()
        val callback = insert
        finish()
        if (text.isNotEmpty()) callback?.invoke(text)
        else onError(error ?: VoiceError.NO_SPEECH)
    }

    private fun fail(error: VoiceError) {
        if (draft().isNotEmpty()) {
            handler.removeCallbacks(quietTimeout)
            handler.removeCallbacks(sessionTimeout)
            restart?.let(handler::removeCallbacks)
            releaseEngine()
            changeState(VoiceState.PAUSED)
            onError(error)
        } else finish(error)
    }

    private fun releaseEngine(): Boolean {
        ++segment
        handler.removeCallbacks(segmentTimeout)
        val previous = engine
        engine = null
        var failed = false
        try { previous?.cancel() }
        catch (_: RuntimeException) { failed = true }
        finally {
            try { previous?.destroy() }
            catch (_: RuntimeException) { failed = true }
        }
        if (failed) onError(VoiceError.INTERNAL)
        return failed
    }

    private fun changeState(next: VoiceState) {
        if (state != next) { state = next; onState(next) }
    }

    private fun finish(error: VoiceError? = null) {
        ++generation
        handler.removeCallbacks(quietTimeout)
        handler.removeCallbacks(resultTimeout)
        handler.removeCallbacks(segmentTimeout)
        handler.removeCallbacks(sessionTimeout)
        restart?.let(handler::removeCallbacks)
        restart = null
        releaseEngine()
        confirmed.clear()
        partial = ""
        insert = null
        changeState(VoiceState.IDLE)
        onPreview("")
        error?.let(onError)
    }
}
