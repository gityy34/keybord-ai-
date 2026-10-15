# Raabta 0.7.0 integrated keyboard redesign

## Existing project and references

Modified the existing Kotlin/native Android View IME in place, starting at source commit
5df47a7abb9d35ce4b9b66097e82a4e607b0ed25. There are no Compose components in this project;
no new UI framework, app identity, demo or dependency was introduced.

Reviewed all 14 unique uploaded screenshots (the two repeated Gboard screenshots have identical
SHA-256 content), including the five new writing/tone/translation panels, earlier settings/key/voice
references and the reported label-alignment screenshot. Also inspected contact sheets from both
reference videos. The new panels use their rounded charcoal cards, violet selected chips, compact
headers, language controls, response alternatives and direct Back-to-keyboard interaction. Existing
Raabta functions remain the feature source; no ChatGPT branding or unsupported reference actions were added.

No separate light/colorful/futuristic theme screenshots were present among the attachments. The three
new variations implement the user's written theme brief using the existing visual system; exact matching
to additional theme images cannot be claimed.

## Implemented behavior

- AI/grammar/rewrite/tone/Roman Urdu and translation replace the key area within the same occupied
  container. Back returns directly to typing; successful Insert/Replace closes the panel. Local draft
  editing uses a compact header/preview plus the real keys in the same area; Done returns to review.
  Host editor contents are untouched until explicit final actions. Three provider-parsed cards remain
  supported; no canned AI response is included in the APK.
- Compact landscape headers combine selectors into the header, retain full tool access through a
  bounded themed chooser, and reserve a usable result viewport. Results scroll independently. Source,
  target, model download and voice translation preserve existing real backend/model actions.
- Tools is a three-column icon/label grid exposing the existing routes: Settings, Undo, Redo, Language,
  Themes, AI Chat/AI Write, Translate, Clipboard, Emoji, editing, Resize, Connections, Roman Urdu,
  original offline media, Floating, Share and One-handed preferences. Unavailable Undo/Redo are dimmed
  and disabled. Settings still opens the real full category Activity independently of onboarding.
- Undo/Redo now works for supported Raabta edits without relying on host undo menus. Typed runs are
  grouped; batches, selection replacement, AI, suggestions, paste and voice have edit boundaries.
  Text/selection restore is validated against the actual current host text before changing it.
  External changes invalidate conflicting history. No supported history means no destructive action.
- History is temporary: up to 20 edits, maximum 4,096 characters per supported editor snapshot; cached
  expected edits avoid a full IPC context read on every ordinary tap. It clears on field/window lifecycle
  changes and is disabled in password/private/non-text fields. No history is logged or persisted.
- Voice opens a bounded workspace with visible language, real RMS timeline, timer, live draft, Stop,
  Cancel/Back, Delete and Retry. Draft deletion cancels/clears the attempt; completed voice deletion only
  restores its unchanged, most recent supported voice edit. Later host edits are never overwritten.
  Cancel does not remove an already committed transcription. Failure displays a useful banner and retry.
- Recognition start is called synchronously from the authorized tap, with no added initial delay.
  The UI distinguishes Starting microphone from the provider's real Ready/Listening callback.
  Successful-utterance restart delay is reduced from 300 to 150 ms. Android 13+ requests latency-optimized
  formatting in Fast mode and quality formatting in Prefer-final mode. Existing Stop/long-pause buffering,
  language/provider selection, permission rules and late-callback guards are preserved.
- Waveform uses smoothed real recognizer RMS samples, updates at approximately 30 frames/s only while
  listening/visible, decays with silence, and stops/clears on processing/cancellation/detach. It does not
  invent audio levels. Wave height adapts in small landscape windows; action targets stay accessible.

## Themes and import

Existing System, Light, Dark and Midnight selections remain stored by stable enum names. Added:

- **Neon Orbit:** navy surfaces, cyan/violet accents, outlined keys/cards and dark accent-button text.
- **Paper White:** white/slate surfaces, blue actions and readable dark text/icons.
- **Spectrum:** light lavender surfaces, violet/magenta tool accents and shared component styling.

`Palette` provides central background/surface/key/text/muted/accent/secondary/border/on-accent values,
pressed shapes, icon colors and system-bar appearance. Keys, tools, writing/translation/voice, result
cards, themes, settings and choosers consume these values. Accent text chooses a readable contrast.
No random per-screen theme colors or heavy glow animations were introduced.

Themes opens directly from Tools. Theme previews use actual palette samples. **Import theme** opens
Android's document picker, reads at most 16 KB on a worker, validates a local JSON color definition,
then applies/persists it. Built-in themes remain available. One imported slot is supported; importing
another replaces that slot. No URLs, images, fonts, scripts, transparent colors or executable content
are accepted. Unreadable contrast and unknown fields are rejected without altering the active theme.

