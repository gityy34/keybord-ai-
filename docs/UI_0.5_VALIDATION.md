# Raabta 0.5.0 UI and writing-tool validation

## Build and delivery

- Kotlin native Android IME `dev.raabta.keyboard`, version 0.5.0 / code 7, Android 8+ / target 35.
- `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` completed successfully.
- **128 tests executed; 0 failures, 0 errors, 0 skipped.** Lint: **No issues found**. No disabled checks or baselines were added.
- OPPO APK: `raabta-keyboard-0.5.0.apk`, ARM64, **21,666,005 bytes**.
- SHA-256: `38504180b134c58f79a859d926a86d0e4338d8956785a4348e1a10e7a6d35ee3`.
- `apksigner verify --verbose --print-certs` passes, v2 signing. Certificate SHA-256 `fa7096ff0c83f98bbbbceab4d223a6e2eaff8b9a6c162350f3f36c8b220ad045` matches the previous delivered APK. Install over the existing app to retain settings and learned words.
- `zipalign -c -P 16 4` passes.
- Source: `keyboard-ui-0.5.0`. Delivery: `apk-ui-0.5.0`.
- Direct APK: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-ui-0.5.0/raabta-keyboard-0.5.0.apk
- File-page fallback: https://github.com/gityy34/keybord-ai-/blob/apk-ui-0.5.0/raabta-keyboard-0.5.0.apk . Open in Chrome, sign in with repository access and tap Download raw file / View raw. Repository visibility is unchanged.

## Functional evidence

- Native Android graphics/text layout checks validate English, Urdu and Roman Urdu at 280/320/360/420 dp with 32-sp preferred key fonts. No key text is ellipsized, shifted to a side, outside its button, or above/below the button. A 1.5x system font scale is tested. OEM fonts cannot dictate key alignment or the key-label typeface.
- Symbol hints are drawn independently at the top right; primary text has no appended span altering its centre.
- Header controls adapt to narrow windows. Suggestions appear above the toolbar with equal-width candidate slots; existing 2/3/4 capacity and learning tests remain passing. Idle suggestions follow the current language.
- Full settings Activity/category navigation, returning with editor text unchanged, applying number-row/emoji preferences on return, and opening translation from full settings are tested. The settings task has no affinity with first-run setup. Scroll position is retained when a setting re-renders its category.
- Vector mic/Stop, recognizer-driven RMS meter, Stop/Cancel during processing, and all existing voice permission/provider/manual-stop/long-pause/lifecycle tests pass. Recognition and automatic language switching remain controlled by the Android provider.
- AI toolbar shortcuts, explicit style/language instructions, one request for up to three genuine variants, explicit replacement of a chosen variant, original/proposed highlighting, local custom-instruction editing with original draft preservation, and stale-caret rejection are tested through the service with a controlled HTTPS transport.
- Opening/changing AI tools or saving a custom instruction does not send a request. Generate sends only the tool draft and chosen instruction/model. A live provider is not configured and has not been tested.
- Emoji search stays in the local picker; selecting a matching emoji alone inserts into the host editor. English and Urdu keyword matching is tested. Catalogue and original offline GIF/sticker templates use no media network service.
- Existing typing, Shift/Caps, repeat deletion, swipe deletion, grapheme cursor sliding, learned-word privacy, suggestions, clipboard/media permissions, translation cancellation and provider credential/security tests still pass.

Native rendered previews from the actual views were inspected: keyboard languages, microphone, translation, AI cards, settings and Preferences. Outputs are retained under `/workspace/artifacts/ui-0.5.0`; these are simulated Android renders, not OPPO screenshots.

## Phone acceptance and AI connection

1. Install the update over Raabta, keeping its data. Under Theme, select **Midnight** if you previously chose another explicit theme.
2. Test rapid English/Urdu typing at your normal and larger phone font sizes, Shift, number row, long-press symbols, Space cursor sliding and swipe Backspace.
3. Tap gear: full settings categories should appear, with no enable/test onboarding page. Change a preference and return to typing; no editor text should be lost.
4. Test emoji search for `love` and `محبت`; search letters must not appear in the app. Pick the heart, then ABC to return.
5. Test Urdu/English/Auto voice, Stop, Cancel and long pauses. Keep On-device only off unless the required language model is installed. A moving meter is shown only when the provider reports RMS. Compare speech accuracy on the OPPO; a new UI does not replace the provider's recognizer.
6. For AI, set your trusted HTTPS OpenAI-compatible endpoint/model/optional token in Connection. Select app text, open a tool and Use selection (or type a local draft), then Generate. Review the card(s), Show changes and Replace Text. No provider/key is bundled; provider charges/policies apply. One card is valid if a provider returns plain text.
7. Test translation model download and Urdu ↔ English offline output on the phone. Model quality and host-specific formatting/media behaviour remain device checks.

Automated checks do not prove physical-device latency, flawless Urdu recognition or zero future crashes. Live provider/model and OPPO acceptance remain outstanding. No new third-party dependency was introduced; [public reference review](CLEVERTYPE_REFERENCE_0.5.md) and [existing transport/dependency security review](WRITING_TOOLS_0.4_SECURITY.md) explain the data flows and limits.
