# Raabta Keyboard

An original, offline Android keyboard built with Kotlin and Android's real input-method APIs.
This first implementation covers **phase 0 and phase 1** of the [project requirements](docs/PROJECT_REQUIREMENTS.md).
Android **8.0 or newer** is required.

## Install and start typing

1. Download the `raabta-keyboard-debug` APK supplied with this delivery, or obtain it from GitHub Actions below.
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
Phone results are still required before calling this a stable daily keyboard.

## Download future APKs from GitHub Actions

After these files are pushed to this repository:

1. Open [Actions](https://github.com/gityy34/keybord-ai-/actions).
2. Open the latest successful **Android debug APK** run for your branch.
3. Under **Artifacts**, download `raabta-keyboard-debug-<commit>`.
4. Unzip the download and install `app-debug.apk`. `app-debug.apk.sha256` is its checksum.

The workflow runs on pushes, pull requests, and manual dispatch. It installs Java 17 and Android SDK 35,
runs unit/service tests and Android lint, builds a debug APK, and uploads the APK and validation reports.
Actions versions were checked against their current official documentation during implementation.
Source code and the Gradle wrapper are tracked; APKs, caches, SDKs, signing keys, and local config are ignored.

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

Phase 1 has **no internet permission**, API key, AI SDK, analytics, clipboard history, or permanent typing capture.
Typing goes directly to the active app through `InputConnection`. Passwords are typed locally like other input;
no text is uploaded. Setup test fields are not saved and are cleared when the screen closes.
Android cloud backup and device transfer of app files are excluded.

English QWERTY, numbers/symbols, editor actions, setup, and core typing are implemented. Basic system-aware colors
and held Backspace are included as foundation conveniences. Urdu/Roman Urdu, prediction, emoji/clipboard,
voice, AI, translation, theme controls, and sound/haptic settings remain in their agreed [later phases](docs/ROADMAP.md).

Architecture and all future requirements are documented in `docs/`. Validation evidence and known limits are in
[VALIDATION.md](docs/VALIDATION.md).
