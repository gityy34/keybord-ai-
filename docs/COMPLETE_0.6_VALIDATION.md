# Raabta 0.6.0 implementation and acceptance record

## Status

The existing single-module project was upgraded in place from `keyboard-ui-0.5.0` /
`e643de15d1c47c95acdddb8442a22d23a4b6c856` on branch `keyboard-complete-0.6.0`.
No separate demo, new app identity or replacement project was created. Existing typing, languages,
gestures, learned suggestions, themes, permissions, voice controller and local translation are retained.

This is an **automatically tested candidate**, not a claim that the requested fully functional product
has passed every acceptance gate. Live provider inference/current free-tier verification and physical
OPPO tests remain outstanding. [Provider evidence and secure connection setup](AI_PROVIDER_0.6_REVIEW.md)
explain the missing credential and cloud destination-policy denial.

## Inspection and implementation

| Module | Existing implementation found | 0.6 changes |
| --- | --- | --- |
| KeyboardView / GestureKeyButton / ToolbarButton | Native real keys, fitting/centering, swipe delete, Space cursor, layouts, settings/tool panels | Seven-tool scrolling toolbar with selected state/vector icons; ordinary rows retained with bounded AI panels; sensitivity controls tap slop; minor Space movement no longer drops fast taps |
| RaabtaInputMethodService / EditorCommands | Real InputConnection editing, suggestions, privacy/lifecycle, explicit writing drafts | Sentence/selection targets; genuine provider result Replace/Insert/Copy; cancellation and stale-editor guards; configurable suggestion refresh delay; immediate ordinary commits |
| WritingToolPanel / AiTools | Working local drafts, compatible HTTP results, preview cards/custom instructions | Rewrite options, two-column tone selector, Explain, full result review, Replace/Insert/Copy/Regenerate/Cancel; all ML Kit language choices and AI source detection |
| AiConnection / AiRequest | HTTPS custom endpoint, optional Keystore-encrypted token, bounded asynchronous requests | Existing saved connection preserved; active/deactivated state, masked key, request-verified status, friendly HTTP/timeout/network/model errors |
| AIProvider / AIService (new) | No shared provider contract | Replaceable provider operations and Groq preset using documented API/model; no runtime mock/backend substitutes |
| AiConnectionActivity | Minimal URL/model/token form | Full Connections summary, provider selector, Save/Test/Activate/Deactivate/Change/Delete/BYOK; password field protections and official key-dashboard link |
| KeyboardSettingsActivity / KeyboardSettingsPanel | Full category screen plus in-IME More pages; scroll restoration scheduled before layout | Existing settings retained and categorized; full Connections navigation; scroll restored after actual replacement layout, including deep toggles |
| VoiceController / DeviceSpeechEngine (preserved) | System recognition, continuous buffering, Stop/Cancel, language tabs, long-pause policy | Compact real-RMS bar, elapsed timer, completed/error/retry states; key rows retained; no claim of replacing recognizer/model accuracy |
| ClipboardPreview / KeyboardOptions | Explicit ephemeral clipboard preview, optional suggestions/extraction/media | Enabled preference and top picker switch; disabling clears/blocks previews; sensitivity/response sliders persisted locally |

The purple Midnight identity uses original icon resources and existing palette/key code. Reference
interaction patterns were implemented without copied CleverType/Gboard branding, closed source or assets.
Selecting an AI tool operates on a local draft. Grammar taps and rewrite/tone choices intentionally
submit text; loading/cancel/retry and result review stay inside the active app. Full settings and
credential editing use dedicated settings Activities as requested. Permission dialogs remain Android dialogs.

## Verification scope

Required command (actual pinned JDK/SDK, existing caches):

```sh
cd /workspace/keybord-ai-
source scripts/cloud-env.sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug --console=plain
```

Final command: **BUILD SUCCESSFUL**. **143 tests, 0 failures, 0 errors, 0 skipped**.
Lint: **No issues found**. ARM64 APK: **21,712,368 bytes**.
SHA-256: `49508b31098bde0de44a577b6d95c353adae5c42882dc2b794dca0902586fbea`.
`apksigner verify --verbose --print-certs` passes with v2 signing; retained certificate SHA-256
`fa7096ff0c83f98bbbbceab4d223a6e2eaff8b9a6c162350f3f36c8b220ad045`.
`zipalign -c -P 16 4` passes. Build/test/signature/metadata evidence is retained in `/workspace/artifacts`.
No disabled tests, lint baselines, permissive TLS configuration or new dependencies were introduced.

Regression coverage exercises:

- Immediate rapid per-character touch commits including slightly moving Space, Shift/Caps, backspace,
  Enter, English/Urdu/Roman input, cursor graphemes, swipe/repeat deletion, reopening/switching lifecycle.
- Configured sensitivity changes actual tap tolerance; response delay affects suggestions independently
  of typing; balanced 2/3/4 suggestion slots, repeated local word learning, privacy and Roman Urdu output.
- Clipboard on/off and stale/expired/private previews, emoji/media/contacts permissions and existing tools.
- Grammar request payload and parsed result; selected/current-sentence Replace with surrounding text
  retained; Insert preserves selected original; Copy leaves the editor intact; stale-caret rejection,
  custom instructions, regenerate/cancel, three genuine parsed alternatives and long result review.
- Provider status changes, activation/deactivation/deletion and form changes without silent overwrite;
  actual AES-GCM ciphertext round trip with a test key source; no plaintext key in stored preferences.
