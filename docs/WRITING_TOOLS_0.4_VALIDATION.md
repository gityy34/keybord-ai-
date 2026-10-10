# Raabta Keyboard 0.4.0 validation

## Build and delivery

- Source branch: `keyboard-tools-0.4.0`; no changes merged to main.
- Command: `source scripts/cloud-env.sh` then `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`.
- Final run: **115 executed/passed, zero failures/errors/skips; Android lint zero issues; build successful**. Android framework simulations use SDK 28 and 34. These are not physical-device performance benchmarks.
- Version: **0.4.0**, code **6**, package `dev.raabta.keyboard`, Android 8+ / target 35.
- Published phone artifact: `raabta-keyboard-0.4.0.apk`, **ARM64**, suitable for OPPO Reno 11 5G; **21341411 bytes**. Build also generates ARMv7/x86/x86_64 and a universal APK. CI uploads the universal APK/checksum.
- SHA-256: `d124e7b190c6bb3570f6f33c93526e57c0900b07c711ccfb09c2be4854c0e5e0`.
- APK v2 signature verified with retained certificate SHA-256 `fa7096ff0c83f98bbbbceab4d223a6e2eaff8b9a6c162350f3f36c8b220ad045`. Install over the previously delivered APK; no uninstall is required for these builds.
- `zipalign -c -P 16 -v 4` passed; ARM64 native translation ELF LOAD alignment is `0x4000`. Google translator model download is separate from the APK.
- Delivery branch: `apk-tools-0.4.0`. Direct link: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-tools-0.4.0/raabta-keyboard-0.4.0.apk
- File page fallback: https://github.com/gityy34/keybord-ai-/blob/apk-tools-0.4.0/raabta-keyboard-0.4.0.apk . Open in Chrome, sign in with repository access, then Download raw file / View raw. The repository remains private if already private.

## Functional changes

| Requirement | Delivered behaviour | Verification/limit |
| --- | --- | --- |
| Suggestions collect on left | Equal-width slots spanning the row; strongest of 3+ candidates in centre; single candidate uses full centred width | Layout width/centre checks and existing 320-dp/landscape bounds checks pass |
| Choose suggestion count | Corrections and suggestions → Number of suggestions → 2 / 3 / 4, default 3 | Actual strip capacity tested; fewer slots appear if fewer matches exist |
| Preserve repeated English/Urdu words | After three completed typing uses, local frequency-ranked prefix suggestions; up to 500 word entries, language separated | Persistence/reload, English/Urdu, three-use threshold, pasted-word exclusion, off switch and clear controls tested; only letter words 2–40 characters are learned |
| Privacy of learning | Password/private/no-suggestions/email/URL fields excluded; no sentences/context/voice history saved | Field/disabled-learning regression tests; explicit word completion required |
| Roman Urdu | Roman layout offers Urdu-script common-word completions including hai → ہے; optional switch and explicit converter | Replacement and punctuation/unknown-name preservation tested; finite original lexicon, not universal transliteration |
| Swipe Backspace | Left drag highlights increasing previous words; release deletes the selection; reverse/cancel keeps text; ordinary tap/hold still work | Selection/release/cancel/reverse/changed-host tests, hold timing and lifecycle tests pass; requires compatible bounded extracted text; no swipe in private fields |
| Responsive input | Text commits immediately, suggestions debounce 16 ms; pooled buttons, no keyboard reconstruction for each character; flat button animations and row alignment | Existing typing/bounds regressions; physical typing latency still needs OPPO measurement |
| Voice language controls | Quick Urdu / English / Auto bar on microphone; changing tab ends the old session, then microphone starts selected mode; Mostly wording removed | Quick controls and Android 14 language detection/switch intent tests pass; actual recognition/Auto accuracy depends on provider |
| Translation panel | Toolbar ⇄ or Tools → Translation. Type a private in-memory draft or explicitly load selected text, swap Urdu/English, Convert, review, Insert | Explicit-download/draft/preview/late-result/editor-anchor tests use a fake engine; real Google model output/offline operation require phone acceptance |
| Urdu voice → English | Translation → Download model → Speak → insert. Source direction sets recognizer language; Stop or chosen long pause translates and inserts when result ready | Controller-to-translator-to-editor path tested with fake speech/translation; provider/model latency and quality need phone test; errors keep typing usable |
| AI Write | Tools → AI Write: rewrite/correct/formal/friendly/shorten/continue; choose English/Urdu; configurable trusted OpenAI-compatible HTTPS connection; Generate → review → Insert | Controlled request/payload/redirect/size/cancel/header/connection tests; no provider yet per user, so live output remains untested and requires configuration |
| Security review | Official SDK/sample inspection, licences/notices, dependency checksums, manifest/TLS/key/cancel checks | See WRITING_TOOLS_0.4_SECURITY.md; closed SDK is not certified malware-free |

Ordinary typing remains offline. INTERNET/network-state permissions allow explicit translation downloads and optional AI; translation text is processed on-device, while Google may collect SDK diagnostics. AI sends only its tool draft to the user-configured endpoint after Generate; optional token uses Android Keystore AES-GCM. No provider/master key is bundled. Never enter API secrets in chat. Voice uses the phone's separate speech service.

## OPPO acceptance checklist

1. Install over Raabta, verify version 0.4.0. In Notes/WhatsApp, type `he` and confirm balanced suggestions; test 2/3/4 settings. Type a new letter-only word followed by Space three times, then its prefix; test Urdu words and Clear learned words.
2. Select Roman Urdu layout and type `hai`; tap `ہے`. Try other common phrases and names; unsupported Roman spellings remain unchanged in the converter.
3. Write several words, drag left from Backspace: selection must grow without losing an initial character. Release, reverse, cancel, hold, and test a long field and app switch.
4. Check Space cursor movement, number/symbol modes, settings preservation, rapid typing, rotation and floating keyboard. Report exact app/steps for any clipping or hang.
5. Open Translation, download the Urdu model with Internet access. Type/translate a short phrase, then switch to airplane mode and repeat. Check pronunciation-independent translation meaning against known English/Urdu text.
6. In Urdu → English Translation, press Speak → insert, speak Urdu with short pauses, Stop. Confirm a single English insertion; verify Cancel/switching field never inserts late text. Repeat reverse direction.
7. General microphone: test Urdu, English and Auto separately on the system provider. Keep On-device only off for initial tests. Auto may be unsupported even on Android 16 if the provider lacks models/switching.
8. Configure a trusted backend/model in Writing tools → AI connection when available; test encrypted-token storage/reopen, Generate/Insert, cancellation, bad credentials and network loss. Check selected text is preserved when a request fails.

No claim of perfect Urdu ASR, broad grammar/glide parity, zero crashes, or Gboard-equivalent device performance follows from these tests. Online GIF search remains a separate future feature. Release signing and a production distribution/security assessment remain later work; this is the retained-key debug testing APK.
