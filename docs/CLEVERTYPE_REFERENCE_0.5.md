# Raabta 0.5 reference review

Reviewed 2026-10-10 using the two user-supplied CleverType screen recordings, the key-alignment screenshot, and these public first-party pages (HTTPS 200 responses):

- https://www.clevertype.co/
- https://play.google.com/store/apps/details?id=com.clevertype.ai.keyboard
- https://www.clevertype.co/privacy-policy

These are product observations and the publisher's descriptions, not an audit of CleverType's internal implementation. No proprietary APK, code, icons, account, credentials, or hosted service was incorporated into Raabta. The original native implementation uses the existing verified dependencies; this update adds no library dependency.

## Observed workflow and implementation

| Reference | Raabta implementation |
| --- | --- |
| Black keyboard, charcoal keys and violet selected controls | Midnight purple theme; dark palette follows the reference; explicit sans-serif metrics and centre alignment instead of inherited OEM button defaults. Existing theme, feedback, number-row and size preferences are retained. |
| Centred letters, with smaller symbol hints | Native auto-sized key labels. Symbol hints are drawn independently at the top right, so they do not push the main character sideways. |
| AI / grammar / tone toolbar controls | AI shortcut; direct tool chips; category menu for AI assistance, Word tone and Fix grammar. |
| Humanise, Reply and summarising | Provider instructions for Humanise, Reply, Summary, Caption, Email, Phrase-to-emoji, Ask AI and Continue. Replies use only the text the user explicitly supplies. |
| Tone options | Professional, Casual, Polite, Romantic, Empathetic, Funny, Poetic, Shorten, Sarcastic, Angry, Flirty, Gen-Z and Witty. |
| Grammar controls | Grammar Fix, Rephrase, Add emojis and Realistic. |
| Custom tools | Up to 90 characters of a custom instruction entered using the keyboard; existing draft retained. Instruction lives in memory and is cleared when the tool/editor is closed. |
| Multiple preview cards / Replace Text | One explicit Generate request can return up to three actual alternatives. Each has Replace Text and Show changes; providers returning plain text produce one card. No fabricated alternatives or automatic extra requests. |
| Show changes | Original/proposed text with changed middle ranges highlighted. This is a bounded prefix/suffix comparison, not a full linguistic diff or grammar explanation. |
| Language dropdown | Explicit English/Urdu dropdowns for AI and translation. Translation keeps the existing on-device Urdu ↔ English model workflow. Other languages are not claimed supported. |
| Recording bar | Vector microphone and Stop icons, English/Urdu/Auto chips, Cancel, live transcript and an RMS meter driven only by system recognizer callbacks. Provider accuracy and automatic switching remain device/provider dependent. |
| Emoji picker | Local English/Urdu keyword search, recent emoji and Faces, Hearts, Nature, Food, Travel, Activities and Objects categories. Search text stays in the picker; only selected emoji are inserted. |
| Full settings categories | Gear opens KeyboardSettingsActivity, with Preferences, Theme, Suggestions, Glide, Voice, Clipboard, Dictionary, Emoji, Privacy and Writing tools. This is not the first-run enable/test screen. Back returns to the previous app. |

## Background and data behavior

The website and store describe AI grammar, tones, responses, translation, assistants, voice typing, custom themes, floating/split layouts and GIF/sticker support. Its privacy policy describes explicit AI text/voice processing. Those descriptions do not expose its backend code or establish its performance/security.

Raabta's AI transport remains an explicit HTTPS request to a user-configured OpenAI-compatible backend. No provider or API key is bundled. Request text is only the local tool draft or explicitly loaded selection, never the screen, message history, clipboard, contacts or surrounding conversations. System speech remains the Android recognizer; we do not claim CleverType's proprietary cloud dictation, filler removal or grammar processing. Translation uses the existing reviewed ML Kit package; its diagnostics disclosure remains applicable.

Existing original offline emoji/stickers/GIFs, dictionary, learned-word frequencies, cursor gesture and swipe deletion are retained. Online GIF search, a downloadable theme marketplace, a proprietary CleverType backend, tablet split layout and all-language AI parity are outside this update.

## Verification

Native Android text/render checks cover narrow widths, English, Urdu, Roman Urdu, large system fonts, voice controls and compact result cards. Integration tests check explicit requests, variant replacement, custom instruction isolation, Show changes and stale-editor protection. The user must still test the final APK on the OPPO; simulated layout tests do not establish device speed or zero crashes.
