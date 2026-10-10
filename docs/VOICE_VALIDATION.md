# Voice typing update validation

**Phone update, 2026-10-10:** the user reports 0.1.1 voice did not work on OPPO Reno 11 5G,
reported Android 16, showing “This speech service or offline model does not support the selected…”
(the original unsupported-language message). Voice phone acceptance is **failed**, while earlier
basic keyboard acceptance remains passed. See [the combined 0.2.0 update](PHASE_2_VALIDATION.md).

Date: 2026-10-09. Version: **0.1.1** (version code 2). Source branch: `voice-typing`.
The user accepted the basic 0.1.0 phone tests and then requested speech-to-text. This delivery
implements that voice milestone ahead of the remaining roadmap phases.

## Verified in this cloud environment

- `source scripts/cloud-env.sh` then `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`: passed.
- **31 tests executed, 31 passed, 0 failures, 0 errors, 0 skipped**: 3 keyboard state, 17 IME/service,
  3 setup/manifest, and 8 voice-controller tests.
- Android lint: **0 issues**, with warnings treated as errors.
- Debug APK Android v2 signature: verified. Same certificate as the phone-tested 0.1.0 APK,
  allowing an in-place update. Certificate SHA-256:
  `fa7096ff0c83f98bbbbceab4d223a6e2eaff8b9a6c162350f3f36c8b220ad045`.
- APK metadata: `dev.raabta.keyboard`, min SDK 26 (Android 8+), target SDK 35.
- Requested permission: **RECORD_AUDIO only**; no INTERNET permission. Microphone hardware is optional.
- Existing typing tests continue to pass, including editing, held deletion, Unicode, editor actions,
  rapid input, password/numeric fields, and missing editor connections.

New tests exercise final result insertion exactly once, selection replacement, language request,
Stop/Cancel, silence, service errors, permission denial/revocation, missing speech service, stalled-session
timeouts, cleanup failure, private/password restrictions, and stale callback rejection after normal typing,
cursor movement, editor switching, cancellation, and keyboard hiding. Recognition requests contain no typed
context. Session tests use a controlled engine through the real IME; they do not prove actual microphone
recognition or remote provider behavior.

## Delivered APK

- File: `/workspace/artifacts/raabta-keyboard-voice-0.1.1.apk`.
- Size: **914,894 bytes**.
- SHA-256: `d9b8f8a035eb83381354fd0cd624326280177f23ad55551464aa5576d87b7b4e`.
- Direct download: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-voice-0.1.1/raabta-keyboard-voice-0.1.1.apk
- The separate download branch holds the APK and checksum; the source branch excludes binaries and signing keys.
- Private repository access requires signing into an authorized GitHub account in the mobile browser.

Hosted Actions status remains unverified because GitHub API access was blocked, as recorded in
[the original validation](VALIDATION.md). Successful local build/tests and native Git publication
are reported separately from hosted CI and browser download verification.

## Phone checks still required

1. Install the update over the accepted 0.1.0 build. Confirm normal typing still works.
2. Choose English voice in setup. In a normal message field, tap the microphone, allow permission,
   return, tap again, and dictate “hello world”. Confirm one final insertion.
3. Dictate and tap **■** to stop/process. Start again and tap **×**; confirm no cancelled result appears.
4. Deny microphone permission or revoke it in Android settings. Confirm the permission explanation appears
   and normal typing remains usable. Granting permission should never begin recording automatically.
5. During dictation, type a key, move the cursor, hide the keyboard, or switch apps. Confirm the old result
   does not appear later or in the new field. Password and numeric fields should have a disabled microphone.
6. Choose Urdu voice, speak a short Urdu phrase, and confirm Unicode Urdu text if the installed recognizer
   and model support it. Unsupported languages should show a clear error and leave typing working.
7. Check airplane mode separately. On Android 12+ a dedicated on-device recognizer is used when available;
   otherwise offline preference is a hint, and the system provider may need a connection or send audio online.
8. Report device/Android version and the exact service, language, app, and error if recognition fails.

No actual phone speech-recognition result is claimed yet. No recordings, transcripts, typed context,
or audio logs are retained by Raabta. The selected voice language is the only stored voice preference.
