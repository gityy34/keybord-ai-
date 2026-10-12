package dev.raabta.keyboard.ui

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.widget.*
import dev.raabta.keyboard.voice.VoiceSettings
import dev.raabta.keyboard.ime.LearnedWords
import dev.raabta.keyboard.tools.AiConnection

/** Shared categories for full-screen settings and the in-IME tools menu. */
class KeyboardSettingsPanel @JvmOverloads constructor(
    context: Context, private val page: String = "Raabta settings", private val navigate: (String) -> Unit = {},
    private val changed: () -> Unit = {}, private val action: (String) -> Unit = {},
) : LinearLayout(context) {
    private val options = KeyboardOptions(context)
    private val palette = Palette.from(context)
    private val body = LinearLayout(context).apply { orientation = VERTICAL; setPadding(context.dp(18), 0, context.dp(18), context.dp(12)) }
    init {
        orientation = VERTICAL
        tag = "SettingsPanel"
        setBackgroundColor(palette.background)
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(palette.surface) }
        header.addView(dev.raabta.keyboard.ime.ToolbarButton(context).apply {
            text = "‹"; contentDescription = "Back"; tag = "SettingsBack"
            setTextColor(palette.text); background=rounded(palette.surface,0f); setOnClickListener { navigate(if (page == "Raabta settings") "" else "Raabta settings") }
        }, LayoutParams(context.dp(52), context.dp(52)))
        header.addView(label(page, 21f), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(header)
        addView(ScrollView(context).apply { tag="SettingsScroll"; addView(body); isFillViewport=true }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        when (page) {
            "Raabta settings" -> home()
            "Preferences", "Keyboard" -> preferences()
            "Connections" -> {
                val saved=AiConnection(context)
                note("Provider: ${saved.provider().label}\nModel: ${saved.model().ifEmpty { "None" }}\nStatus: ${saved.status()}\nConnection type: HTTPS")
                row("Manage Connections", "Test, activate, deactivate or change your provider and encrypted API key.") { action("ai:connection") }
            }
            "Advanced" -> { row("Glide typing", "Gesture settings") { navigate("Glide typing") }; row("Dictionary", "Local learned and saved words") { navigate("Dictionary") } }
            "Appearance" -> { row("Theme", "Keyboard colours") { navigate("Theme") }; row("Resize", "Height and font controls") { navigate("Preferences") } }
            "Voice" -> voice()
            "Tools" -> {
                row("Settings", "Full keyboard settings") { action("settings") }
                row("Connections", "Provider status, test and encrypted keys") { action("connections") }
                row("AI Chat", "Ask your configured AI provider") { action("ai") }
                row("Language", "Switch keyboard layout") { action("language") }
                row("Theme", "Choose keyboard colours.") { navigate("Theme") }
                row("Resize", "Choose keyboard height.") { navigate("Preferences") }
                row("Emoji", "Open the emoji picker.") { action("emoji") }
                row("One-handed", "Choose left or right in Preferences.") { navigate("Preferences") }
                row("Text editing", "Move, select, copy, cut or paste text.") { action("editing") }
                row("Share Raabta", "Share the APK page.") { action("share") }
                toggle("floating", "Floating", "Compact keyboard over your app; drag its title to reposition.")
                row("Undo", "Ask the current app to undo its last edit.") { action("edit:undo") }
                row("Translation", "Urdu ↔ English, including voice-to-translation.") { action("translate") }
                row("AI Write", "Rewrite, correct, shorten or continue using your chosen provider.") { action("ai") }
                row("Roman Urdu → Urdu", "Convert common Roman Urdu words locally.") { action("roman") }
                row("Clipboard", "Open clipboard previews.") { action("clipboard") }
            }
            "Corrections and suggestions" -> corrections()
            "Emojis, stickers and GIFs" -> emoji()
            "Clipboard" -> clipboard()
            "Theme" -> choices("Theme", KeyboardTheme.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }, KeyboardPreferences.load(context).theme.ordinal) {
                KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(theme = KeyboardTheme.entries[it])); changed()
            }
            "Languages" -> {
                section("Keyboard layouts")
                choices("Layout", listOf("English (QWERTY)", "Urdu (اردو)", "Roman Urdu (QWERTY)"), context.getSharedPreferences("keyboard_options", 0).getInt("language", 0)) {
                    options.setNumber("language", it); changed()
                }
                row("Switch language", "Use the globe key to cycle these layouts.") { action("language") }
            }
            "Voice typing" -> voice()
            "Writing tools", "AI" -> {
                section("Translation")
                row("Open translation", "Type or speak Urdu and insert English; reverse direction is available.") { action("translate") }
                note("Translation runs on-device after you explicitly download the Urdu model. Google ML Kit may send SDK usage diagnostics; your translation text is processed locally.")
                row("Remove translation model", "Delete the downloaded Urdu model from this phone.") { action("translation_delete") }
                section("Roman Urdu")
                toggle("roman_suggestions", "Urdu suggestions for Roman Urdu", "In Roman Urdu layout, suggest common Urdu words such as hai → ہے.")
                row("Convert Roman Urdu", "Common words convert offline; names and unknown words remain unchanged.") { action("roman") }
                section("AI Write")
                row("Open AI Write", "Review generated text before inserting it.") { action("ai") }
                row("AI connection", "Set your trusted HTTPS backend and model. The optional token is encrypted on this device.") { action("ai:connection") }
                row("Remove AI connection", "Clear the backend, model and encrypted token.") { action("ai:clear") }
                note(if(AiConnection(context).ready()) "A provider is configured. Generate sends only the tool draft to it; provider fees and privacy policies apply." else "No AI provider is connected. Normal typing and local suggestions work without one.")
            }
            "Glide typing" -> {
                section("Gestures")
                toggle("glide", "Glide typing", "Slide across letter keys. This offline starter recognizer matches dictionary paths.")
                toggle("cursor_slide", "Space cursor control", "Slide left or right on Space to move through text.")
            }
            "Dictionary" -> {
                section("Personal dictionary")
                row("Add current word", "Save the word immediately before the cursor, explicitly and locally.") { action("dictionary_add"); navigate(page) }
                toggle("learn_words", "Learn frequently typed words", "Learn a completed word after 3 uses. Local only; excludes password, private, email and URL fields.")
                row("Clear learned words", "Remove all learned frequencies without changing your explicit dictionary.") { action("learned_clear"); navigate(page) }
                options.dictionary().forEach { word -> row(word, "Tap to remove this word.") { options.removeWord(word); changed(); navigate(page) } }
            }
            "Privacy" -> {
                section("Local data")
                note("Optional learned word frequencies, explicit dictionary words and recent emoji stay on this phone. Voice and writing drafts are not saved. Clipboard previews expire. Translation text is processed on-device; Google may collect SDK diagnostics. AI sends only its draft to your chosen provider when you press Generate.")
                row("Clear dictionary and recent emoji", "Also clears learned words and current clipboard previews.") { options.clearLocalData(); action("clear"); changed(); navigate(page) }
                row("App permissions", "Manage microphone, optional contacts and screenshot access in Android.") { action("permissions") }
            }
            "About" -> note("Raabta Keyboard 0.6.0\nLocal typing and suggestions; optional system voice recognition.\nMore → Settings opens full-screen settings; Back returns to your app.\nAI tools use your chosen provider. Android permission screens are system dialogs.")
            "Help and feedback" -> note("Space: tap to insert a space; slide to move the cursor.\nHold a letter for its symbol.\nTap a suggestion to insert it; emoji do not replace your word.\nUrdu voice: use Urdu-only mode for mostly Urdu phrases.\nImage paste requires an app that accepts image content.\nReport issues with the app, Android version and steps to reproduce.")
        }
    }
    private fun home() {
        listOf("Connections" to "Active provider, model, real connection test and encrypted key", "AI" to "Writing tools and provider controls", "Voice" to "Recording and recognition", "Appearance" to "Theme, keys and size", "Advanced" to "Gestures and dictionary", "Languages" to "English, Urdu and Roman Urdu", "Preferences" to "Keys, size, layout, shortcuts and feedback",
            "Theme" to "Midnight purple, system, light or dark", "Corrections and suggestions" to "Local words, emoji and correction controls",
            "Glide typing" to "Slide typing and Space cursor control", "Voice typing" to "Languages, Stop and long-pause behaviour",
            "Clipboard" to "Copied text, images and extracted items", "Dictionary" to "Your explicitly saved words",
            "Emojis, stickers and GIFs" to "Emoji rows, size and suggestions", "Privacy" to "Local data and permissions",
            "Writing tools" to "Translation, Roman Urdu and optional AI connection", "About" to "Version and app information", "Help and feedback" to "Usage and troubleshooting").forEach { (title, detail) -> row(title, detail) { navigate(title) } }
        row("Share Raabta", "Share the APK download page.") { action("share") }
    }
    private fun preferences() {
        section("Typing response")
        slider("typing_sensitivity", "Keyboard sensitivity", "Accept fast taps with small finger movement. All normal taps commit immediately; this never queues letters.")
        slider("suggestion_delay", "Suggestion response delay (ms)", "0 is fastest; higher values refresh suggestions less often. Typing stays immediate.")
        section("Keys")
        basicToggle("number_row", "Number row", "Always show numbers above the letter keys.", KeyboardPreferences.load(context).numberRow) {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(numberRow = it)); changed()
        }
        toggle("symbols_number_row", "Number row for symbols", "Show the number row in the first symbols layout.")
        toggle("emoji_key", "Emoji key", "Show a dedicated emoji key.")
        toggle("language_key", "Language switch key", "Show the globe key to switch keyboard languages.")
        toggle("comma_key", "Comma key", "Show a dedicated comma key.")
        toggle("full_stop_key", "Full stop key", "Show a dedicated full stop key.")
        section("Appearance")
        slider("font_size", "Font size", "Adjust letter size.")
        row("Use default font size", "Restore 22 sp.") { options.setNumber("font_size",22); changed(); navigate(page) }
        slider("emoji_size", "Emoji size", "Adjust emoji size and the number shown per row.")
        section("Layout")
        toggle("suggestion_strip", "Suggestion strip", "Show suggestions and access features.")
        toggle("floating", "Floating keyboard", "Compact keyboard over your app; drag the title to reposition.")
        choices("Keyboard height", listOf("Compact", "Normal", "Tall"), KeyboardPreferences.load(context).height.ordinal) {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(height = KeyboardHeight.entries[it])); changed()
        }
        choices("One-handed keyboard", listOf("Off", "Left", "Right"), options.number("one_handed")) { options.setNumber("one_handed", it); changed() }
        section("Shortcuts")
        toggle("double_space", "Double-space full stop", "Two quick spaces after text insert a full stop and space.")
        toggle("auto_space", "Auto-space after punctuation", "Add a space after comma, full stop, question mark and exclamation mark.")
        toggle("apostrophe_space", "Auto-switch after apostrophe", "Return to letters after entering an apostrophe from symbols.")
        toggle("symbols_hold", "Touch and hold keys for symbols", "Show symbol hints; hold a letter to insert its symbol.")
        toggle("flick_symbols", "Flick keys to enter symbols", "Flick upwards on a letter to insert its symbol.")
        slider("flick_sensitivity", "Flick input sensitivity", "Upward distance in dp before a symbol gesture is recognised.")
        toggle("cursor_slide", "Space cursor control", "Slide across Space to move the text cursor.")
        toggle("delete_swipe", "Swipe Backspace to delete words", "Drag left to highlight words; release to delete. Returning to the start or cancelling keeps text.")
        section("Key tap")
        basicToggle("sound", "Sound", "Play sound when a key is pressed.", KeyboardPreferences.load(context).sound) {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(sound = it)); changed()
        }
        slider("sound_volume", "Sound volume", "Volume percentage; respects phone sound settings.")
        basicToggle("vibration", "Keyboard vibrations", "Vibrate when a key is pressed.", KeyboardPreferences.load(context).vibration) {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(vibration = it)); changed()
        }
        slider("vibration_strength", "Vibration strength", "Short vibration duration in milliseconds; device-dependent.")
        toggle("popup", "Key pop-up", "Show a letter preview while a key is pressed.")
        slider("hold_duration", "Touch and hold duration", "Milliseconds before a held letter inserts its symbol.")
        choices("Backspace repeat delay", listOf("200 ms", "400 ms", "600 ms"), listOf(200L,400L,600L).indexOf(KeyboardPreferences.load(context).holdDelayMillis).coerceAtLeast(0)) {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(holdDelayMillis = listOf(200L,400L,600L)[it])); changed()
        }
        choices("Backspace repeat speed", listOf("Fast", "Normal", "Slow"), listOf(55L,85L,120L).indexOf(KeyboardPreferences.load(context).repeatMillis).coerceAtLeast(0)) {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(repeatMillis = listOf(55L,85L,120L)[it])); changed()
        }
        basicToggle("app_icon", "App icon", "Show Raabta in the launcher. Settings remain available from the keyboard.", options.enabled("app_icon")) {
            context.packageManager.setComponentEnabledSetting(ComponentName(context, "dev.raabta.keyboard.Launcher"),
                if (it) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            options.set("app_icon", it)
        }
    }
    private fun corrections() {
        section("Automatic corrections")
        toggle("auto_correction", "Auto-correction", "Correct common dictionary typos when you press Space. Off by default.")
        toggle("auto_capitalization", "Auto-capitalisation", "Capitalise at sentence starts when the editor supports it.")
        section("Spelling and grammar")
        toggle("spell_check", "Spell check", "Mark recognised common typos in red when the app supports suggestion spans.")
        toggle("grammar_check", "Grammar check", "Offer basic English agreement fixes; blue marks where the app supports them.")
        section("Suggestions")
        toggle("offensive_filter", "Don't suggest offensive words", "Exclude blocked words from dictionary and contact suggestions.")
        toggle("suggestion_strip", "Suggestion strip", "Show suggestions and access features.")
        choices("Number of suggestions", listOf("2", "3", "4"), options.number("suggestion_count")-2) { options.setNumber("suggestion_count",it+2); changed() }
        toggle("word_suggestions", "Word suggestions", "Show ranked word completions and known typo matches in balanced slots.")
        toggle("learn_words", "Learn frequently typed words", "Suggest completed words after 3 uses. Stores word frequencies only on this phone; private fields are excluded.")
        toggle("roman_suggestions", "Roman Urdu → Urdu suggestions", "Offer Urdu-script words when Roman Urdu layout is selected.")
        row("Clear learned words", "Remove the local learned-word frequencies.") { action("learned_clear") }
        toggle("next_word", "Next-word suggestions", "Use common local phrases to suggest the next word.")
        toggle("emoji_suggestions", "Relevant emoji", "Suggest emoji for English, Urdu and Roman Urdu words.")
        permissionToggle("contacts", "Suggest contacts", "Suggest matching contact names locally. Requires your permission.")
        toggle("smart_replies", "Smart replies", "Offer simple local greeting replies; no messages are uploaded.")
    }
    private fun emoji() {
        section("Layout")
        toggle("emoji_row", "Emoji fast-access row", "Show popular and recently selected emoji on the main keyboard.")
        toggle("symbols_emoji", "Emoji in symbols keyboard", "Show recently selected emoji in symbol layouts.")
        slider("emoji_size", "Emoji size", "Set the size and number of emoji shown in a row.")
        row("Use default emoji size", "Restore 26 sp.") { options.setNumber("emoji_size",26); changed(); navigate(page) }
        toggle("emoji_suggestions", "Relevant emoji suggestions", "Suggest emoji according to the word near your cursor.")
        row("Open emoji picker", "Choose from local emoji categories.") { action("emoji") }
        section("Stickers and GIFs")
        row("Open stickers and GIFs", "Choose original offline smile and heart stickers or animations.") { action("media") }
        note("Online sticker/GIF search is not connected. Offline images and GIFs require a receiving app that supports rich content.")
    }
    private fun clipboard() {
        toggle("clipboard_enabled", "Clipboard", "Allow local clipboard previews and paste tools. Off clears previews.")
        section("Suggestions")
        toggle("clipboard_suggestions", "Show recently copied text and images", "Preview the current clipboard in the suggestions bar. Nothing is saved to disk.")
        toggle("clipboard_items", "Show addresses, phone numbers and other items", "Extract phone numbers, email addresses and links from current copied text.")
        permissionToggle("screenshots", "Save recent screenshots in clipboard", "While this keyboard is visible, show newly saved screenshots. Requires image access; selected-photo access limits what is visible. Previews expire.")
        row("Open clipboard", "Paste current text or an image in supported apps.") { action("clipboard") }
        row("Clear clipboard previews", "Remove in-memory previews; leaves the system clipboard unchanged.") { action("clear") }
    }
    private fun voice() {
        section("Language")
        choices("Voice language", listOf("English", "Urdu", "Auto-detect English / Urdu"), listOf(VoiceSettings.ENGLISH,VoiceSettings.URDU,VoiceSettings.MIXED).indexOf(VoiceSettings.language(context))) {
            VoiceSettings.setLanguage(context,listOf(VoiceSettings.ENGLISH,VoiceSettings.URDU,VoiceSettings.MIXED)[it])
        }
        section("Completion")
        choices("Insert transcription", listOf("Only when I press Stop", "After 8 seconds of silence", "After 12 seconds of silence"), listOf(0L,8000L,12000L).indexOf(VoiceSettings.pauseMillis(context))) {
            VoiceSettings.setPauseMillis(context,listOf(0L,8000L,12000L)[it])
        }
        choices("Finish latency", listOf("Fast (0.8 seconds with a draft)", "Prefer-final (up to 3 seconds)"), if(VoiceSettings.finalWaitMillis(context)==800L) 0 else 1) {
            VoiceSettings.setFinalWaitMillis(context,if(it==0) 800 else 3000)
        }
        basicToggle("on_device", "On-device only", "Requires the phone's installed language model.", VoiceSettings.onDeviceOnly(context)) { VoiceSettings.setOnDeviceOnly(context,it) }
        note("Tap the microphone to show Urdu, English and Auto tabs. Auto requires Android 14+ and a supporting system provider; detection and recognition accuracy depend on that provider.")
    }
    private fun permissionToggle(key: String, title: String, detail: String) = basicToggle(key,title,detail,options.enabled(key)) {
        options.set(key,it); changed(); if(it) action("permission:$key")
    }
    private fun toggle(key: String, title: String, detail: String) = basicToggle(key,title,detail,options.enabled(key)) { options.set(key,it); changed() }
    private fun basicToggle(key: String, title: String, detail: String, value: Boolean, save: (Boolean)->Unit) {
        val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,context.dp(10),0,context.dp(10)) }
        val texts = LinearLayout(context).apply { orientation = VERTICAL; addView(label(title,18f)); addView(label(detail,13f,palette.muted)) }
        line.addView(texts,LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        line.addView(Switch(context).apply {
            thumbTintList=android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(palette.accent,palette.muted))
            trackTintList=android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf((palette.accent and 0x00FFFFFF) or 0x66000000,0xFF55515B.toInt()))
            tag = "option:$key"; contentDescription = title; isChecked=value; setOnCheckedChangeListener { _,checked-> save(checked) }
        })
        body.addView(line)
    }
    private fun slider(key: String, title: String, detail: String) {
        val range = KeyboardOptions.numbers.getValue(key).second
        val text = label("$title · ${options.number(key)}",18f)
        body.addView(text); body.addView(label(detail,13f,palette.muted))
        body.addView(SeekBar(context).apply {
            progressTintList=android.content.res.ColorStateList.valueOf(palette.accent)
            thumbTintList=android.content.res.ColorStateList.valueOf(palette.accent)
            tag="option:$key"; contentDescription=title; max=range.last-range.first; progress=options.number(key)-range.first
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,user:Boolean) { if(user) { options.setNumber(key,value+range.first); text.text=context.getString(dev.raabta.keyboard.R.string.setting_value,title,value+range.first) } }
                override fun onStartTrackingTouch(bar:SeekBar?) = Unit
                override fun onStopTrackingTouch(bar:SeekBar?) { changed() }
            })
        })
    }
    private fun choices(title:String,values:List<String>,selected:Int,save:(Int)->Unit) {
        body.addView(label(title,18f))
        body.addView(RadioGroup(context).apply {
            values.forEachIndexed { index,value -> addView(RadioButton(context).apply {
                typeface=android.graphics.Typeface.DEFAULT; buttonTintList=android.content.res.ColorStateList.valueOf(palette.accent)
                id=View.generateViewId(); tag="choice:$title:$index"; text=value; setTextColor(palette.text); isChecked=index==selected
                setOnCheckedChangeListener { _,checked -> if(checked) save(index) }
            }) }
        })
    }
    private fun row(title:String,detail:String,click:()->Unit) {
        body.addView(LinearLayout(context).apply {
            orientation=VERTICAL; tag="settings:$title"; isClickable=true; isFocusable=true; contentDescription="$title. $detail"
            setPadding(0,context.dp(13),0,context.dp(13)); addView(label(title,18f)); addView(label(detail,13f,palette.muted))
            setOnClickListener { click() }
        })
    }
    private fun section(text:String) { body.addView(label(text,14f,palette.accent).apply { setPadding(0,context.dp(20),0,context.dp(12)) }) }
    private fun note(text:String) { body.addView(label(text,15f,palette.muted).apply { setPadding(0,context.dp(14),0,context.dp(14)) }) }
    private fun label(text:String,size:Float,color:Int=palette.text) = TextView(context).apply { this.text=text; textSize=size; typeface=android.graphics.Typeface.DEFAULT; setTextColor(color); setPadding(0,context.dp(3),context.dp(12),context.dp(3)) }
}
