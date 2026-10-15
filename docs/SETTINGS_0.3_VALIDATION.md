# Screenshot settings and local tools validation — 0.3.0

Date: 2026-10-10. Source branch `keyboard-settings-0.3.0`. Version code 5.
The user's screenshots are functional references; no Gboard application code, logos, assets or media were copied.
The full original requirements and this screenshot update remain in PROJECT_REQUIREMENTS.md.

## Navigation and presentation

Keyboard gear opens category pages within the IME and retains the current editor connection. Back returns
through categories to typing. Preference changes rebuild the view in the same editor and keep the current
settings page. Android's keyboard-settings entry uses KeyboardSettingsActivity, distinct from the launcher's
enable/select/test onboarding. All category pages share headings, descriptions, switches, sliders and choices.
Dark colours use neutral surfaces and a light blue accent; light/system themes remain available.

## Functional scope

| Screenshot area | Implemented behaviour | Practical scope |
| --- | --- | --- |
| Key visibility | Main/symbol number row, emoji/globe/comma/full-stop keys | Changes actual layout, not labels only |
| Appearance | Font size/default, emoji size/default, themes and height | Three themes/heights; larger emoji reduce row count |
| Layout | One-handed left/right; compact floating with title dragging | IME touch region excludes unused floating area; phone app/inset gate remains |
| Shortcuts | Double-space full stop, auto-space punctuation, apostrophe return to letters | Optional; automatic text features blocked in private/password fields |
| Symbols | Hint labels, held/flick symbols, flick sensitivity and hold duration | Latin symbols and Urdu letter variants; release/cancel prevents extra letter |
| Feedback | Separate sound/volume and vibration/duration, native key popups | Honors phone settings and handles unavailable devices |
| Cursor | Space sliding and editor arrows/home/end | Grapheme boundaries using extracted text; native arrow fallback; mixed bidi needs phone checks |
| Suggestions | Stable shared word/emoji/next-word strip with reusable buttons | Small original offline lexicon, English/Urdu/Roman Urdu emoji keywords |
| Corrections | Opt-in typo correction on Space; basic spell/grammar marking | Known typos and simple English agreement rules; apps may strip underline spans |
| Filtering/replies | Offensive-word filter; simple greeting reply suggestions | Small word block list and local greeting patterns; no full conversational AI |
| Contacts | Opt-in prefix-matched names | Explicit permission, worker query, bounded cache, no storage; denial disables |
| Languages | English, native Unicode Urdu, Roman Urdu QWERTY switch | Urdu extra/held variants; Roman Urdu is Latin typing, not automatic transliteration |
| Emoji rows/picker | Fast/recent row, symbols row, sizes and picker | Recent explicitly chosen emoji stored; private emoji choices are not saved |
| Stickers/GIFs | Four original offline smile/heart PNG/GIF items | Secure cache provider and host rich-content support; no online catalog/search |
| Clipboard | Explicit paste panel; opt-in copied text/image suggestions | Two-minute in-memory expiry, sensitive-clip guard; compatible hosts required for images |
| Clipboard items | Phone, email, URL and common street-address extraction | Bounded pattern matching, not general address understanding |
| Screenshots | Optional new screenshot previews while IME visible | Explicit image access, selected-photo limits, filename/timestamp matching; no archive/background capture |
| Dictionary/privacy | Explicit add/remove words and clear dictionary/recent emoji/previews | No automatic typing history, analytics, transcript or contact storage |
| Glide | Opt-in exact dictionary-path recognition | Starter engine, not probabilistic Gboard-quality glide recognition |
| Editor tools | Select all/copy/cut/paste/undo/redo via host, cursor navigation | Host may reject actions; message shown instead of claiming success |
| App icon/share | Launcher alias visibility and APK-page sharing | Settings remain accessible from IME/Android when icon hidden |

Online translation, AI rewriting and online media search remain unconnected. Rate-us is omitted because
there is no published store listing; no nonfunctional store button was fabricated. Cloud runtime Gboard/Clever
inspection remains blocked; supplied screenshots now provide the UI reference directly. Later models/media
providers require the original privacy, licensing, quota and explicit-action requirements.

## Verified automated checks

`source scripts/cloud-env.sh` followed by `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` passed.
**85 tests executed; 85 passed; zero failures, errors or skipped tests. Android lint: 0 issues.**
Existing typing, Unicode deletion, editor actions, voice guards/Stop buffering and saved-setting tests passed.
New checks exercise in-IME settings without launching an activity, category-only Android settings,
editor preservation after number-row changes, stale replacement rejection, password suggestion guards,
English/Urdu/Roman Urdu emoji matching/insertion, word completion, grapheme cursor navigation and repeated
Space slide steps, ordinary Space, held/flick symbol release, cancellation when settings open, typo correction,
double-space punctuation, grammar markup, dictionary explicitness, emoji row sizing, native Urdu variants,
floating drag/bounds, expiry/item extraction, permission denial/partial photo access, sensitive clipboard,
media-provider file restrictions, and independent host-JVM GIF decoding of two distinct animation frames.

Suggestion updates are delayed 40 ms without delaying synchronous key commits. Six suggestion buttons are
reused; ordinary characters do not rebuild the keyboard. Contacts/media queries use bounded workers and
callbacks check editor/session identity. These implementation choices are not physical latency measurements.

APK v2 signature verified against the retained debug certificate:
`fa7096ff0c83f98bbbbceab4d223a6e2eaff8b9a6c162350f3f36c8b220ad045`.
Package `dev.raabta.keyboard`, min SDK 26, target SDK 35, version 0.3.0/code 5.
Declared permissions: microphone, optional contacts/images/selected photos (legacy storage through SDK 32),
and normal vibration permission. No Internet permission. Separate system speech recognition may use network.

## Download

- Direct: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-keyboard-0.3.0/raabta-keyboard-0.3.0.apk
- File page: https://github.com/gityy34/keybord-ai-/blob/apk-keyboard-0.3.0/raabta-keyboard-0.3.0.apk
- Install over 0.2.x; same retained signing key. Open in Chrome/sign in for private repository access,
  then choose Download raw file/View raw if an in-app browser opens a page without downloading.
- APK bytes: 1,071,154. SHA-256: `bbcbf4c884ae4a3ff5fe18a61f6344bb4e3a5f257c7d106deb721f36555ce34b`.

## OPPO acceptance still required

1. Gear → Preferences → change number row/size/feedback → Back: original text field and text remain.
2. Type rapidly in Notes/WhatsApp/Chrome; move on Space in English, Urdu, mixed RTL/LTR and joined emoji.
   A Space tap inserts one space; sliding/held symbols must not insert extra characters.
3. Type love/pyar/محبت/birthday; tap suggested emoji. Toggle suggestions/emoji rows and check actual changes.
4. Try one-handed and floating mode, title dragging, hide/reopen/rotation and returning to full size.
   In floating mode confirm taps outside the keyboard reach the app and layout/insets remain correct.
5. Test clipboard text/image/GIF/undo in compatible apps, sensitive/private fields, expiry, contacts denial,
   and full vs selected-photo screenshot permissions. Confirm normal typing survives unsupported actions.
6. Retest the retained voice Stop/long-pause controls, English/Urdu recognition and final-word completion.

No absolute crash-free guarantee, measured Gboard parity, universal host support or upgraded speech model is
claimed. Physical performance and daily typing comfort require real-phone evidence. Hosted Actions status
remains unverified while its API is unavailable; APK publication is verified by native Git byte comparison.
