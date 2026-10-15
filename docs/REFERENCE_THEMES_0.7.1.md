# Raabta 0.7.1 reference themes

The existing native Kotlin IME is updated in place. Three additional built-in themes adapt the supplied neon-glass, charcoal, and white references. Choose **Settings → Appearance → Theme**, or **More → Themes**. The selection persists and applies when the keyboard redraws/reopens. Existing System, Light, Dark, Midnight, Neon Orbit, Paper White, Spectrum and imported themes remain available. Other presentation preferences and saved AI connections are preserved.

| Theme | Appearance | Actual keyboard preview |
| --- | --- | --- |
| Neon Glass | Navy glass gradients, cyan/violet edge illumination, colored icons, ringed microphone, blue/purple action gradient and spacebar indicator | [Preview](themes/previews-0.7.1/neon-glass.png) |
| Soft Charcoal | Charcoal raised surfaces, subdued borders, colored icons, pink microphone, violet action gradient | [Preview](themes/previews-0.7.1/soft-charcoal.png) |
| Cloud White | White raised keys/cards, gray modifier keys, dark text, colored icons and blue actions | [Preview](themes/previews-0.7.1/cloud-white.png) |

The reference's duplicated uppercase/lowercase rows are illustration artifacts: the working QWERTY/Urdu/Roman layouts are retained. Existing optional number row, symbol hints, cursor slide, swipe deletion and suggestions remain. No proprietary branding/assets or unsupported extra reference actions are introduced.

## Implementation and files

- `ui/Palette.kt`: central semantic colors, seven distinct toolbar tints, surface styles, modifier/action/space roles and action-gradient stops. Existing palettes and imported color-only JSON retain their original flat styling.
- `ui/ThemeSurfaceDrawable.kt`: reusable static vector gradients, subtle raised surfaces, glass edge illumination and space indicator; geometry/shaders cache on resize. No bitmap backgrounds, new dependencies, animated key glow or delay on typing.
- `ui/KeyboardPreferences.kt`: stable named built-in selections; previous preference names remain valid.
- `ui/ThemePicker.kt`: real styled key/action previews; new options follow existing built-ins, imported slot remains last. Tap saves the actual selected theme.
- `ime/KeyboardView.kt`: role-based key backgrounds and selected/unselected themed toolbar surfaces; existing functional actions retained.
- `ime/SuggestionStrip.kt`: rounded themed suggestion surface with dividers derived from visible 2/3/4 slots; existing ordering, equal widths, local learning and tap behavior preserved.
- `ui/EmojiPanel.kt`: search/category/back buttons share styled surfaces.
- `voice/VoiceLevelView.kt`: cyan RMS waveform in Neon Glass; existing actual-recognizer sampling, animation lifecycle and other palettes preserved.
- `ui/ReferenceThemeTest.kt`: theme persistence without preference loss, readable surface/action/icon contrast, actual key/panel renders, same-height navigation, narrow English/Urdu/Roman fitting and real candidate/action callbacks.
- `app/build.gradle.kts`: version0.7.1/code10; existing toolchain, application and ABI splits unchanged.

AI/translation/Tools/voice result cards and selectors inherit the new styles through their existing Palette calls. They remain inside the keyboard's occupied area, with existing Back/Insert/Replace behavior. Full Settings still uses the real settings Activity. Saved provider/backend logic is unchanged.

## Validation

Command: `source scripts/cloud-env.sh && ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug --console=plain`.

**BUILD SUCCESSFUL**, **162 tests, zero failures/errors/skips**, lint **No issues found**. A first lint run identified one KTX style error; it was corrected and the full checks rerun. No tests disabled, lint baseline or network security changes.

27 native Android previews rendered for the three themes: keys, Tools, AI cards, translation, voice, theme panel and narrow English/Urdu/Roman with1.5x font. Actual previews inspected against the three supplied reference images. Existing157 regression tests also retain layout/landscape bounds, typing/undo/redo/cursor/backspace, voice state/backend guards, translation and secure-provider failure handling.

ARM64 APK: **21510024 bytes**; SHA256 `4f9f355521a3634fe115601b952a1d9999db2b1f64da683b1fa72df6bbfa325a`. V2 signature and `zipalign -c -P16 4` verified. Retained certificate SHA256 `fa7096ff0c83f98bbbbceab4d223a6e2eaff8b9a6c162350f3f36c8b220ad045` matches previous APKs. Install over existing Raabta to retain settings.

Source branch: `keyboard-reference-themes-0.7.1`. APK branch: `apk-reference-themes-0.7.1`.

[Download APK](https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-reference-themes-0.7.1/raabta-keyboard-0.7.1.apk)

[Fallback file page](https://github.com/gityy34/keybord-ai-/blob/apk-reference-themes-0.7.1/raabta-keyboard-0.7.1.apk). Open in Chrome and sign into GitHub if the repository is private, then Download raw file/View raw. Authenticated Git byte verification is distinct from anonymous HTTP access.

## Genuine limitations

Native Robolectric UI renders and regression fixtures are automated evidence, not physical OPPO acceptance or a guarantee of zero crashes. Check the selected theme in a real host app on the phone. AI still requires a user-configured real provider/key; none is bundled and no live AI success is claimed. Android's speech provider still determines audio startup and Urdu/mixed recognition accuracy. Theme import remains a safe local color-only16KB JSON slot; these advanced surface styles are built-in, not executable imported themes. Production signing is unchanged; this is a debug-signed phone-testing build. Earlier implementation details and gates: [0.7 report](POLISH_0.7_VALIDATION.md).
