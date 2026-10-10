# Raabta Keyboard development phases

The complete product context is preserved in [PROJECT_REQUIREMENTS.md](PROJECT_REQUIREMENTS.md).
It remains the source of requirements. The first delivery deliberately covers phases 0 and 1.

| Phase | Scope | Acceptance gate |
| --- | --- | --- |
| 0 | Inspect existing repository, preserve existing work, identify build prerequisites | Inspection recorded; initial repository had one README and no project code |
| 1 | Native Kotlin IME, English typing, Shift/Caps, symbols/numbers, setup, APK, CI | Tests/lint/build passed; basic phone functional tests passed per user report; extended device checks and hosted CI verification remain open |
| 2 | UI refinement, explicit light/dark/system settings, haptics/sound controls, orientation and performance | Controls implemented in 0.2.0; automated regression/layout checks passed; real-phone comfort, rotation and performance gate pending |
| 3 | Urdu Unicode layout, Roman Urdu, language switch, modest local suggestions | Correct text/cursor behavior in all three modes |
| 4 | Emoji, explicit clipboard access, suggestion strip, shortcuts | Privacy and Unicode regression checks |
| 5 | On-demand AI toolbar; interchangeable provider layer; loading, cancel, timeout/quota errors | Provider/free-tier limits reverified at implementation time; asynchronous actions; typing stays local and responsive |
| 6 | Device speech recognition; language-aware voice; on-device translation where supported | Voice implemented in 0.1.1; phone test failed with unsupported language/model. 0.2.0 changes recognizer selection and adds provider-dependent mixed English/Urdu requests; phone retest required. Translation remains pending |
| 7 | Private mode, sensitive-field controls, API configuration, clear-data controls | Password and sensitive-field tests; no secret/text logging; explicit upload consent |
| 8 | End-to-end performance, app compatibility, offline/failure scenarios | Repeated real-phone testing in messaging, mail, search, notes, and other apps |

Some foundation safeguards are included immediately: no network permission, no typing storage,
no backup, protected IME registration, Unicode-safe deletion, and no analytics.
These do not replace the later privacy-hardening phase.

## Architecture decisions

- Native Android views and `InputMethodService`, Kotlin, one small app module, no runtime UI framework.
- `KeyboardState` contains only mode/case; it never stores typed content.
- `KeyboardView` renders keys and reports explicit key commands.
- `EditorCommands` is the only text-editing path, using the current `InputConnection`.
- `EditorPolicy` maps field types and Android editor actions.
- `SetupActivity` helps users enable/select the IME and supplies disposable test fields.
- `VoiceController` owns cancellable speech sessions with timeouts and rejects late callbacks; `DeviceSpeechEngine` adapts Android speech recognition. Permission is requested through a separate activity and never starts recording automatically.
- Voice preferences store only the selected language and on-device-only choice. Recognized text passes directly to the current connection; passwords/private/non-text fields block voice. The default system recognizer may process audio online; on-device-only mode is an explicit opt-in.
- No background service apart from Android's managed IME; no account, backend, AI SDK, or paid service.
- Future AI adapters will live separately from keyboard editing. They must never execute for ordinary keystrokes.

## Phase 1 phone acceptance

On 2026-10-09 the user reported completing the provided tests on their phone and that every tested
feature works properly. Record basic functional acceptance as passed. The complete checklist below
remains available for extended coverage; the user has not supplied a device model, Android version,
host-app matrix, or per-scenario results.

- Install and launch; enable Raabta in Android keyboard management; select it in the picker.
- Type `hello world`, delete it, enter multiple lines, and type rapidly without dropped letters.
- Tap Shift, type `Ab`; tap Shift twice, type `ABC`; tap again and type lowercase.
- Try both number/symbol pages and return to ABC. Try holding Backspace.
- Test the setup app's Search, numeric, and password fields.
- Type in WhatsApp, Notes, Chrome search, Gmail, Messages, and Instagram where available.
- Reopen the keyboard, switch between apps/keyboards, and rotate the phone.
- Repeat ordinary typing with airplane mode enabled.
- Report phone model, Android version, host app, reproduction steps, and observed behavior.

Emulator and JVM results cannot establish real-phone compatibility or daily-use stability.
Preserve the distinction between user-reported basic acceptance and unreported extended scenarios.

## Voice milestone requested after phase 1

Version 0.1.1 adds a microphone, Stop/Cancel, English/Urdu dictation settings, permission handling,
service/error/timeout handling, and editor-lifecycle protections. All 31 tests passed, including the
original typing regression suite, and lint/build/signature checks passed. This milestone advances the
voice portion of phase 6; at that delivery UI refinement, Urdu key layouts, AI, and translation remained pending.
The subsequent phone voice test failed with an unsupported-language/model error. Version 0.2.0 changes recognizer selection and needs another phone test; see [PHASE_2_VALIDATION.md](PHASE_2_VALIDATION.md).

## Latest user priorities (2026-10-10)

The user requests Gboard-like smoothness and broad customization, and asks to limit credit/time usage.
The voice compatibility fix and phase 2 controls are combined in 0.2.0 at the user’s explicit request.
System/light/dark theme, keyboard height, number row and vibration/sound are implemented; held deletion
and Caps Lock remain supported. Device rotation, physical feedback, mixed-language recognition and sustained
typing remain phone acceptance checks. Gboard-equivalent functionality or performance is not yet established.

The user requests written communication in English or Urdu, not Hindi. Mixed English + Urdu voice is requested; 0.2.0 adds Android 14+ provider-dependent language detection/switching. Urdu/Roman Urdu key layouts, suggestions, emoji/clipboard, AI, translation, glide typing and broader Gboard-like features are not delivered by this milestone.

## Continuous voice milestone (0.2.1)

The user reports working 0.2.0 controls and online English/Urdu transcription, with Urdu mistakes and
premature insertion after short pauses. 0.2.1 buffers/restarts utterances, exposes manual/long-pause
completion and fast/final-result modes, defaults mixed voice to Urdu with careful language switching,
and adds Backspace hold/repeat timing. Actual Urdu accuracy and continuous-capture gaps remain provider-dependent
phone checks. Next milestones and researched source limits are in [KEYBOARD_REFERENCE_REVIEW.md](KEYBOARD_REFERENCE_REVIEW.md).


## Screenshot settings and local tools milestone (0.3.0)

Category settings now run in the keyboard; Android settings has a dedicated category entry. Original
onboarding stays for enable/select/test. Adds Space cursor slide, a stable suggestion strip, local emoji/
word/phrase/rule suggestions, opt-in typo correction, Urdu/Roman Urdu layouts, held/flick symbol controls,
font/emoji size, feedback intensities, one-handed/floating layouts, editor tools, optional current clipboard/
contact/screenshot previews and an original offline sticker/GIF pack. No Gboard application source/assets
were copied. See SETTINGS_0.3_VALIDATION.md for per-feature scope, checks and phone gate.

Phase 3/4 foundations are advanced; they are not complete acceptance claims. Expand dictionaries and
swipe recognition using licensed resources, test host compatibility and device latency, and then add
optional translation/AI/online media providers. Existing voice recognition still relies on phone models.
