# Continuous dictation update validation

Date: 2026-10-10. Version **0.2.1**, version code 4. Source branch `voice-continuous`.

## User feedback and resulting behavior

The user reports 0.2.0 controls working and online voice recognition producing English and Urdu text.
English works reasonably well; Urdu makes many errors. The user reports that even a short speaking
pause causes insertion, and wants background recognition with insertion on Stop or only a long silence.
This is reported physical-device evidence, not a cloud recognition test.

The old controller immediately inserted each provider final result. The new controller buffers confirmed
utterances and the latest partial in memory, requests partial results, and restarts an utterance recognizer
after provider endpoints without inserting text. A live draft is shown in the keyboard, separate from the
host editor. No draft is persisted. Stop or the chosen long silence inserts the assembled draft once.

- Completion choices: **manual Stop only**, **8 seconds of silence** (default), or **12 seconds**.
- Stop in Fast mode gives an active recognizer up to **800 ms** to improve available draft text. If no draft
  exists it waits up to 3 seconds. Prefer-final mode allows up to **3 seconds**. Buffered final text between
  utterances is inserted immediately. These are controller deadlines, not guaranteed server recognition latency.
- Speech providers can ignore requested silence lengths. The controller handles short provider endpoints
  by restarting after 300 ms (500 ms for no-speech; limited 800-ms busy retries). Restarting may cause short
  audio-capture gaps; continuous lossless audio streaming is not claimed. Incoming callbacks from old
  segments/sessions cannot insert or duplicate text.
- Service errors with a draft pause capture and retain it for explicit Stop/Cancel. Errors with no draft
  return idle. A five-minute session limit stops capture without automatically inserting its draft.
- Cancel, ordinary typing, cursor movement, hidden keyboard, changed editor/configuration or destruction
  cancel capture, timers, pending restarts and buffered text.
- Mixed mode starts with **Urdu** by default and requests high-precision language switching on Android 14+;
  mostly-English can be selected explicitly. Android 13+ requests quality formatting. Urdu-only mode is
  recommended for predominantly Urdu phrases. These provider options do not train or replace an ASR model;
  actual Urdu accuracy improvement needs matched phone examples and service/model validation.

## Additional settings

Backspace repeat-start delay: 200/400/600 ms. Repeat interval: 55/85/120 ms. Defaults retain 400/55.
Changing them rebuilds the keyboard on next show. Held Shift still uses Android's native long-press timing.
Voice choices, pause duration, finish latency and mixed primary language persist as settings only.
Existing themes/height/number row and feedback remain available.

## Verified cloud checks

- `source scripts/cloud-env.sh` and `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`: passed.
- **56 tests executed, 56 passed, 0 failures/errors/skipped**; Android lint **0 issues**.
- Regression coverage includes editor selection/Unicode/actions/rapid typing, voice permission and editor
  safeguards, utterance buffering/restarts without short-pause insertion, manual mode, long-silence completion,
  800-ms live-draft fallback, final-result refinement, no duplicate/late insertion, cancellation of scheduled
  restarts, network-failure draft preservation, five-minute pause, quality mode, mixed language requests,
  persistent settings, and real editor deletion with configured hold/repeat/cancel timings.
- APK v2 signature verified, same certificate as delivered 0.2.0. Package `dev.raabta.keyboard`, min SDK 26,
  target SDK 35; RECORD_AUDIO only, no Internet permission. The separate system speech provider may send audio online.
- Hosted Actions status remains unverified because the API is blocked; native Git publication is checked separately.

## APK

- `/workspace/artifacts/raabta-keyboard-0.2.1.apk`; 949,013 bytes.
- SHA-256: `9f52b953095eead9c435964434f2ebacc636b2dbb59c0199fc9d54859a45b7eb`.
- Direct: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-voice-0.2.1/raabta-keyboard-0.2.1.apk
- File page: https://github.com/gityy34/keybord-ai-/blob/apk-voice-0.2.1/raabta-keyboard-0.2.1.apk
- Install over the existing app. GitHub private-repository access requires browser sign-in; use Chrome
  and Download raw file/View raw if an in-app browser opens a page without starting the download.

## Remaining phone gate and reference audit

On the OPPO, test a two-second pause followed by more speech: no editor insertion should occur until
Stop or chosen long silence. Test manual mode with a 20-second pause, then Stop. Check final words are
not lost; use Prefer-final mode if Fast mode's partial fallback is incomplete. Cancel/switch fields/rotate
must discard drafts. Compare Urdu-only vs mixed mostly-Urdu using the same spoken sentence and record
specific recognition errors supplied by the user. Recognition accuracy/physical capture gaps/latency
are not established by fake-engine tests.

FlorisBoard public source was downloaded/read for timing/settings references; no application code was copied.
Gboard/Clever APKs were not installed or decompiled, and Google/Clever pages returned proxy CONNECT 403.
The required extra domains were saved in the environment draft; publication is still required for runtime
access. See [KEYBOARD_REFERENCE_REVIEW.md](KEYBOARD_REFERENCE_REVIEW.md) for exact sources, limitations,
feature checklist and next milestones. Neither current Gboard menu parity nor its measured performance is claimed.
