# Raabta Keyboard development phases

The complete product context is preserved in [PROJECT_REQUIREMENTS.md](PROJECT_REQUIREMENTS.md).
It remains the source of requirements. The first delivery deliberately covers phases 0 and 1.

| Phase | Scope | Acceptance gate |
| --- | --- | --- |
| 0 | Inspect existing repository, preserve existing work, identify build prerequisites | Inspection recorded; initial repository had one README and no project code |
| 1 | Native Kotlin IME, English typing, Shift/Caps, symbols/numbers, setup, APK, CI | Tests/lint/build pass; installed IME smoke test; phone acceptance checklist |
| 2 | UI refinement, explicit light/dark/system settings, haptics/sound controls, orientation and performance | Comfortable fast typing on the user's phone; measured regressions |
| 3 | Urdu Unicode layout, Roman Urdu, language switch, modest local suggestions | Correct text/cursor behavior in all three modes |
| 4 | Emoji, explicit clipboard access, suggestion strip, shortcuts | Privacy and Unicode regression checks |
| 5 | On-demand AI toolbar; interchangeable provider layer; loading, cancel, timeout/quota errors | Provider/free-tier limits reverified at implementation time; asynchronous actions; typing stays local and responsive |
| 6 | Device speech recognition; language-aware voice; on-device translation where supported | Permissions/failures tested; current supported translation language pairs verified |
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
- No background service apart from Android's managed IME; no account, backend, AI SDK, or paid service.
- Future AI adapters will live separately from keyboard editing. They must never execute for ordinary keystrokes.

## Phase 1 phone acceptance (pending user results)

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
Do not mark the phone gate passed until the user reports their results.
