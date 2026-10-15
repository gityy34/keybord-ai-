# Keyboard reference review and next steps

Historical 0.2.1 review. Current screenshot-based 0.3.0 implementation and acceptance limits are recorded in [SETTINGS_0.3_VALIDATION.md](SETTINGS_0.3_VALIDATION.md).

Date: 2026-10-10. The user clarified the requested AI keyboard name as **Clever AI Keyboard**.
It is not assumed to be FlorisBoard. Its exact store listing/source repository was not verified.

## What was actually inspected

FlorisBoard was downloaded as a separate read-only public source reference under `/tmp`.
Repository: https://github.com/florisboard/florisboard
Commit: `fe1241f4921b3eae923571ff7a3e113a7e10d677`. License: Apache 2.0, read before review.
No FlorisBoard application code was copied into Raabta; Raabta's changes are original.

Inspected source:

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt`: categories for localization,
  theme, keyboard, smartbar, typing, gestures, clipboard, media, extensions and other settings.
- `app/settings/keyboard/KeyboardScreen.kt`: configurable long-press timeout (100–700 ms, 50-ms steps,
  optional system default), long-press hints, popups and multi-tap settings.
- `app/settings/keyboard/InputFeedbackScreen.kt`: independent audio/haptic controls and feedback per
  press, long press, repeated action and gesture; configurable audio volume.
- `ime/keyboard3/interaction/InteractionTimingOptions.kt`: reference defaults 300-ms repeat timeout,
  50-ms repeat interval, 300-ms long-press timeout, 800-ms multi-tap and 300-ms double-tap.
  Word deletion/undo/redo repeat intervals use a multiplier. AndroidInteractionController can instead
  use ViewConfiguration system timings. These are source defaults, not measured phone latency or Gboard timings.

Raabta's new controls are specifically **Backspace** repeat-start delay (200/400/600 ms) and repeat interval
(55/85/120 ms). Caps Lock on held Shift continues to use Android's native long-press behavior; no claim
is made that all keys share the Backspace delay. Layout reconstruction happens on mode/settings changes,
not on ordinary character commits; native feedback calls remain separate from editor updates.

## Gboard and Clever inspection limits

The environment proxy rejected Google help and the candidate CleverType website with CONNECT 403.
No Gboard/Clever APK was installed or decompiled, and no original source code was obtained.
An APK contains compiled code/resources and is not a checkout of its original source.
The cloud software emulator already proved unsuitable for reliable touch/performance comparison;
no hardware-accelerated Android or Play Store target is available here. No Gboard performance parity is claimed.

Official pages attempted or provided for subsequent verification:

- Gboard theme help: https://support.google.com/gboard/answer/7068412?hl=en (blocked; not read live).
- Gboard preferences help: https://support.google.com/gboard/answer/6102154?hl=en (blocked; not read live).
- Official Gboard Android listing: https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin
  (not fetched/installed).
- Candidate CleverType reference: https://www.clevertype.co/ (blocked; not confirmed as the user's exact listing).

The environment draft preserves existing domains and adds support.google.com, play.google.com,
clevertype.co and www.clevertype.co for review. The runtime policy is not changed by saving the draft.
Review/save/publish in environment settings is required before a live current-version audit can be retried.

## Feature checklist to verify against the installed Gboard version

This is a comparison checklist based on broadly available Gboard functionality, not an exhaustive,
freshly observed inventory. Availability depends on device, language, app version and region.

| Area | Features to compare | Raabta status |
| --- | --- | --- |
| Languages | Multiple layouts, multilingual typing, Urdu/Roman Urdu | English keys; Urdu/mixed speech via provider; Urdu key layouts next |
| Appearance | Themes, colors/backgrounds, borders | System/light/dark implemented; custom images/borders pending |
| Preferences | Number row, size, key feedback, popup/long-press hints | Height/number row/feedback and Backspace timing implemented; popup hints pending |
| Text correction | Suggestions, autocorrect, capitalization, next-word prediction | Shift/caps implemented; local suggestions/correction pending |
| Glide/gestures | Swipe typing, gesture deletion, cursor movement with Space | Held deletion implemented; gesture recognition/cursor mode pending |
| Voice | Dictation, multilingual recognition, punctuation | Buffered sessions, Stop/pause/latency controls; accuracy depends on provider |
| Dictionary | Personal words, shortcuts, multilingual dictionary | Pending, should be explicit local entries rather than silent typing capture |
| Clipboard | Paste, pinned items, expiry/history | Pending, explicit user access and expiry controls required |
| Media | Emoji, GIF/sticker search, suggestion options | Pending; local emoji first, remote media later |
| Tools/layout | Text editing, one-handed/floating/resize, translation | Ordinary editor actions present; advanced tools/translation pending |
| Privacy | Personalization, sensitive fields, clear-data controls | Password/private voice guards and no typing history; extra controls later |
| AI | Rewriting/grammar/tone helpers, provider settings | On-demand AI is a later milestone; never on normal keystrokes |

## Recommended next work

1. Complete the OPPO phone gate for 0.2.1: manual Stop, normal pauses, draft preservation, Urdu-only
   and mixed recognition, and configured Backspace timing. Compare actual missed words/latency on-device.
2. Add native Unicode Urdu and Roman Urdu layouts with a fast language switch and tested RTL cursor/deletion.
   For Urdu voice errors, use Urdu-only mode and the provider's supported language model; record examples
   explicitly supplied by the user instead of guessing replacements or silently uploading typed context.
3. Add local suggestion/correction dictionaries, long-press symbol hints, and cursor movement; organize
   settings into language, appearance, typing, gestures, voice and privacy screens.
4. Add explicit clipboard/emoji tools, one-handed layout, and device timing benchmarks.
5. Add optional AI rewrite/grammar actions and interchangeable providers after ordinary typing remains stable.
   Recheck provider limits, credentials and explicit text-upload consent at that implementation phase.
