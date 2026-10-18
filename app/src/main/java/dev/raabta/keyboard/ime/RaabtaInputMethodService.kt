package dev.raabta.keyboard.ime

import androidx.core.net.toUri
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import dev.raabta.keyboard.setup.SetupActivity
import android.widget.Toast
import dev.raabta.keyboard.R
import dev.raabta.keyboard.voice.*
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.KeyboardOptions
import dev.raabta.keyboard.ui.appVersionName
import dev.raabta.keyboard.setup.OptionalPermissionActivity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.ClipboardManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.database.ContentObserver
import android.view.KeyEvent
import android.view.inputmethod.InputContentInfo
import dev.raabta.keyboard.tools.*
import dev.raabta.keyboard.setup.AiConnectionActivity
import android.content.ClipDescription
import android.text.SpannableString
import android.text.Spanned
import android.text.style.SuggestionSpan
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale

class RaabtaInputMethodService : InputMethodService() {
    private val state = KeyboardState()
    private var keyboard: KeyboardView? = null
    private var editor = EditorInfo()
    private var editorGeneration = 0
    private val editHistory = EditHistory()
    private var rawEditor: android.view.inputmethod.InputConnection? = null
    private var trackedEditor: android.view.inputmethod.InputConnection? = null
    private var voiceEditId: Long? = null
    private var voiceCommitted=false
    override fun getCurrentInputConnection(): android.view.inputmethod.InputConnection? {
        val raw = super.getCurrentInputConnection() ?: return null
        if (!VoicePolicy.allows(editor)) return raw
        if (raw !== rawEditor) {
            editHistory.clear(); voiceEditId=null; rawEditor=raw; trackedEditor=editHistory.wrap(raw)
        }
        return trackedEditor
    }
    private var awaitingVoicePermission = false
    private val options by lazy { KeyboardOptions(this) }
    private val learned by lazy { LearnedWords(this) }
    @Volatile private var acceptingLearning = true
    private var learningBuffer=""
    private val deleteSelection=DeleteSelection()
    private var deleting=false
    private var writingMode=""
    private var writingDraft=""
    private var writingResult=""
    private var writingVariants=emptyList<String>()
    private var writingCustom=""
    private var customEditing=false
    private var writingAutoStyle=false
    private var writingSource="ur"
    private var writingTarget="en"
    private var writingStyle="rewrite"
    private var writingBusy=false
    @Volatile private var writingRevision=0
    private var writingAnchor=""
    private var voiceToTranslation=false
    private var translator:TranslationEngine?=null
    @Volatile private var aiProvider:AIProvider?=null
    private var writingTargetText:EditorTarget?=null
    private var selectionCaseMode = 0
    private var voiceTranslateAfter = false
    private var voiceTranslateTarget = "en"
    private var lastVoiceText = ""
    internal var aiRequestFactory: ()->AiRequest = { AiRequest() }
    internal var translationFactory: (String,String)->TranslationEngine = { source,target -> LocalTranslation(this,source,target) }
    private val clipboard by lazy { ClipboardPreview(this) }
    private val ui=Handler(Looper.getMainLooper())
    private val mediaWorker=ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(4),ThreadPoolExecutor.AbortPolicy())
    private val worker=ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(1),ThreadPoolExecutor.DiscardOldestPolicy())
    /** Prediction is isolated from contact/AI work and never runs on the IME thread. */
    private val suggestionWorker=ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(1),ThreadPoolExecutor.DiscardOldestPolicy())
    /** Learning persistence is also kept away from the keypress and Space paths. */
    private val learningWorker=ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(8),ThreadPoolExecutor.DiscardOldestPolicy())
    private var contactNames=emptyList<String>()
    private var screenshotSince=0L
    private var showing=false
    private var spaceTime=-1L
    /** Local, non-persistent prefix used to show the typed candidate immediately. */
    private var typedPrefix=""
    /** Tracks whether the previous space followed a locally typed word. */
    private var lastSpaceHadWord=false
    private var candidateBefore=""
    private var candidateGeneration=0
    private var hasTypedSinceStart=false
    private var inputSequence=0L
    private val suggestionSequence=AtomicLong()
    private val suggestionUpdate=Runnable { updateSuggestions() }
    private val clipboardListener=ClipboardManager.OnPrimaryClipChangedListener {
        if(showing && VoicePolicy.allows(editor) && options.enabled("clipboard_enabled")) { clipboard.refresh(newCopy=true); refreshSuggestions() }
    }
    private val screenshotObserver=object:ContentObserver(ui) {
        override fun onChange(selfChange:Boolean) { checkScreenshots() }
    }
    internal var speechEngineFactory = SpeechEngineFactory { listener -> DeviceSpeechEngine(this, listener) }
    private val voice by lazy {
        VoiceController(SpeechEngineFactory { speechEngineFactory.create(it) }, { keyboard?.setVoiceState(it) }, ::voiceError,
            { keyboard?.showVoicePreview(it) }, { VoiceSettings.pauseMillis(this) }, { VoiceSettings.finalWaitMillis(this) }, { keyboard?.setVoiceLevel(it) }, { keyboard?.voiceReady() })
    }

    override fun onCreate() {
        super.onCreate()
        // Invalidate work queued by an older IME instance before accepting new
        // learning writes for this session.
        learned.beginSession()
    }

    override fun onCreateInputView(): View = KeyboardView(
        this, state, ::handleKey,
        onCaps = { voice.cancel(); state.lockShift(); keyboard?.updateShift() },
        onSettings = {
            voice.cancel(); closeWriting(); deleteSelection.cancel(); deleting=false; learningBuffer=""
            keyboard?.hideVoiceLanguages(); keyboard?.cancelRepeats()
            startActivity(Intent(this,dev.raabta.keyboard.setup.KeyboardSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onSwitch = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
        onVoice = ::toggleVoice,
        onCancelVoice = { voice.cancel(); keyboard?.dismissVoiceStatus(); keyboard?.hideVoiceLanguages() },
        onSettingsChanged = { page ->
            val position=keyboard?.findViewWithTag<android.widget.ScrollView>("SettingsScroll")?.scrollY ?: 0
            voice.cancel(); closeWriting(); learningBuffer=""; deleteSelection.cancel(); deleting=false; keyboard?.cancelRepeats()
            setInputView(onCreateInputView())
            keyboard?.showSettings(page)
            keyboard?.findViewWithTag<android.widget.ScrollView>("SettingsScroll")?.let { scroll -> scroll.addOnLayoutChangeListener(object:View.OnLayoutChangeListener { override fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int) { if(b>t) { scroll.removeOnLayoutChangeListener(this); scroll.scrollTo(0,position) } } }) }
            refreshSuggestions(); loadContacts()
        },
        onTool = ::tool,
        onCandidate = ::chooseCandidate,
    ).also {
        window.window?.let { imeWindow -> Palette.from(this).applySystemBars(imeWindow) }
        keyboard = it
        it.render(EditorPolicy.enterLabel(editor))
        it.setVoiceAllowed(VoicePolicy.allows(editor))
        refreshSuggestions()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        showing=true
        screenshotSince=System.currentTimeMillis()/1000
        try {
            getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipboardListener)
            getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipboardListener)
            contentResolver.unregisterContentObserver(screenshotObserver)
            if(options.enabled("screenshots") && OptionalPermissionActivity.hasImageAccess(this))
                contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,screenshotObserver)
        } catch (_:RuntimeException) { }
        if(VoicePolicy.allows(info) && options.enabled("clipboard_enabled")) clipboard.refresh()
        loadContacts(); refreshSuggestions()
        if (keyboard?.matchesCurrentPreferences() == false) {
            voice.cancel()
            keyboard?.cancelRepeats()
            setInputView(onCreateInputView())
        }
        if (awaitingVoicePermission) {
            awaitingVoicePermission = false
            keyboard?.showVoiceMessage(if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                R.string.voice_permission_granted else R.string.voice_permission_denied)
        }
        if(keyboard?.findViewWithTag<View>("ConnectionSummary")!=null) keyboard?.showConnections(AiConnection(this))
        dev.raabta.keyboard.ui.PendingKeyboardTool.consume().takeIf { it.isNotEmpty() }?.let(::tool)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        voice.cancel(); closeWriting(); learningBuffer=""; deleteSelection.cancel(); deleting=false
        keyboard?.cancelRepeats()
        super.onConfigurationChanged(newConfig)
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        voice.cancel(); closeWriting(); learningBuffer=""; deleteSelection.cancel(); deleting=false
        ++editorGeneration
        keyboard?.cancelRepeats()
        editor = attribute
        editHistory.clear(); rawEditor=null; trackedEditor=null; voiceEditId=null
        inputSequence++
        val capsMode = currentInputConnection?.getCursorCapsMode(attribute.inputType) ?: 0
        state.reset(EditorPolicy.isNumeric(attribute), options.enabled("auto_capitalization") && capsMode != 0)
        candidateBefore=""; clipboard.clearPreview(); contactNames=emptyList(); spaceTime=-1
        typedPrefix=""; lastSpaceHadWord=false
        hasTypedSinceStart=false
        keyboard?.showCandidates(emptyList())
        keyboard?.setTypingActive(false)
        ui.removeCallbacks(suggestionUpdate)
        keyboard?.render(EditorPolicy.enterLabel(editor))
        keyboard?.setVoiceAllowed(VoicePolicy.allows(editor))
    }

    private fun handleKey(key: Key) {
        // Any new key invalidates prediction work that is still in flight. The
        // commit itself remains synchronous and is never queued behind prediction.
        inputSequence++
        if (key is Key.Text || key == Key.Space) hasTypedSinceStart=true
        voice.cancel()
        if(writingMode.isNotEmpty()) { writingKey(key); return }
        if(key==Key.Emoji) learningBuffer=""
        if(keyboard?.handleEmojiKey(key)==true) { learningBuffer=""; return }
        if(key !is Key.DeleteSwipe && deleting) { deleteSelection.cancel(); deleting=false }
        val connection = currentInputConnection
        if(key !is Key.Text && key!=Key.Space && key!=Key.Enter) learningBuffer=""
        when (key) {
            is Key.Text -> {
                val displayed=state.display(key.text)
                // Keep a small in-memory prefix for the current touch session.
                // It is never persisted or sent anywhere, but lets the typed
                // word appear in the first suggestion slot immediately while
                // the richer prediction pass runs in the worker.
                if (displayed.isNotEmpty() && displayed.all { it.isLetter() })
                    typedPrefix=(typedPrefix+displayed).takeLast(64)
                else typedPrefix=""
                if(displayed.length==1 && displayed[0].isLetter() && canLearn()) {
                    // Keep this entirely local.  Reading the editor over Binder for
                    // every letter makes rapid typing contend with the next touch.
                    // Cursor moves and selection changes already clear this buffer.
                    learningBuffer=(learningBuffer+displayed).takeLast(41)
                } else {
                    if(key.text in listOf(".",",","?","!","؟","،","۔")) learnBoundary()
                    learningBuffer=""
                }
                if (EditorCommands.type(connection, displayed + if(VoicePolicy.allows(editor) && options.enabled("auto_space") && key.text in listOf(".",",","?","!","؟","،","۔")) " " else "")) {
                    val previousShift = state.shift
                    state.afterCharacter()
                    if (previousShift != state.shift) keyboard?.updateShift()
                }
            }
            Key.Space -> if(connection!=null) {
                val localWord=typedPrefix
                if (richSpacingEnabled()) editHistory.transaction("spacing",connection) { space(connection,localWord) }
                else space(connection,localWord)
            }
            Key.Delete -> { typedPrefix=""; lastSpaceHadWord=false; EditorCommands.delete(connection) }
            Key.Enter -> { typedPrefix=""; lastSpaceHadWord=false; learnBoundary(); EditorCommands.enter(connection, editor) }
            Key.Emoji -> keyboard?.showEmojiPicker()
            Key.Language -> tool("language")
            is Key.DeleteSwipe -> {
                if(key.words>=0) {
                    if(!deleting && VoicePolicy.allows(editor)) deleting=deleteSelection.begin(connection)
                    if(deleting) deleteSelection.move(key.words)
                } else {
                    if(key.words==-1 && deleting) deleteSelection.finish() else deleteSelection.cancel()
                    deleting=false
                }
            }
            is Key.Cursor -> { typedPrefix=""; lastSpaceHadWord=false; CursorNavigation.move(connection,key.steps) }
            is Key.Glide -> if(VoicePolicy.allows(editor)) glide(key.path, connection)
            Key.Shift -> {
                val selected = try { connection?.getSelectedText(0)?.toString().orEmpty() } catch (_: RuntimeException) { "" }
                if (selected.isNotEmpty() && VoicePolicy.allows(editor)) {
                    val transformed = when (selectionCaseMode % 3) {
                        0 -> selected.replace(Regex("\\p{L}+")) { token ->
                            token.value.replaceFirstChar { first -> first.titlecase(Locale.ROOT) }
                        }
                        1 -> selected.uppercase(Locale.ROOT)
                        else -> selected.lowercase(Locale.ROOT)
                    }
                    if (transformed != selected) connection?.commitText(transformed, 1)
                    selectionCaseMode = (selectionCaseMode + 1) % 3
                    refreshSuggestions()
                } else {
                    state.toggleShift(); keyboard?.updateShift()
                }
            }
            is Key.Mode -> { typedPrefix=""; lastSpaceHadWord=false; state.selectMode(key.mode); keyboard?.render(EditorPolicy.enterLabel(editor)) }
        }
        if(key is Key.Text && key.text=="'" && options.enabled("apostrophe_space") && state.mode!=KeyboardMode.LETTERS) {
            state.selectMode(KeyboardMode.LETTERS); keyboard?.render(EditorPolicy.enterLabel(editor))
        }
        if(key!=Key.Space) spaceTime=-1
        if (key is Key.Text && typedPrefix.isNotEmpty() && options.enabled("suggestion_strip")) {
            keyboard?.setTypingActive(true)
            keyboard?.showCandidates(listOf(Candidate(typedPrefix,typedPrefix,typedPrefix)))
        }
        refreshSuggestions()
    }

    private fun richSpacingEnabled() = options.enabled("double_space") ||
        options.enabled("auto_correction") || options.enabled("spell_check") || options.enabled("grammar_check")

    /**
     * Cursor-capitalisation is useful feedback, but getCursorCapsMode is an
     * editor IPC on many hosts.  Schedule it after the commit so a fast Space
     * press is never held up by the host application.
     */
    private fun updateAutomaticShiftAsync(connection: android.view.inputmethod.InputConnection) {
        if (!options.enabled("auto_capitalization") || state.shift == ShiftMode.LOCKED) return
        val sequence = inputSequence
        ui.post {
            if (sequence != inputSequence || currentInputConnection !== connection || !VoicePolicy.allows(editor)) return@post
            try {
                state.setAutomaticShift(connection.getCursorCapsMode(editor.inputType) != 0)
                keyboard?.updateShift()
            } catch (_: RuntimeException) { }
        }
    }

    private fun glide(path: String, connection: android.view.inputmethod.InputConnection?) {
        val input = connection ?: return
        val generation = editorGeneration
        val sequence = inputSequence
        val language = options.number("language")
        val roman = options.enabled("roman_suggestions")
        val dictionary = options.dictionary()
        val learnedWords = if (canLearn()) learned.matches(path.take(2), language) else emptyList()
        try {
            suggestionWorker.execute {
                val match = LocalSuggestions.glideCandidates(path, dictionary, learnedWords, language, roman)
                    .firstOrNull()?.text ?: return@execute
                ui.post {
                    if (generation != editorGeneration || sequence != inputSequence || currentInputConnection !== input || !VoicePolicy.allows(editor)) return@post
                    if (EditorCommands.type(input, state.display(match))) {
                        learningBuffer=match
                        state.afterCharacter(); keyboard?.updateShift(); refreshSuggestions()
                    }
                }
            }
        } catch (_:RejectedExecutionException) { }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInput() {
        voice.cancel(); closeWriting(); learningBuffer=""; typedPrefix=""; lastSpaceHadWord=false; deleteSelection.cancel(); deleting=false
        ++editorGeneration
        keyboard?.cancelRepeats()
        editor = EditorInfo()
        editHistory.clear(); rawEditor=null; trackedEditor=null; voiceEditId=null
        state.reset(numeric = false, capitalized = false)
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        voice.cancel()
        keyboard?.cancelRepeats()
        hideTools()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        acceptingLearning=false
        voice.cancel()
        keyboard?.cancelRepeats()
        hideTools(); worker.shutdownNow(); mediaWorker.shutdownNow(); suggestionWorker.shutdownNow(); learningWorker.shutdownNow(); contactNames=emptyList()
        keyboard = null
        super.onDestroy()
    }

    override fun onWindowHidden() {
        voice.cancel(); hideTools(); keyboard?.cancelRepeats()
        super.onWindowHidden()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        editHistory.selectionChanged(newSelStart,newSelEnd)
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            inputSequence++
            typedPrefix=""; lastSpaceHadWord=false
            selectionCaseMode = 0
            voice.cancel(); refreshSuggestions()
            if(writingMode.isNotEmpty()) closeWriting()
        }
    }

    private fun toggleVoice() {
        if(customEditing) { keyboard?.showTextMessage("Finish the custom instruction first using Use tool."); return }
        keyboard?.showVoiceLanguages(if(writingMode=="translate") when(writingSource) { "ur" -> VoiceSettings.URDU; "en" -> VoiceSettings.ENGLISH; "auto" -> VoiceSettings.MIXED; else -> writingSource } else VoiceSettings.language(this))
        if (voice.active) { voice.stop(); return }
        if (!VoicePolicy.allows(editor)) { message(R.string.voice_blocked); return }
        if (writingMode!="translate" && VoiceSettings.language(this) == VoiceSettings.MIXED && Build.VERSION.SDK_INT < 34) {
            keyboard?.showVoiceMessage(R.string.voice_mixed_unsupported)
            return
        }
        val connection = currentInputConnection ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            awaitingVoicePermission = true
            startActivity(Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val generation = editorGeneration
        val revision = writingRevision
        val toolMode = writingMode
        val language = if(toolMode=="translate") { when(writingSource) { "ur" -> VoiceSettings.URDU; "en" -> VoiceSettings.ENGLISH; "auto" -> VoiceSettings.MIXED; else -> writingSource } } else VoiceSettings.language(this)
        voiceEditId=null; voiceCommitted=false
        voice.start(language) { result ->
            if (generation != editorGeneration || currentInputConnection !== connection || !VoicePolicy.allows(editor)) {
                message(R.string.voice_error_insert)
                return@start
            }
            lastVoiceText = result
            if(voiceTranslateAfter) {
                voiceTranslateAfter = false
                keyboard?.showVoicePreview(result)
                translateVoiceText(result, voiceTranslateTarget)
            } else if(toolMode.isNotEmpty()) {
                if(revision!=writingRevision || toolMode!=writingMode) return@start
                writingDraft=(writingDraft+if(writingDraft.isEmpty()) "" else " ")+result
                writingResult=""; writingVariants=emptyList(); keyboard?.showVoicePreview("")
                if(voiceToTranslation && toolMode=="translate") runWriting(autoInsert=true) else updateWriting("Voice draft ready.")
            } else {
                val inserted=editHistory.transaction("voice",connection) { EditorCommands.type(connection,result) }
                if(!inserted) message(R.string.voice_error_insert) else {
                    voiceCommitted=true; voiceEditId=editHistory.lastEditId; keyboard?.showVoiceCompleted(result)
                }
            }
            refreshSuggestions()
        }
    }

    private fun translateVoiceText(text: String, target: String) {
        if (text.isBlank() || !VoicePolicy.allows(editor)) return
        val source = if (VoiceSettings.language(this) == VoiceSettings.URDU) "ur" else "en"
        val revision = editorGeneration
        try {
            translator?.close()
            translator = translationFactory(source, target)
            keyboard?.showVoiceMessage(R.string.voice_processing)
            translator?.translate(text) { result ->
                if (revision != editorGeneration) return@translate
                val value = result.orEmpty()
                keyboard?.showVoiceTranslation(value)
                keyboard?.showVoiceMessage(if (value.isBlank()) R.string.voice_error_insert else null)
            }
        } catch (_: RuntimeException) {
            keyboard?.showVoiceMessage(R.string.voice_error_insert)
        }
    }


    private fun canLearn() = options.enabled("learn_words") && VoicePolicy.allows(editor) &&
        editor.inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0 &&
        editor.inputType and android.text.InputType.TYPE_MASK_VARIATION !in listOf(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, android.text.InputType.TYPE_TEXT_VARIATION_URI)
    private fun recordLearnedAsync(word:String, language:Int, selected:Boolean=false) {
        if (!acceptingLearning || !LearnedWords.valid(word)) return
        // Capture the store generation before enqueueing.  A clear action from
        // Settings increments it; queued writes from before that action then
        // become no-ops instead of repopulating the just-cleared dictionary.
        val version = learned.version()
        try { learningWorker.execute {
            if (acceptingLearning) learned.record(word,language,selected,expectedVersion=version)
        } } catch (_:RejectedExecutionException) { }
    }
    private fun learnBoundary(context: String? = null) {
        val value=learningBuffer
        learningBuffer=""
        if(canLearn() && value.isNotEmpty() && LocalSuggestions.lastWord(context ?: before())==value && filtered(value))
            recordLearnedAsync(value,options.number("language"))
    }

    private fun editorAnchor():String {
        if(!VoicePolicy.allows(editor)) return ""
        val input=currentInputConnection ?: return ""
        return try { listOf(input.getTextBeforeCursor(128,0), input.getSelectedText(0)?.toString()?.take(2001), input.getTextAfterCursor(128,0)).joinToString("\u0000") } catch (_:RuntimeException) { "" }
    }
    private fun openWriting(mode:String) {
        closeWriting(); learningBuffer=""; deleteSelection.cancel(); deleting=false
        if(!VoicePolicy.allows(editor)) { keyboard?.showTextMessage("Writing tools are disabled in private fields."); return }
        writingMode=mode; writingAnchor=editorAnchor(); writingSource="ur"; writingTarget="en"
        writingCustom=""; customEditing=false; writingVariants=emptyList()
        keyboard?.showWritingTool(mode,writingSource,writingTarget,writingStyle)
        if(currentInputConnection!=null) {
            writingTargetText=EditorTarget.capture(currentInputConnection!!)
            writingDraft=writingTargetText?.original.orEmpty()
        }
        updateWriting(if(mode=="ai") "Generate sends only this draft to your configured provider." else if(mode=="translate") "Download the model once, then translate on this phone." else "Common Roman Urdu words convert offline; unknown words stay unchanged.")
    }
    private fun stopWritingRequest() {
        ++writingRevision; aiProvider?.cancel(); aiProvider=null
        try { translator?.close() } catch (_:RuntimeException) { }
        translator=null; writingBusy=false
    }
    private fun closeWriting() {
        stopWritingRequest(); writingMode=""; writingDraft=""; writingResult=""; writingVariants=emptyList(); writingCustom=""; customEditing=false; writingAnchor=""; writingTargetText=null; voiceToTranslation=false
        keyboard?.closeWritingTool()
    }
    private fun updateWriting(status:String="") { keyboard?.updateWritingTool(writingDraft,writingResult,status,writingBusy,writingVariants) }
    private fun writingKey(key:Key) {
        if(key is Key.Text || key==Key.Space || key==Key.Enter || key==Key.Delete) {
            stopWritingRequest(); writingResult=""; writingVariants=emptyList(); voiceToTranslation=false
            if(customEditing) {
                writingCustom=when(key) {
                    is Key.Text -> (writingCustom+state.display(key.text)).take(90)
                    Key.Space, Key.Enter -> (writingCustom+" ").take(90)
                    else -> if(writingCustom.isEmpty()) "" else writingCustom.substring(0,writingCustom.offsetByCodePoints(writingCustom.length,-1))
                }
                if(key is Key.Text) { state.afterCharacter(); keyboard?.updateShift() }
                keyboard?.showCustomInstruction(writingCustom); return
            }
            writingDraft=when(key) {
                is Key.Text -> (writingDraft+state.display(key.text)).take(2000)
                Key.Space -> (writingDraft+" ").take(2000)
                Key.Enter -> (writingDraft+"\n").take(2000)
                else -> if(writingDraft.isEmpty()) "" else writingDraft.substring(0,writingDraft.offsetByCodePoints(writingDraft.length,-1))
            }
            if(key is Key.Text) { state.afterCharacter(); keyboard?.updateShift() }
            updateWriting()
        } else when(key) {
            Key.Shift -> { state.toggleShift(); keyboard?.updateShift() }
            is Key.Mode -> { state.selectMode(key.mode); keyboard?.render(EditorPolicy.enterLabel(editor)) }
            Key.Language -> { options.setNumber("language",(options.number("language")+1)%3); keyboard?.render(EditorPolicy.enterLabel(editor)) }
            else -> Unit
        }
    }
    private fun writingAction(action:String) {
        if(writingMode.isEmpty()) return
        if(action.startsWith("style:") || action.startsWith("target:") || action.startsWith("source:")) {
            stopWritingRequest(); writingResult=""; writingVariants=emptyList()
            customEditing=false
            val value=action.substringAfter(':')
            if(action.startsWith("style:")) {
                if(AiTools.all.none { it.id==value }) return
                writingStyle=value
            } else {
                if(value !in com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages() && !(action.startsWith("source:") && value=="auto")) return
                if(action.startsWith("target:")) writingTarget=value
                else writingSource=value
            }
            keyboard?.showWritingTool(writingMode,writingSource,writingTarget,writingStyle); updateWriting()
            if(writingAutoStyle && action.startsWith("style:") && value!="custom") runWriting()
            if(value=="custom" && action.startsWith("style:")) { customEditing=true; keyboard?.showCustomInstruction(writingCustom) }
            return
        }
        if(action.startsWith("insert:") || action.startsWith("replace:") || action.startsWith("copy:")) {
            val value=writingVariants.getOrNull(action.substringAfter(':').toIntOrNull() ?: return) ?: return
            if(writingBusy) return
            if(action.startsWith("copy:")) {
                if(VoicePolicy.allows(editor)) getSystemService(ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("AI result",value))
                updateWriting("Copied result."); return
            }
            writingResult=value; insertWriting(replace=action.startsWith("replace:")); return
        }
        when(action) {
            "keyboard" -> keyboard?.toggleWritingKeyboard()
            "custom" -> {
                stopWritingRequest(); writingResult=""; writingVariants=emptyList(); customEditing=true
                keyboard?.showCustomInstruction(writingCustom)
            }
            "close" -> { closeWriting(); refreshSuggestions() }
            "selection" -> {
                stopWritingRequest(); writingResult=""; writingVariants=emptyList()
                val selected=try { currentInputConnection?.getSelectedText(0)?.toString().orEmpty() } catch (_:RuntimeException) { "" }
                if(selected.length>2000) { updateWriting("Select at most 2,000 characters so no selected text is lost."); return }
                writingDraft=selected
                writingTargetText=currentInputConnection?.let(EditorTarget::capture)
                writingAnchor=editorAnchor()
                updateWriting(if(writingDraft.isEmpty()) "Select text in your app first, or type a draft here." else "Selected text loaded. Insert will replace this selection.")
            }
            "swap", "target", "style" -> {
                stopWritingRequest(); writingResult=""; writingVariants=emptyList()
                if(action=="swap") { val old=writingSource; writingSource=writingTarget; writingTarget=old }
                if(action=="target") writingTarget=if(writingTarget=="en") "ur" else "en"
                if(action=="style") { val styles=listOf("rewrite","correct","formal","friendly","shorten","continue"); writingStyle=styles[(styles.indexOf(writingStyle)+1)%styles.size] }
                keyboard?.showWritingTool(writingMode,writingSource,writingTarget,writingStyle); updateWriting()
            }
            "download" -> {
                if(writingSource=="auto") { updateWriting("Choose a source language for offline model download. Detect Language uses your AI connection."); return }
                if(writingBusy) return
                stopWritingRequest(); val revision=writingRevision
                try {
                    translator=translationFactory(writingSource,writingTarget); writingBusy=true
                    updateWriting("Downloading translation model; Internet/data required. Google may collect SDK usage diagnostics.")
                    translator?.download { ok -> if(revision==writingRevision && writingMode=="translate") {
                        writingBusy=false; updateWriting(if(ok) "Model ready. Translation now works offline." else "Download failed. Check Internet and storage, then retry.")
                    } }
                } catch (_:RuntimeException) { writingBusy=false; updateWriting("Could not prepare translation. Please retry.") }
            }
            "run" -> if(customEditing) {
                if(writingCustom.isBlank()) return
                customEditing=false; writingStyle="custom"
                keyboard?.showWritingTool(writingMode,writingSource,writingTarget,writingStyle)
                updateWriting("Custom tool ready. Press Generate to send your draft.")
            } else runWriting()
            "insert" -> insertWriting(replace=true)
            "cancel" -> { stopWritingRequest(); updateWriting("Cancelled. Your text was kept.") }
            "retry" -> runWriting()
            "nearby" -> { writingTargetText=currentInputConnection?.let(EditorTarget::capture); writingDraft=writingTargetText?.original.orEmpty(); writingAnchor=editorAnchor(); updateWriting("Current sentence loaded.") }
        }
    }
    private fun runWriting(autoInsert:Boolean=false) {
        if(writingBusy || writingDraft.isBlank() || !VoicePolicy.allows(editor)) return
        stopWritingRequest(); val revision=writingRevision; val generation=editorGeneration
        val mode=writingMode; val draft=writingDraft; val connection=currentInputConnection
        writingResult=""; writingVariants=emptyList(); writingBusy=true; updateWriting(if(mode=="ai") "Generating…" else "Converting…")
        fun complete(result:String?, failure:String) {
            if(revision!=writingRevision || generation!=editorGeneration || connection!==currentInputConnection || mode!=writingMode) return
            writingBusy=false; voiceToTranslation=false
            writingVariants=if(result==null) emptyList() else if(mode=="ai") AiTools.variants(result) else listOf(result.take(8000))
            writingResult=writingVariants.firstOrNull().orEmpty()
            updateWriting(if(result==null) failure else "Review the result, then Replace Text.")
            if(result!=null && autoInsert) insertWriting(replace=false)
        }
        when(mode) {
            "roman" -> complete(RomanUrdu.convert(draft),"")
            "translate" -> if(writingSource=="auto") {
                val target=writingTarget
                try { mediaWorker.execute {
                    if(revision!=writingRevision) return@execute
                    var failure="Language detection needs an active AI connection. Retry or select Urdu/English manually."
                    val result=try {
                        val provider=AIService(AiConnection(this),aiRequestFactory).provider(); aiProvider=provider
                        if(revision!=writingRevision) { provider.cancel(); return@execute }
                        provider.translate(draft,"auto-detect",target)
                    } catch(e:AiFailure) { failure=e.problem.userMessage; null } catch (_:Exception) { null }
                    ui.post { complete(result,failure) }
                } } catch (_:java.util.concurrent.RejectedExecutionException) { complete(null,"The tool is busy. Retry.") }
            } else try {
                translator=translationFactory(writingSource,writingTarget)
                translator?.translate(draft) { result -> complete(result,"Translation unavailable. Download the model first, then retry.") }
            } catch (_:RuntimeException) { complete(null,"Could not start translation.") }
            "ai" -> {
                val settings=AiConnection(this)
                val provider=try { AIService(settings,aiRequestFactory).provider() } catch(e:AiFailure) { complete(null,e.problem.userMessage); return }
                aiProvider=provider
                val instruction=AiTools.instruction(writingStyle,writingTarget,writingCustom)
                try { mediaWorker.execute {
                    if(revision!=writingRevision) { provider.cancel(); return@execute }
                    var failure="AI temporarily unavailable. Retry or open Connection."
                    val result=try { provider.generateText(instruction,draft,variants=true) } catch(e:AiFailure) { failure=e.problem.userMessage; null } catch (_:Exception) { null }
                    ui.post { complete(result,failure) }
                } } catch (_:java.util.concurrent.RejectedExecutionException) { complete(null,"The tool is busy. Please retry.") }
            }
        }
    }
    private fun insertWriting(replace:Boolean=true) {
        if(writingResult.isEmpty() || !VoicePolicy.allows(editor)) return
        if(editorAnchor()!=writingAnchor) { updateWriting("The cursor or text changed. Reload the selection before inserting."); return }
        try {
            val connection=currentInputConnection ?: return
            val done=editHistory.transaction("writing",connection) { if(replace && writingTargetText!=null) writingTargetText!!.replace(connection,writingResult) else if(!replace && !connection.getSelectedText(0).isNullOrEmpty()) {
                val selected=connection.getSelectedText(1)
                if(selected==null || selected.length>2000) { updateWriting("Place the cursor where you want to insert, then retry."); false }
                else connection.commitText(android.text.TextUtils.concat(selected,writingResult),1)
            } else EditorCommands.type(connection,writingResult) }
            if(done) { closeWriting(); refreshSuggestions() }
            else updateWriting("This editor did not accept the result.")
        } catch (_:RuntimeException) { updateWriting("The editor is no longer available.") }
    }

    private fun before(connection: android.view.inputmethod.InputConnection? = currentInputConnection):String {
        if(!VoicePolicy.allows(editor)) return ""
        val input=connection ?: return ""
        return try {
            if(!input.getSelectedText(0).isNullOrEmpty()) "" else input.getTextBeforeCursor(128,0)?.toString().orEmpty()
        } catch (_:RuntimeException) { "" }
    }
    private fun refreshSuggestions() {
        ui.removeCallbacks(suggestionUpdate)
        // A zero delay keeps the strip responsive without placing work on the key
        // dispatch path.  Non-zero values remain useful for users who prefer less
        // frequent prediction updates.
        val delay = options.number("suggestion_delay").toLong()
        if (delay <= 0L) ui.post(suggestionUpdate) else ui.postDelayed(suggestionUpdate, delay)
    }
    private fun filtered(value:String, offensiveFilter:Boolean = options.enabled("offensive_filter")):Boolean = !offensiveFilter ||
        value.lowercase().split(Regex("[^\\p{L}]+" )).none { it in setOf("fuck","shit","bitch") }
    private fun updateSuggestions() {
        if(writingMode.isNotEmpty()) return
        val connection=currentInputConnection
        val generation=editorGeneration
        val sequence=inputSequence
        if(connection==null || !VoicePolicy.allows(editor) || editor.inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS!=0) {
            suggestionSequence.incrementAndGet()
            candidateBefore=""; candidateGeneration=generation; keyboard?.setTypingActive(false); keyboard?.showCandidates(emptyList()); return
        }
        val serial=suggestionSequence.incrementAndGet()
        val wordsEnabled=options.enabled("word_suggestions")
        val nextEnabled=options.enabled("next_word")
        val emojiEnabled=options.enabled("emoji_suggestions")
        val grammarEnabled=options.enabled("grammar_check")
        val smartReplies=options.enabled("smart_replies")
        val contactsEnabled=options.enabled("contacts")
        val clipboardEnabled=options.enabled("clipboard_suggestions")
        val clipboardItems=options.enabled("clipboard_items")
        val offensiveFilter=options.enabled("offensive_filter")
        val language=options.number("language")
        val roman=options.enabled("roman_suggestions")
        val learn=canLearn()
        val dictionary=if(wordsEnabled) options.dictionary() else emptyList()
        val contactSnapshot=contactNames.toList()
        // Do not call getTextBeforeCursor here. It is a synchronous Binder call
        // into the host editor and can stall the next touch during rapid typing.
        // The worker reads the snapshot and applies only the newest result.
        try {
            suggestionWorker.execute {
                val context=before(connection)
                val word=LocalSuggestions.lastWord(context)
                val values=LocalSuggestions.candidates(context,dictionary,wordsEnabled,nextEnabled,emojiEnabled,grammarEnabled,smartReplies,
                    if(learn) learned.matches(word,language) else emptyList(),language,roman).toMutableList()
                if(contactsEnabled && wordsEnabled && word.length>=2)
                    contactSnapshot.asSequence().filter { it.startsWith(word,true) }.take(2).forEach { values.add(Candidate(it,it,word)) }
                if(clipboardEnabled) {
                    val copied=clipboard.candidates(clipboardItems)
                    if(word.isEmpty()) values.addAll(0,copied) else values.addAll(copied)
                }
                val visibleValues=values.filter { filtered(it.text,offensiveFilter) }
                ui.post {
                    if(serial != suggestionSequence.get() || generation != editorGeneration || sequence != inputSequence || currentInputConnection !== connection || writingMode.isNotEmpty()) return@post
                    candidateBefore=context; candidateGeneration=generation
                    keyboard?.setTypingActive(hasTypedSinceStart && context.isNotEmpty())
                    keyboard?.showCandidates(visibleValues)
                }
            }
        } catch (_:RejectedExecutionException) { }
    }
    private fun chooseCandidate(candidate:Candidate) {
        voice.cancel()
        if(!VoicePolicy.allows(editor) && !candidate.emoji) return
        if(SystemClock.uptimeMillis()>candidate.expiresAt) { clipboard.clearPreview(); refreshSuggestions(); return }
        // Slot one mirrors the word currently being composed.  Selecting it
        // should keep the existing text, never append a duplicate copy.
        if (!candidate.emoji && candidate.replace.isNotEmpty() && candidate.text == candidate.replace) {
            refreshSuggestions(); return
        }
        val connection=currentInputConnection ?: return
        connection.beginBatchEdit()
        try {
            if(candidate.imageUri!=null) {
                val uri=candidate.imageUri.toUri()
                val mime=contentResolver.getType(uri) ?: "image/png"
                if(editor.contentMimeTypes?.any { ClipDescription.compareMimeTypes(mime,it) }!=true ||
                    !connection.commitContent(InputContentInfo(uri,ClipDescription("Clipboard image",arrayOf(mime)),null),android.view.inputmethod.InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null))
                    keyboard?.showTextMessage("This app does not accept clipboard images here.")
            } else {
                if(candidate.replace.isNotEmpty()) {
                    val context=before(connection)
                    if(candidateGeneration!=editorGeneration || context!=candidateBefore || !context.endsWith(candidate.replace)) { updateSuggestions(); return }
                    if(!connection.deleteSurroundingText(candidate.replace.length,0)) return
                }
                val prefix=if(candidate.emoji && before(connection).lastOrNull()?.isLetter()==true) " " else ""
                if(EditorCommands.type(connection,prefix+candidate.text) && candidate.emoji && VoicePolicy.allows(editor)) options.usedEmoji(candidate.text)
                if(!candidate.emoji && canLearn() && LearnedWords.valid(candidate.text)) {
                    recordLearnedAsync(candidate.text, options.number("language"), selected = candidate.replace.isNotEmpty())
                    learningBuffer=candidate.text
                } else learningBuffer=""
                state.afterCharacter(); keyboard?.updateShift()
            }
        } catch (_:RuntimeException) { keyboard?.showTextMessage("The editor or clipboard content is no longer available.") } finally { connection.endBatchEdit() }
        refreshSuggestions()
    }
    private fun space(connection:android.view.inputmethod.InputConnection?, localWord: String = "") {
        connection ?: return
        // The keyboard already knows the word it just composed.  Prefer that
        // local prefix and only read the editor when a feature genuinely needs
        // surrounding context (grammar or a pre-existing word).
        val value = (localWord.ifBlank { learningBuffer }).take(64)
        learningBuffer=""
        typedPrefix=""
        if (!richSpacingEnabled()) {
            if (canLearn() && LearnedWords.valid(value) && filtered(value)) recordLearnedAsync(value,options.number("language"))
            val time=SystemClock.uptimeMillis()
            if (EditorCommands.type(connection," ")) {
                spaceTime=time
                lastSpaceHadWord=value.isNotEmpty()
                updateAutomaticShiftAsync(connection)
            }
            return
        }
        val needsContext = options.enabled("grammar_check") ||
            (value.isEmpty() && (options.enabled("auto_correction") || options.enabled("spell_check")))
        val context=if (needsContext) before(connection) else ""
        val word=if(value.isNotEmpty()) value else LocalSuggestions.lastWord(context)
        if (canLearn() && LearnedWords.valid(word) && filtered(word)) recordLearnedAsync(word,options.number("language"))
        if(VoicePolicy.allows(editor) && options.enabled("auto_correction")) {
            LocalSuggestions.corrections[word.lowercase()]?.let { replacement ->
                if(connection.deleteSurroundingText(word.length,0)) EditorCommands.type(connection,LocalSuggestions.matchCase(word,replacement))
            }
        } else if(VoicePolicy.allows(editor) && options.enabled("spell_check") && LocalSuggestions.corrections.containsKey(word.lowercase())) {
            val span=SpannableString(word)
            span.setSpan(SuggestionSpan(this,arrayOf(LocalSuggestions.corrections.getValue(word.lowercase())),SuggestionSpan.FLAG_EASY_CORRECT or SuggestionSpan.FLAG_MISSPELLED),0,word.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(connection.deleteSurroundingText(word.length,0)) connection.commitText(span,1)
        }
        if(VoicePolicy.allows(editor) && options.enabled("grammar_check")) {
            LocalSuggestions.grammar.entries.firstOrNull { context.lowercase().endsWith(it.key) }?.let { rule ->
                val original=context.takeLast(rule.key.length)
                val span=SpannableString(original)
                if(Build.VERSION.SDK_INT>=31) span.setSpan(SuggestionSpan(this,arrayOf(rule.value),SuggestionSpan.FLAG_GRAMMAR_ERROR),0,original.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else span.setSpan(android.text.style.UnderlineSpan(),0,original.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if(connection.deleteSurroundingText(original.length,0)) connection.commitText(span,1)
            }
        }
        val time=SystemClock.uptimeMillis()
        if(options.enabled("double_space") && spaceTime>=0 && time-spaceTime<700 && lastSpaceHadWord) {
            if(connection.deleteSurroundingText(1,0)) EditorCommands.type(connection,if(options.number("language")==1) "۔ " else ". ")
            spaceTime=-1; lastSpaceHadWord=false
        } else if (EditorCommands.type(connection," ")) {
            spaceTime=time; lastSpaceHadWord=word.isNotEmpty()
        }
        updateAutomaticShiftAsync(connection)
    }
    private fun tool(name:String) {
        val wasListening = voice.active
        if(name=="voice") { toggleVoice(); return }
        if(name=="voice:retry") { voice.cancel(); keyboard?.showVoicePreview(""); toggleVoice(); return }
        if(name=="voice:delete") {
            var cannotRemove=false
            voice.cancel()
            if(voiceCommitted) {
                val raw=super.getCurrentInputConnection()
                cannotRemove=raw==null || voiceEditId==null || !editHistory.undo(raw,voiceEditId,discard=true)
            }
            voiceCommitted=false; voiceEditId=null; lastVoiceText=""; keyboard?.showVoicePreview(""); keyboard?.showVoiceTranslation(""); keyboard?.showVoiceDeleted()
            if(cannotRemove) keyboard?.showTextMessage("The editor changed or cannot safely remove this transcription. Select it to delete manually.")
            if(writingMode.isNotEmpty()) { writingDraft=""; writingResult=""; writingVariants=emptyList(); voiceToTranslation=false; stopWritingRequest(); updateWriting("Voice draft deleted.") }
            refreshSuggestions(); return
        }
        if(name.startsWith("voice:translate-target:")) {
            voiceTranslateTarget = name.substringAfterLast(':').takeIf { it == "en" || it == "ur" } ?: "en"
            voiceTranslateAfter = true
            if (lastVoiceText.isNotBlank()) {
                voiceTranslateAfter = false
                translateVoiceText(lastVoiceText, voiceTranslateTarget)
            } else {
                keyboard?.showTextMessage(getString(R.string.voice_listening))
                if (wasListening) toggleVoice()
            }
            return
        }
        if(name=="writing:voice") { writingDraft=""; writingTargetText=null; writingAnchor=editorAnchor(); voiceToTranslation=true; toggleVoice(); return }
        voice.cancel()
        if(name.startsWith("writing:")) { writingAction(name.substringAfter(':')); return }
        if(name.startsWith("voice-language:")) {
            if(writingMode!="translate") voiceToTranslation=false
            else writingSource=when(name.substringAfter(':')) { VoiceSettings.URDU -> "ur"; VoiceSettings.ENGLISH -> "en"; else -> "auto" }
            VoiceSettings.setLanguage(this,name.substringAfter(':'))
            if(name.substringAfter(':')!=VoiceSettings.MIXED) VoiceSettings.setMixedPrimary(this,name.substringAfter(':'))
            keyboard?.showVoiceLanguages(name.substringAfter(':')); if(wasListening) toggleVoice(); return
        }
        if(name in listOf("translate","ai","roman","grammar","rewrite","tone")) {
            writingAutoStyle=name in listOf("rewrite","tone")
            if(name=="grammar") writingStyle="correct" else if(name=="rewrite") writingStyle="rewrite" else if(name=="tone") writingStyle="polite" else if(name=="ai") writingStyle="ask"
            openWriting(if(name in listOf("grammar","rewrite","tone")) "ai" else name)
            keyboard?.setActiveTool(when(name) { "grammar" -> "Grammar"; "rewrite" -> "Rewrite"; "tone" -> "Tone"; "translate" -> "Translation"; else -> "AI" })
            if(name=="grammar" && writingDraft.isNotBlank()) runWriting()
            return
        }
        closeWriting(); learningBuffer=""
        if(name.startsWith("clipboard:") && name!="clipboard:refresh") {
            if(!VoicePolicy.allows(editor)) return
            if(name.startsWith("clipboard:clear:")) {
                if(name.endsWith(":UNPINNED")) clipboard.history.clearUnpinned()
                else ClipSection.entries.firstOrNull { it.name==name.substringAfterLast(':') }?.let(clipboard.history::clear)
            } else {
                val operation=name.substringAfter("clipboard:").substringBefore(':')
                val id=name.substringAfterLast(':')
                val clip=clipboard.history.find(id) ?: return
                when(operation) {
                    "paste" -> { currentInputConnection?.let { EditorCommands.type(it,clip.text) }; keyboard?.showSettings(""); refreshSuggestions(); return }
                    "copy" -> getSystemService(ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Raabta clip",clip.text))
                    "pin" -> clipboard.history.pin(id,!clip.pinned)
                    "delete" -> clipboard.history.delete(id)
                }
            }
            keyboard?.showClipboard(clipboard.history); refreshSuggestions(); return
        }
        when(name) {
            "tools" -> { keyboard?.showSettings("Tools"); keyboard?.setHistoryAvailability(editHistory.canUndo,editHistory.canRedo) }
            "themes" -> keyboard?.showSettings("Theme")
            "languages" -> keyboard?.showSettings("Languages")
            "resize" -> keyboard?.showResizeMode()
            "one_handed" -> keyboard?.showSettings("Preferences")
            "floating" -> { options.set("floating",!options.enabled("floating")); setInputView(onCreateInputView()) }
            "theme:import" -> startActivity(Intent(this,dev.raabta.keyboard.setup.ThemeImportActivity::class.java).putExtra("from_keyboard",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "voice:retry" -> toggleVoice()
            "settings" -> { keyboard?.showSettings(""); startActivity(Intent(this,dev.raabta.keyboard.setup.KeyboardSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "settings:search" -> startActivity(Intent(this,dev.raabta.keyboard.setup.KeyboardSettingsActivity::class.java)
                .putExtra("page", "Search")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "connections" -> { keyboard?.showConnections(AiConnection(this)); keyboard?.setActiveTool("Tools") }
            "connection:test" -> testProvider()
            "connection:toggle" -> { val settings=AiConnection(this); settings.setActive(!settings.active()); keyboard?.showConnections(settings) }
            "editing" -> { keyboard?.showEditingTools(); keyboard?.setHistoryAvailability(editHistory.canUndo,editHistory.canRedo) }
            "emoji" -> keyboard?.showEmojiPicker()
            "media" -> keyboard?.showMediaPicker()
            "clipboard", "clipboard:refresh" -> {
                if(VoicePolicy.allows(editor)) { clipboard.refresh(); keyboard?.showClipboard(clipboard.history) }
                else keyboard?.showClipboard(null)
            }
            "language" -> {
                options.setNumber("language",(options.number("language")+1)%3)
                keyboard?.cancelRepeats(); setInputView(onCreateInputView())
            }
            "dictionary_add" -> { val word=LocalSuggestions.lastWord(before()); if(word.isNotEmpty()) options.addWord(word); refreshSuggestions() }
            "clear" -> { learned.clear(); clipboard.clear(); learningBuffer=""; refreshSuggestions() }
            "learned_clear" -> { learned.clear(); learningBuffer=""; refreshSuggestions() }
            "tools:reset" -> { dev.raabta.keyboard.ime.ToolOrder(this).reset(); keyboard?.showSettings("Tools") }
            "translation_delete" -> { LocalTranslation.deleteModels(this) { success -> keyboard?.showTextMessage(if(success) "Urdu translation model removed." else "Could not remove the translation model.") } }
            "ai:connection" -> startActivity(Intent(this,AiConnectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "ai:clear" -> AiConnection(this).clear()
            "permissions" -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:$packageName".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "share" -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain"; putExtra(Intent.EXTRA_TEXT,"Raabta Keyboard v${appVersionName()}: https://github.com/gityy34/keybord-ai-/releases/tag/v${appVersionName()}")
            },"Share Raabta").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> if(name.startsWith("edit:")) {
                val connection=currentInputConnection
                val command=name.substringAfter(':')
                try {
                    if(command in listOf("undo","redo")) {
                        val raw=super.getCurrentInputConnection()
                        val success=raw!=null && VoicePolicy.allows(editor) && if(command=="undo") editHistory.undo(raw) else editHistory.redo(raw)
                        if(!success) keyboard?.showTextMessage("No supported edit is available to $command.")
                        keyboard?.setHistoryAvailability(editHistory.canUndo,editHistory.canRedo); refreshSuggestions(); return
                    }
                    val contextAction=when(command) { "selectAll" -> android.R.id.selectAll; "copy" -> android.R.id.copy; "cut" -> android.R.id.cut; "paste" -> android.R.id.paste; "undo" -> android.R.id.undo; "redo" -> android.R.id.redo; else -> null }
                    if(contextAction!=null) {
                        if(VoicePolicy.allows(editor) && connection?.performContextMenuAction(contextAction)!=true) keyboard?.showTextMessage("This app does not support that editing action.")
                    } else if(command in listOf("left","right")) CursorNavigation.move(connection,if(command=="left") -1 else 1)
                    else {
                        val code=if(command=="home") KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END
                        connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code)); connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))
                    }
                    keyboard?.setHistoryAvailability(editHistory.canUndo,editHistory.canRedo); refreshSuggestions()
                } catch (_:RuntimeException) { keyboard?.showTextMessage("The editor is no longer available.") }
            } else if(name.startsWith("media:")) {
                val parts=name.split(':')
                val generation=editorGeneration; val connection=currentInputConnection; val context=before()
                if(VoicePolicy.allows(editor) && parts.size==3 && parts[1] in listOf("sticker","anim") && parts[2] in listOf("smile","heart")) {
                    val mime=if(parts[1]=="anim") "image/gif" else "image/png"
                    if(editor.contentMimeTypes?.any { ClipDescription.compareMimeTypes(mime,it) }!=true) keyboard?.showTextMessage("This app does not accept stickers or GIFs here.")
                    else try { mediaWorker.execute {
                        try {
                            val uri=LocalMediaProvider.create(this,parts[1],parts[2])
                            ui.post { if(showing && generation==editorGeneration && connection===currentInputConnection && context==before()) chooseCandidate(Candidate("Local media","",imageUri=uri.toString())) }
                        } catch (_:RuntimeException) { ui.post { if(generation==editorGeneration) keyboard?.showTextMessage("Could not prepare this media item.") } }
                    } } catch (_:java.util.concurrent.RejectedExecutionException) { keyboard?.showTextMessage("Media is busy. Please try again.") }
                }
            } else if(name.startsWith("permission:")) startActivity(Intent(this,OptionalPermissionActivity::class.java).putExtra("setting",name.substringAfter(':')).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    override fun onComputeInsets(outInsets:Insets) {
        super.onComputeInsets(outInsets)
        if(keyboard?.floating==true) {
            val height=window.window?.decorView?.height ?: return
            outInsets.contentTopInsets=height; outInsets.visibleTopInsets=height
            outInsets.touchableInsets=Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(keyboard!!.contentBounds())
        }
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        if(keyCode==KeyEvent.KEYCODE_BACK) {
            if(writingMode.isNotEmpty()) { voice.cancel(); closeWriting(); return true }
            if(keyboard?.backFromPanel()==true) return true
        }
        return super.onKeyDown(keyCode,event)
    }
    private fun testProvider() {
        val current=keyboard ?: return
        val settings=AiConnection(this)
        if(!settings.configured()) { current.showTextMessage("Save a connection first using Change Provider."); return }
        val revision=writingRevision; val generation=editorGeneration
        current.findViewWithTag<View>("ConnectionTest")?.isEnabled=false
        current.showTextMessage("Connecting…")
        try { mediaWorker.execute {
            if(revision!=writingRevision) return@execute
            val failure=try {
                val provider=AIService(settings,aiRequestFactory).provider(forTest=true); aiProvider=provider
                if(revision!=writingRevision) { provider.cancel(); return@execute }
                provider.testConnection(); null
            } catch(e:AiFailure) { e.problem.userMessage } catch (_:Exception) { "Connection could not be verified. Retry." }
            ui.post { if(current===keyboard && generation==editorGeneration && revision==writingRevision) {
                settings.recordTest(failure==null); current.showConnections(settings); current.showTextMessage(failure ?: "Connected successfully.")
            } }
        } } catch (_:java.util.concurrent.RejectedExecutionException) { current.findViewWithTag<View>("ConnectionTest")?.isEnabled=true; current.showTextMessage("Busy. Retry shortly.") }
    }
    private fun hideTools() {
        showing=false; closeWriting(); learningBuffer=""; typedPrefix=""; lastSpaceHadWord=false; deleteSelection.cancel(); deleting=false; keyboard?.hideVoiceLanguages(); ui.removeCallbacks(suggestionUpdate); clipboard.clearPreview(); contactNames=emptyList()
        editHistory.clear(); voiceEditId=null
        try { getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipboardListener); contentResolver.unregisterContentObserver(screenshotObserver) } catch (_:RuntimeException) { }
    }
    private fun loadContacts() {
        if(!options.enabled("contacts") || !VoicePolicy.allows(editor) || checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) { contactNames=emptyList(); return }
        val generation=editorGeneration
        worker.execute {
            val names=mutableListOf<String>()
            try {
                contentResolver.query(ContactsContract.Contacts.CONTENT_URI,arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),null,null,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)?.use { cursor ->
                    while(names.size<1000 && cursor.moveToNext()) cursor.getString(0)?.takeIf { it.isNotBlank() }?.let(names::add)
                }
            } catch (_:RuntimeException) { }
            ui.post { if(showing && generation==editorGeneration && options.enabled("contacts") && VoicePolicy.allows(editor)) { contactNames=names; refreshSuggestions() } }
        }
    }
    private fun checkScreenshots() {
        if(!showing || !VoicePolicy.allows(editor) || !options.enabled("screenshots")) return
        val generation=editorGeneration
        val after=screenshotSince
        worker.execute {
            var uri:String?=null; var date=after
            try {
                contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DATE_ADDED),
                    "${MediaStore.Images.Media.DATE_ADDED} > ? AND LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE ?",arrayOf(after.toString(),"%screenshot%"),"${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                    if(cursor.moveToFirst()) { uri=android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)).toString(); date=cursor.getLong(1) }
                }
            } catch (_:RuntimeException) { }
            ui.post { if(showing && generation==editorGeneration && options.enabled("screenshots") && VoicePolicy.allows(editor)) {
                screenshotSince=date; uri?.let { clipboard.screenshot(it); refreshSuggestions() }
            } }
        }
    }

    private fun voiceError(error: VoiceError) {
        keyboard?.showVoiceFailure()
        val text = when (error) {
        VoiceError.PERMISSION -> R.string.voice_permission_denied
        VoiceError.UNAVAILABLE -> R.string.voice_error_unavailable
        VoiceError.NETWORK -> R.string.voice_error_network
        VoiceError.NO_SPEECH -> R.string.voice_error_no_speech
        VoiceError.BUSY -> R.string.voice_error_busy
        VoiceError.LANGUAGE -> R.string.voice_error_language
        VoiceError.AUDIO -> R.string.voice_error_audio
        VoiceError.TIMEOUT -> R.string.voice_error_timeout
        VoiceError.INTERNAL -> R.string.voice_error_internal
        }
        keyboard?.showVoiceMessage(text)
        if (keyboard == null) message(text)
    }

    private fun message(text: Int) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
