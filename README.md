# Raabta Keyboard 0.2.0

Android 8+. Install this debug-signed update over the previous delivered Raabta APK.
The signing certificate matches 0.1.0 and 0.1.1. Source and validation: phase-2-ui branch.

Direct download: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-phase-2-0.2.0/raabta-keyboard-0.2.0.apk
Sign into an authorized GitHub account in your mobile browser if this repository is private.

Includes system/light/dark themes, compact/normal/tall height, optional digit row,
vibration/sound toggles, landscape layout improvements, and native press feedback.

Voice now uses the installed system speech service by default without forcing an offline model,
to address the user-reported unsupported-language/model error. Leave On-device only OFF for the retest.
Choose English voice first, then try English + Urdu voice on Android 14+ with a supporting provider.
Mixed recognition depends on provider language/model support; actual phone success is not claimed yet.
The system speech provider may send audio online. Ordinary typing remains local.
Microphone permission never begins recording automatically. Errors remain visible in the keyboard.

44 automated tests passed, Android lint 0 issues, build passed and v2 signature verified.
Phone voice recognition, comfort and sustained performance still need real-device testing.
No private signing key is included. The SHA-256 file validates this exact APK.
