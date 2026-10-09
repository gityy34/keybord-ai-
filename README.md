# Raabta Keyboard voice update 0.1.1

Download raabta-keyboard-voice-0.1.1.apk in this branch. Android 8.0+.
This debug-signed personal test build uses the same signing certificate as the delivered 0.1.0 APK;
install it over the existing app. Source and validation are in the voice-typing branch.

Direct APK: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-voice-0.1.1/raabta-keyboard-voice-0.1.1.apk
Sign into an authorized GitHub account in your mobile browser if the repository is private.

Choose English voice or Urdu voice in setup. Tap the keyboard microphone, allow microphone permission,
return to your text field and tap it again to dictate. Tap the square to stop/process, or x to cancel.
Urdu recognition requires device/service language support. On-device recognition is preferred;
otherwise the system speech provider may process audio online. Normal typing remains local.

Cloud validation: 31 tests passed, Android lint 0 issues, build passed, v2 APK signature verified.
Actual microphone recognition remains to be checked on your phone. The adjacent SHA-256 file verifies
this APK's content. No private signing key is included in this branch.
