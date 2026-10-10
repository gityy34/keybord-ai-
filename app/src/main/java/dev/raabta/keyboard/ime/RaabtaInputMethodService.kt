package dev.raabta.keyboard.ime

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
import dev.raabta.keyboard.ui.KeyboardOptions
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
import android.content.ClipDescription
import android.text.SpannableString
import android.text.Spanned
import android.text.style.SuggestionSpan
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class RaabtaInputMethodService : InputMethodService() {
    private val state = KeyboardState()
    private var keyboard: KeyboardView? = null
    private var editor = EditorInfo()
    private var editorGeneration = 0
    private var awaitingVoicePermission = false
    private val options by lazy { KeyboardOptions(this) }
    private val clipboard by lazy { ClipboardPreview(this) }
    private val ui=Handler(Looper.getMainLooper())
    private val mediaWorker=ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(4),ThreadPoolExecutor.AbortPolicy())
    private val worker=ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(1),ThreadPoolExecutor.DiscardOldestPolicy())
    private var contactNames=emptyList<String>()
    private var screenshotSince=0L
    private var showing=false
    private var spaceTime=-1L
    private var candidateBefore=""
    private var candidateGeneration=0
    private val suggestionUpdate=Runnable { updateSuggestions() }
    private val clipboardListener=ClipboardManager.OnPrimaryClipChangedListener {
        if(showing && VoicePolicy.allows(editor) && options.enabled("clipboard_suggestions")) { clipboard.refresh(); refreshSuggestions() }
    }
    private val screenshotObserver=object:ContentObserver(ui) {
        override fun onChange(selfChange:Boolean) { checkScreenshots() }
    }
    internal var speechEngineFactory = SpeechEngineFactory { listener -> DeviceSpeechEngine(this, listener) }
    private val voice by lazy {
        VoiceController(SpeechEngineFactory { speechEngineFactory.create(it) }, { keyboard?.setVoiceState(it) }, ::voiceError,
            { keyboard?.showVoicePreview(it) }, { VoiceSettings.pauseMillis(this) }, { VoiceSettings.finalWaitMillis(this) })
    }

    override fun onCreateInputView(): View = KeyboardView(
        this, state, ::handleKey,
        onCaps = { voice.cancel(); state.lockShift(); keyboard?.updateShift() },
        onSettings = { voice.cancel(); keyboard?.showSettings() },
        onSwitch = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
        onVoice = ::toggleVoice,
        onCancelVoice = { voice.cancel() },
        onSettingsChanged = { page ->
            voice.cancel(); keyboard?.cancelRepeats()
            setInputView(onCreateInputView())
            keyboard?.showSettings(page)
            refreshSuggestions(); loadContacts()
        },
        onTool = ::tool,
        onCandidate = ::chooseCandidate,
    ).also {
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
        if(VoicePolicy.allows(info) && options.enabled("clipboard_suggestions")) clipboard.refresh()
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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        voice.cancel()
        keyboard?.cancelRepeats()
        super.onConfigurationChanged(newConfig)
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        voice.cancel()
        ++editorGeneration
        keyboard?.cancelRepeats()
        editor = attribute
        val capsMode = currentInputConnection?.getCursorCapsMode(attribute.inputType) ?: 0
        state.reset(EditorPolicy.isNumeric(attribute), options.enabled("auto_capitalization") && capsMode != 0)
        candidateBefore=""; clipboard.clear(); contactNames=emptyList(); spaceTime=-1
        keyboard?.showCandidates(emptyList())
        ui.removeCallbacks(suggestionUpdate)
        keyboard?.render(EditorPolicy.enterLabel(editor))
        keyboard?.setVoiceAllowed(VoicePolicy.allows(editor))
    }

    private fun handleKey(key: Key) {
        voice.cancel()
        val connection = currentInputConnection
        when (key) {
            is Key.Text -> {
                if (EditorCommands.type(connection, state.display(key.text) + if(VoicePolicy.allows(editor) && options.enabled("auto_space") && key.text in listOf(".",",","?","!","؟","،","۔")) " " else "")) {
                    val previousShift = state.shift
                    state.afterCharacter()
                    if (previousShift != state.shift) keyboard?.updateShift()
                }
            }
            Key.Space -> space(connection)
            Key.Delete -> EditorCommands.delete(connection)
            Key.Enter -> EditorCommands.enter(connection, editor)
            Key.Emoji -> keyboard?.showEmojiPicker()
            Key.Language -> tool("language")
            is Key.Cursor -> CursorNavigation.move(connection,key.steps)
            is Key.Glide -> if(VoicePolicy.allows(editor)) LocalSuggestions.glide(key.path,options.dictionary())?.let { EditorCommands.type(connection,state.display(it)); state.afterCharacter(); keyboard?.updateShift() }
            Key.Shift -> { state.toggleShift(); keyboard?.updateShift() }
            is Key.Mode -> { state.selectMode(key.mode); keyboard?.render(EditorPolicy.enterLabel(editor)) }
        }
        if(key is Key.Text && key.text=="'" && options.enabled("apostrophe_space") && state.mode!=KeyboardMode.LETTERS) {
            state.selectMode(KeyboardMode.LETTERS); keyboard?.render(EditorPolicy.enterLabel(editor))
        }
        if(key!=Key.Space) spaceTime=-1
        refreshSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInput() {
        voice.cancel()
        ++editorGeneration
        keyboard?.cancelRepeats()
        editor = EditorInfo()
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
        voice.cancel()
        keyboard?.cancelRepeats()
        hideTools(); worker.shutdownNow(); mediaWorker.shutdownNow(); contactNames=emptyList()
        keyboard = null
        super.onDestroy()
    }

    override fun onWindowHidden() {
        voice.cancel(); hideTools(); keyboard?.cancelRepeats()
        super.onWindowHidden()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) { voice.cancel(); refreshSuggestions() }
    }

    private fun toggleVoice() {
        if (voice.active) { voice.stop(); return }
        if (!VoicePolicy.allows(editor)) { message(R.string.voice_blocked); return }
        if (VoiceSettings.language(this) == VoiceSettings.MIXED && Build.VERSION.SDK_INT < 34) {
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
        voice.start(VoiceSettings.language(this)) { result ->
            if (generation != editorGeneration || currentInputConnection !== connection || !VoicePolicy.allows(editor)) {
                message(R.string.voice_error_insert)
                return@start
            }
            if (!EditorCommands.type(connection, result)) message(R.string.voice_error_insert)
            refreshSuggestions()
        }
    }


    private fun before():String {
        if(!VoicePolicy.allows(editor)) return ""
        val connection=currentInputConnection ?: return ""
        return try {
            if(!connection.getSelectedText(0).isNullOrEmpty()) "" else connection.getTextBeforeCursor(128,0)?.toString().orEmpty()
        } catch (_:RuntimeException) { "" }
    }
    private fun refreshSuggestions() {
        ui.removeCallbacks(suggestionUpdate)
        ui.postDelayed(suggestionUpdate,40)
    }
    private fun filtered(value:String):Boolean = !options.enabled("offensive_filter") ||
        value.lowercase().split(Regex("[^\\p{L}]+" )).none { it in setOf("fuck","shit","bitch") }
    private fun updateSuggestions() {
        if(!VoicePolicy.allows(editor) || editor.inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS!=0) {
            candidateBefore=""; keyboard?.showCandidates(emptyList()); return
        }
        val context=before(); candidateBefore=context; candidateGeneration=editorGeneration
        val values=LocalSuggestions.candidates(context,options.dictionary(),options.enabled("word_suggestions"),
            options.enabled("next_word"),options.enabled("emoji_suggestions"),options.enabled("grammar_check"),options.enabled("smart_replies")).toMutableList()
        val word=LocalSuggestions.lastWord(context)
        if(options.enabled("contacts") && options.enabled("word_suggestions") && word.length>=2)
            contactNames.asSequence().filter { it.startsWith(word,true) }.take(2).forEach { values.add(Candidate(it,it,word)) }
        if(options.enabled("clipboard_suggestions")) values.addAll(clipboard.candidates(options.enabled("clipboard_items")))
        keyboard?.showCandidates(values.filter { filtered(it.text) })
    }
    private fun chooseCandidate(candidate:Candidate) {
        voice.cancel()
        if(!VoicePolicy.allows(editor) && !candidate.emoji) return
        if(SystemClock.uptimeMillis()>candidate.expiresAt) { clipboard.clear(); refreshSuggestions(); return }
        val connection=currentInputConnection ?: return
        try {
            if(candidate.imageUri!=null) {
                val uri=Uri.parse(candidate.imageUri)
                val mime=contentResolver.getType(uri) ?: "image/png"
                if(editor.contentMimeTypes?.any { ClipDescription.compareMimeTypes(mime,it) }!=true ||
                    !connection.commitContent(InputContentInfo(uri,ClipDescription("Clipboard image",arrayOf(mime)),null),android.view.inputmethod.InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null))
                    keyboard?.showTextMessage("This app does not accept clipboard images here.")
            } else {
                if(candidate.replace.isNotEmpty()) {
                    if(candidateGeneration!=editorGeneration || before()!=candidateBefore || !before().endsWith(candidate.replace)) { updateSuggestions(); return }
                    if(!connection.deleteSurroundingText(candidate.replace.length,0)) return
                }
                val prefix=if(candidate.emoji && before().lastOrNull()?.isLetter()==true) " " else ""
                if(EditorCommands.type(connection,prefix+candidate.text) && candidate.emoji && VoicePolicy.allows(editor)) options.usedEmoji(candidate.text)
                state.afterCharacter(); keyboard?.updateShift()
            }
        } catch (_:RuntimeException) { keyboard?.showTextMessage("The editor or clipboard content is no longer available.") }
        refreshSuggestions()
    }
    private fun space(connection:android.view.inputmethod.InputConnection?) {
        connection ?: return
        val context=before()
        val word=LocalSuggestions.lastWord(context)
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
        if(options.enabled("double_space") && spaceTime>=0 && time-spaceTime<700 && context.endsWith(" ") && context.getOrNull(context.length-2)?.isLetterOrDigit()==true) {
            if(connection.deleteSurroundingText(1,0)) EditorCommands.type(connection,if(options.number("language")==1) "۔ " else ". ")
            spaceTime=-1
        } else { EditorCommands.type(connection," "); spaceTime=time }
        if(options.enabled("auto_capitalization") && state.shift!=ShiftMode.LOCKED) {
            state.setAutomaticShift(connection.getCursorCapsMode(editor.inputType)!=0); keyboard?.updateShift()
        }
    }
    private fun tool(name:String) {
        voice.cancel()
        when(name) {
            "tools" -> keyboard?.showSettings("Tools")
            "editing" -> keyboard?.showEditingTools()
            "emoji" -> keyboard?.showEmojiPicker()
            "media" -> keyboard?.showMediaPicker()
            "clipboard" -> {
                if(VoicePolicy.allows(editor)) { clipboard.refresh(); keyboard?.showClipboard(clipboard.candidates(options.enabled("clipboard_items"))) }
                else keyboard?.showClipboard(emptyList())
            }
            "language" -> {
                options.setNumber("language",(options.number("language")+1)%3)
                keyboard?.cancelRepeats(); setInputView(onCreateInputView())
            }
            "dictionary_add" -> { val word=LocalSuggestions.lastWord(before()); if(word.isNotEmpty()) options.addWord(word); refreshSuggestions() }
            "clear" -> { clipboard.clear(); refreshSuggestions() }
            "permissions" -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "share" -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain"; putExtra(Intent.EXTRA_TEXT,"Raabta Keyboard: https://github.com/gityy34/keybord-ai-/tree/apk-keyboard-0.3.0")
            },"Share Raabta").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> if(name.startsWith("edit:")) {
                val connection=currentInputConnection
                val command=name.substringAfter(':')
                try {
                    val contextAction=when(command) { "selectAll" -> android.R.id.selectAll; "copy" -> android.R.id.copy; "cut" -> android.R.id.cut; "paste" -> android.R.id.paste; "undo" -> android.R.id.undo; "redo" -> android.R.id.redo; else -> null }
                    if(contextAction!=null) {
                        if(VoicePolicy.allows(editor) && connection?.performContextMenuAction(contextAction)!=true) keyboard?.showTextMessage("This app does not support that editing action.")
                    } else if(command in listOf("left","right")) CursorNavigation.move(connection,if(command=="left") -1 else 1)
                    else {
                        val code=if(command=="home") KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END
                        connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code)); connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))
                    }
                    refreshSuggestions()
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
        if(keyCode==KeyEvent.KEYCODE_BACK && keyboard?.backFromPanel()==true) return true
        return super.onKeyDown(keyCode,event)
    }
    private fun hideTools() {
        showing=false; ui.removeCallbacks(suggestionUpdate); clipboard.clear(); contactNames=emptyList()
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