- Request-based connection test with controlled HTTPS transport; missing-key authentication failure;
  HTTP auth/model/quota/unavailable, timeout, response parsing, lifecycle/cancellation and cancelled queued
  language-detection requests. These transport fixtures are test-only, **not live provider inference**.
- Existing voice permission/error/language/Stop/continuous/long-pause/lifecycle tests; RMS/timer/status/retry
  panels, English/Urdu/Roman labels at 280/320/360/420 dp, 32-sp labels and 1.5x system font; full Settings,
  deep-scroll retention, selected-toolbar visibility and finite 320-dp landscape AI/voice key bounds.
- Existing real ML Kit client initialization and controlled translation lifecycle/failure/model download
  tests. Actual downloaded model quality/offline inference still requires a phone.

Actual-view native Android renders were inspected under `/workspace/artifacts/ui-0.6.0` (keyboard
languages, AI cards, translate, microphone, settings/preferences, tone/voice landscape). These are
simulated renders, not phone screenshots or physical latency benchmarks.

## APK and source delivery

Version 0.6.0 / code 8; application `dev.raabta.keyboard`; Android 8+ / target 35.
ARM64 candidate for OPPO: `raabta-keyboard-0.6.0.apk`. Universal/other ABI builds remain available.
Delivery branch: `apk-complete-0.6.0`. Source branch: `keyboard-complete-0.6.0`; main remains unmerged.

- Direct file: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-complete-0.6.0/raabta-keyboard-0.6.0.apk
- File-page fallback: https://github.com/gityy34/keybord-ai-/blob/apk-complete-0.6.0/raabta-keyboard-0.6.0.apk

Open in Chrome and sign in if the repository is private; choose **Download raw file / View raw** on
the fallback page. Git verification of bytes is not evidence that anonymous access works. Repository
visibility was not changed. Install over the prior Raabta app to retain its settings/learning; do not
uninstall first. Debug signing is for testing; production release signing/distribution remain future work.

## Phone/live acceptance steps

1. Type quickly in Notes, WhatsApp, Messages and Chrome. Test Shift/Caps, Space, Enter, cursor slide and
   swipe delete. Change sensitivity and suggestion response in Preferences; confirm no dropped keystrokes.
2. Scroll far down Preferences, toggle a setting and confirm the same scroll position. Check themes,
   number row, large fonts, narrow/landscape/floating/one-handed layouts and reopening/switching the IME.
3. Turn Clipboard off/on from its top switch. Confirm preview/paste rules in ordinary/private fields.
4. Configure your own provider key securely, Test Connection, Activate. Use selected text and a middle
   sentence for Grammar, Rewrite, Tone, Reply/Summary/Explain. Review all results before Replace/Insert/Copy.
   Retry/cancel, change fields while a request runs, and test airplane mode and invalid key/model.
5. Select manual translation languages and explicitly download the model; test Urdu/English offline.
   Detect Language requires active AI. Test Speak → insert with Stop and failure without changing host text.
6. Test Urdu, English and Auto voice on the OPPO with On-device only off unless models are installed.
   Short pauses must retain the draft; Stop/long-pause completes. Test Cancel, Retry, permissions and app
   changes. Urdu accuracy and mixed-language switching remain system speech-provider capabilities.

Remaining limitations: no live AI or current numeric free-tier verification yet; phone compatibility,
Keystore execution on actual hardware, translation/recognition quality and measured typing/provider
latency are unverified. InputConnection behavior depends on the host; failed commits/deletions are
reported, but arbitrary host mutations cannot be rolled back transactionally. Local dictionaries,
grammar hints, Roman Urdu and glide remain finite starter implementations. Offline media is original
and finite; online media search and a production AI proxy are not delivered. Zero future crashes,
perfect Urdu or proprietary Gboard/CleverType performance parity cannot be established by these tests.

## Changed files

- Build/version: `app/build.gradle.kts`.
- Keyboard/editor: `ime/KeyboardView.kt`, `GestureKeyButton.kt`, `ToolbarButton.kt`,
  `RaabtaInputMethodService.kt`, `ClipboardPreview.kt`, new `EditorTarget.kt`.
- Provider/network/storage: `tools/AiConnection.kt`, `AiRequest.kt`, `AiTools.kt`, new `AIProvider.kt`.
- Settings/UI: `setup/AiConnectionActivity.kt`, `KeyboardSettingsActivity.kt`,
  `ui/KeyboardOptions.kt`, `KeyboardSettingsPanel.kt`, `WritingToolPanel.kt`.
- Resources: `res/values/strings.xml`, new `res/drawable/ic_grammar.xml`, `ic_rewrite.xml`,
  `ic_tone.xml`, `ic_translate.xml`.
- Tests: `ime/AdvancedKeyboardTest.kt`, `AiPanelWorkflowTest.kt`, `KeyboardServiceTest.kt`,
  new `CompletionRegressionTest.kt`, `ui/KeyboardCustomizationTest.kt`, `ReferenceLayoutTest.kt`.
- Documentation: `README.md`, `docs/ROADMAP.md`, new `docs/COMPLETE_0.6_VALIDATION.md`,
  `docs/AI_PROVIDER_0.6_REVIEW.md`.

Java/Kotlin paths above are under `app/src/main/java/dev/raabta/keyboard/`; resource paths under
`app/src/main/`; test paths under `app/src/test/java/dev/raabta/keyboard/`.
