# Raabta Keyboard phase-one APK download

This branch contains the installable debug APK for personal phone testing.
It is a one-time delivery fallback because the cloud environment cannot currently access the GitHub Actions API.
Application source, tests, and the normal APK build workflow are on `phase-1-keyboard`.
Future builds should use Actions artifacts or release assets rather than adding binaries to source branches.

[Download the APK directly](https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-phase-1-0.1.0/raabta-keyboard-debug.apk)

For a private repository, sign in to GitHub with the account that can access this repository first.
Android 8.0+ is required. Download the APK, install it, open Raabta Keyboard, enable it in Android settings,
then select Raabta in the keyboard picker. Test in the built-in field and your messaging/notes apps.

Version: 0.1.0. Package: dev.raabta.keyboard. Size: 885,600 bytes. Debug signed.
SHA-256: c2b98607e85ed7ff56ad7f80961b311d9449ac65d89c752b3ba331ff287811d4

English typing, shift/caps, numbers/symbols, space, enter, and backspace are implemented.
16 tests, Android lint, APK build and signature verification passed. Real-phone typing acceptance is pending.
This APK requests no network permission and contains no API keys or typing history.