Use [the working example](themes/neon-orbit.json). Format: `version: 1`, short `name`, Boolean `dark`,
`colors` containing opaque `#RRGGBB` background/surface/text/muted/accent and optional key/secondary/border.
Text contrast against background/surface/key must reach 4.5:1; muted labels against the surface 4.5:1;
accent against the background 3:1. The file contains colors only, never credentials/editor text.

## Checks and build

**BUILD SUCCESSFUL**, **157 tests, 0 failures, 0 errors, 0 skipped**; lint **No issues found**.
ARM64 APK: **21,508,614 bytes**, SHA-256 `618455ff3f9ac60ec05ddc16e6f2f22e6e7fddb336ee5221fe753ddd9d05e7c4`.
`apksigner verify --verbose --print-certs` and `zipalign -c -P 16 4` pass; retained v2 signer
SHA-256 `fa7096ff0c83f98bbbbceab4d223a6e2eaff8b9a6c162350f3f36c8b220ad045` matches earlier deliveries.
Source branch: `keyboard-polish-0.7.0`; APK branch: `apk-polish-0.7.0`.
Install over the existing app to keep settings. This is a debug-signed phone-testing build.

Direct APK: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-polish-0.7.0/raabta-keyboard-0.7.0.apk

Fallback: https://github.com/gityy34/keybord-ai-/blob/apk-polish-0.7.0/raabta-keyboard-0.7.0.apk .
Open in Chrome, sign in if private, then Download raw file/View raw. Repository visibility is unchanged.
Authenticated remote byte checks do not prove anonymous access.

 Required actual command:

```sh
cd /workspace/keybord-ai-
source scripts/cloud-env.sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug --console=plain
```

Regression suite retains the existing typing, caps/shift, deletion/cursor, English/Urdu/Roman, learning,
privacy, clipboard/media/permissions, AI request/result/cancellation, connection/security, translation
and continuous voice tests. New checks cover undo/redo/branching, batched selections, external changes,
private/large fields, voice Delete/Cancel safety, direct recognition start/readiness, 150-ms restart,
formatting preferences, waveform stop, theme validation and the actual document-picker result/import flow.

Native Android rendered previews are under `/workspace/artifacts/ui-0.7.0`. Four palette variants were
rendered for keys, Tools, real result views, translation and voice. Checks compare tool heights to the
normal keyboard, three equal grid columns, selected/disabled controls, 280-dp/1.5x-font draft editing,
English/Urdu/Roman key fitting and 240-/320-dp landscape workspaces. Final images were visually inspected
against the references. These renders use test-only controlled text/RMS, not a live provider or OPPO audio.
No tests or lint checks were disabled and no baselines added.

## Preserved features and remaining acceptance

Preserved working layouts/typing/Shift/Caps/Enter/Space, symbols/number row, repeat/swipe Backspace,
grapheme cursor movement, learned suggestions, local dictionaries/Roman Urdu, emoji and original offline
media, Clipboard switch/previews, optional permissions, voice buffering/language preferences, ML Kit
translation, existing Groq/Custom connection and encrypted BYOK storage. Normal typing has no deliberate
queue/throttle and does not use AI. AI/network work stays off the UI thread.

Live AI still requires the developer key absent in this environment; no fake free/shared key or live
inference claim was added. See AI_PROVIDER_0.6_REVIEW.md for provider research/network/account gates.
ASR microphone startup, audio gaps, Urdu accuracy and formatting depend on the installed speech provider;
issuing start promptly and displaying Ready cannot guarantee capture before the provider is ready.
Physical OPPO/host-app performance, speech/model quality, system picker/OEM behavior and actual hardware
Keystore acceptance still require device testing. Local lexicons/glide/media remain finite. History
only handles supported bounded plain-text edits in the visible field; rich formatting and arbitrary
host mutations are not a transactional undo guarantee. Production signing/proxy deployment are unchanged.

## Files/components changed

Core files: `ime/KeyboardView.kt`, `RaabtaInputMethodService.kt`, new `EditHistory.kt`, `ToolbarButton.kt`;
`ui/WritingToolPanel.kt`, new `ToolsPanel.kt`, `VoicePanel.kt`, `ThemedChoices.kt`, `ThemePicker.kt`,
`ThemeImport.kt`, `Palette.kt`, `KeyboardPreferences.kt`, `KeyboardSettingsPanel.kt`, `EmojiPanel.kt`,
`PendingKeyboardTool.kt`; `voice/SpeechEngine.kt`, `VoiceController.kt`, `VoiceLevelView.kt`;
`setup/KeyboardSettingsActivity.kt`, `SetupActivity.kt`, new `ThemeImportActivity.kt`.

Build/manifest/strings updated, seven original tool icons added. Existing interaction/layout tests
updated for the requested navigation; new PolishInteractionTest, PolishLayoutTest, VoicePolishTest and
ThemeImportActivityTest exercise the added behavior. README, ROADMAP and this validation/schema example
record the new contract. Source paths above are under app/src/main/java/dev/raabta/keyboard.
