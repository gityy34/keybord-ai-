# Customization and voice compatibility update

Date: 2026-10-10. Version: **0.2.0** (version code 3). Source branch: `phase-2-ui`.

## User report and changes

The user reports the 0.1.1 microphone immediately returned to idle on an OPPO Reno 11 5G,
reported Android 16, showing “This speech service or offline model does not support the selected…”
(the unsupported-language/model error). The earlier basic keyboard test remains accepted;
voice phone acceptance is failed until another successful phone test is reported.

0.1.1 automatically selected the dedicated on-device recognizer whenever available and requested offline
recognition. Availability of that recognizer does not establish support for the requested language/model.
0.2.0 uses the installed system recognizer by default without forcing offline recognition. On-device-only
recognition is now an explicit opt-in setting with an Android 12+ availability guard. The exact failing
provider and language were not supplied, so this is a targeted compatibility correction, not a proven
successful phone repair. A provider may still lack Urdu or the selected language.

Speech errors now remain visible inside the keyboard instead of disappearing as a toast. Audio capture
errors have specific guidance. Returning from microphone permission explains that another microphone tap
is needed; granting permission never starts recording automatically. Password/private/non-text guards,
Stop/Cancel, timeouts and late-result protections remain in place.

At the user's request, English + Urdu mode asks Android 14+ speech providers for language detection and
balanced automatic switching, restricting both to en-US and ur-PK. This is a request to the provider;
providers may lack the languages/models or ignore switching. It does not add independent bilingual ASR.
Older Android versions show a clear unsupported-mode message; separate English/Urdu modes remain available.

## Implemented customization controls

- Saved system/light/dark theme for the keyboard and setup; microphone permission screen also follows it.
- Compact/normal/tall keyboard height, shorter landscape rows, centered content capped at 840 dp on wide displays.
- Optional number row in letters mode; native press ripple with palette-aware colors.
- Independent vibration and sound controls, sound off by default. Android haptic/touch-sound settings and
  silent mode are respected. Feedback hardware errors disable the failing feedback path, leaving key editing usable.
- Preferences refreshed when the keyboard is shown, retaining the active editor text/connection and mode.
  Settings are captured per view so ordinary keypresses do not read preferences or rebuild the layout.
- Navigation-bar inset padding, cancellation of held deletion and speech during configuration changes.

Settings store presentation choices, selected voice language and on-device preference only. No typed
content, recordings, transcripts, or credentials are stored. No new permissions or runtime dependencies.

## Cloud validation

- `source scripts/cloud-env.sh` then `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`: passed.
- **44 tests executed, 44 passed, 0 failures/errors/skipped**. SDK 28 regression tests plus an SDK 34 mixed-voice request test.
- Android lint **0 issues**, with warnings treated as errors.
- Tests include original fast typing/editing/voice safeguards, persistent language errors, permission return,
  defaults and opt-in offline mode, independent feedback toggles and hardware failure, theme persistence,
  phone-night behavior, height ordering, digit-row insertion into the active editor, measured portrait and
  wide-landscape button bounds, editor preservation during preference refresh, and both mixed-language intent lists.
- v2 APK signature verified; signing certificate matches the original phone-tested APK.
- Package `dev.raabta.keyboard`, Android 8+ (min SDK 26), target SDK 35; RECORD_AUDIO is the only permission.
- Actual microphone recognition, mixed-language recognition, phone frame/touch latency, physical sound/vibration,
  and daily-use stability are **not established by these automated tests**. Hosted Actions remains unverified
  because GitHub API access is blocked; source/download publication uses the working native Git proxy.

## APK

- File: `/workspace/artifacts/raabta-keyboard-0.2.0.apk`.
- Size: 911,765 bytes.
- SHA-256: `e3e301b9f1d15ead64fc73fcdcc7a04207e7f84134a51ff5475fc9887896ff67`.
- Direct download: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-phase-2-0.2.0/raabta-keyboard-0.2.0.apk
- Same signing certificate as 0.1.0/0.1.1; install over the current app.
- The download branch holds APK/checksum only; the source branch excludes binaries/signing keys.
  Sign into an authorized GitHub account in the browser if this repository is private.

## Phone retest

1. Install the update, open setup and leave **On-device only off**. Select **English voice** first.
   Focus a normal text field, tap the microphone and speak “hello world”. Stop should insert once; Cancel should insert nothing.
2. Select **English + Urdu voice** and dictate a sentence mixing the languages. Recognition/switching needs provider support.
   If an error remains, read its persistent message. Enable/update the installed speech provider (for example,
   Speech Recognition and Synthesis from Google where available), select it in Android voice-input settings,
   and install supported language models. A phone/system update was not established as the cause.
3. Try every theme/height, number row and feedback toggle. Reopen the keyboard after changes. Verify silent mode,
   held Backspace/Caps, rotation, keyboard hiding, app switching and ordinary airplane-mode typing.
4. Confirm old speech results never land in another field after typing/cancelling/switching. Password/numeric fields should disable voice.

The user requests Gboard-like smoothness and customization. This milestone implements the controls above;
Gboard feature/performance parity is not claimed. Urdu/Roman Urdu key layouts, suggestions, emoji/clipboard,
AI, translation, glide typing and broader customization remain later roadmap work. Phase 2 phone comfort
and performance acceptance remains open until real-device results are supplied.
