# Raabta Keyboard

An original Android keyboard built with Kotlin and Android's real input-method APIs. Normal typing works offline.
The implementation covers **phase 0 and phase 1**, the **phase 2 customization foundation**,
plus user-requested **voice typing** ahead of phase 6,
of the [project requirements](docs/PROJECT_REQUIREMENTS.md).
Android **8.0 or newer** is required.

## Install and start typing

1. [Download the APK 0.2.0 (customization + voice update) directly](https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-phase-2-0.2.0/raabta-keyboard-0.2.0.apk), or obtain it from GitHub Actions below. Sign in to GitHub first if the repository is private.
2. Open the APK on your Android phone. If Android asks, allow your browser/file manager to install this app.
3. Open **Raabta Keyboard**.
4. Tap **1 · Enable Raabta**, find **Raabta Keyboard**, and turn it on. Android displays its standard keyboard-access warning. Menu names vary by manufacturer.
5. Return to the app. Tap **2 · Choose Raabta**, then select **Raabta Keyboard**.
6. Tap the test message field and type. Then test in WhatsApp or your notes app.

Use Shift once for one capital, twice (or hold it) for Caps Lock, and once more to turn Caps Lock off.
Use **?123** for numbers, **=\<** for extra symbols, and **ABC** to return to letters.
Hold Backspace to delete repeatedly. Enter becomes Search, Send, Next, or Done when the active app requests it.
The globe opens Android's keyboard picker; the gear opens setup. Android's Back button/gesture hides the keyboard.

Test `hello world`, `Ab`, `ABC`, punctuation, numbers, deletion, and multiline Enter. Try airplane mode,
fast typing, switching apps, and reopening/rotating the keyboard. See the full [phone acceptance checklist](docs/ROADMAP.md).
The user reported successful basic phone testing on 2026-10-09. Extended app compatibility,
rotation, offline scenarios, and sustained daily use still need specific results.

## Customize your keyboard

Open Raabta setup using the launcher or keyboard gear, then choose:

- **Phone theme**, **Light**, or **Dark**; keyboard and setup colors follow the saved choice.
- **Compact**, **Normal**, or **Tall** height. Landscape uses shorter rows; wide screens center the keys.
- **Always show number row** to add digits above the letters.
- **Vibrate on keypress** and **Sound on keypress** independently. Sound is off by default; feedback respects Android's haptic/touch-sound settings and silent mode. Missing hardware never interrupts typing.

Reopen the keyboard to apply changes. Settings persist without saving typed content. Keys use native press ripples;
held Backspace and held Shift remain supported. Changing the setup theme clears its disposable test fields.
Phone comfort, rotation and sustained typing still need real-device acceptance; no Gboard performance parity is claimed.

## Voice typing

The 0.2.0 download uses the same debug signing certificate as the delivered 0.1.0 APK and can update it
without uninstalling. Version 0.1.1 failed its voice test on the user’s OPPO Reno 11 5G (reported Android 16), with an unsupported-language/model error. The 0.2.0 compatibility change needs another phone test; actual speech success is not yet established.

1. In Raabta setup, choose **English voice**, **Urdu voice**, or **English + Urdu voice** (Android 14+, provider support required). This changes dictation language, not the key layout.
2. Focus a normal text field and tap the keyboard's microphone. If permission is missing, tap **Allow microphone**,
   grant Android's permission, return to the text field, and tap the microphone again. Permission never starts recording by itself.
3. Speak while **Listening…** is shown. Tap **■** to stop and process, or **×** to cancel without inserting a result.
4. Only the final recognized text is inserted. Ordinary typing, moving the cursor, hiding the keyboard,
   or switching fields/apps cancels the session and prevents late insertion into the wrong editor.

Voice is disabled in password fields, fields marked private by the host app, and non-text fields.
The installed system speech service is now the default, without forcing an offline model. It may process audio online.
**On-device only** is an optional setup switch for Android 12+ with a supported installed language model; leave it off for the initial retest.
English and Urdu availability depend on that service and its installed language models. Mixed mode requests automatic detection/switching between English and Urdu; the provider may not support or honor it. It is not app-level bilingual transcription, and older Android versions show a message instead of pretending to switch. Missing service,
unsupported language, denied permission, silence, network errors, and timeouts show a message and leave typing usable.
No typed context is sent, and the keyboard stores no recordings or transcript history.
See [voice validation and phone checks](docs/VOICE_VALIDATION.md) and [the combined customization/voice update](docs/PHASE_2_VALIDATION.md).

## Download future APKs from GitHub Actions

After these files are pushed to this repository:

1. Open [Actions](https://github.com/gityy34/keybord-ai-/actions).
2. Open the latest successful **Android debug APK** run for your branch.
3. Under **Artifacts**, download `raabta-keyboard-debug-<commit>`.
4. Unzip the download and install `app-debug.apk`. `app-debug.apk.sha256` is its checksum.

The workflow runs on pushes, pull requests, and manual dispatch. It installs Java 17 and Android SDK 35,
runs unit/service tests and Android lint, builds a debug APK, and uploads the APK and validation reports.
Actions versions were checked against their current official documentation during implementation.
Source code and the Gradle wrapper are tracked; APKs, caches, SDKs, signing keys, and local config are ignored in the development branch.
The separate `apk-phase-1-0.1.0`, `apk-voice-0.1.1`, and `apk-phase-2-0.2.0` branches contain verified APKs as direct-download fallbacks while cloud access to
the Actions API is blocked. Future builds should use Actions artifacts or release assets.

Debug builds made on different machines may use different signing keys. If an update says “App not installed”,
uninstall the older debug build, install the new APK, and enable/select the keyboard again.
Use this debug APK for personal testing; release signing is a later task.

## Build locally

Install a full **JDK 17** and Android SDK **platform 35 / build-tools 35.0.0** using Android Studio or Android command-line tools.
Set `ANDROID_HOME` to your SDK, or put its location in the ignored `local.properties` file:

```properties
sdk.dir=/your/path/to/android-sdk
```

From the repository root:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
JUnit and Robolectric run regression tests using Android framework simulation; lint warnings fail the build.
The wrapper pins Gradle 8.11.1 and verifies the distribution's SHA-256.

For this Linux x86_64 Codex cloud machine (Python 3.12+, curl required):

```sh
python3 scripts/bootstrap-cloud.py
source scripts/cloud-env.sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The bootstrap installs pinned, checksum-verified toolchains in `/workspace/toolchains`, accepts the Android SDK licenses,
uses the machine's trusted Java CA store, and keeps machine configuration outside the checkout.
Each cloud task is already isolated: use the existing checkout; do not create a Git worktree unless explicitly requested.

## Privacy and scope

The app has **no internet permission**, API key, AI SDK, analytics, clipboard history, or permanent typing capture.
Typing goes directly to the active app through `InputConnection`. Passwords are typed locally like other input;
ordinary typed text is not uploaded. Voice requests microphone permission only when explicitly requested;
the separate phone speech service may process audio online. Setup test fields are not saved and are cleared when the screen closes.
Android cloud backup and device transfer of app files are excluded.

English QWERTY, numbers/symbols, editor actions, setup, and core typing are implemented. Basic system-aware colors
and held Backspace are included as foundation conveniences. English/Urdu dictation is implemented using Android speech recognition.
Urdu/Roman Urdu key layouts, prediction, emoji/clipboard, AI and translation remain in their agreed [later phases](docs/ROADMAP.md).
Theme, height, number-row and sound/haptic controls are implemented; broader customization remains a future goal.

Architecture and all future requirements are documented in `docs/`. Validation evidence and known limits are in
[VALIDATION.md](docs/VALIDATION.md).
