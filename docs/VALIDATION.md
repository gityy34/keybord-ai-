# Phase 0 / phase 1 validation

This records the original 0.1.0 delivery. The subsequent user-requested 0.1.1 voice update is recorded
separately in [VOICE_VALIDATION.md](VOICE_VALIDATION.md).

## Repository inspection

- Initial HEAD: `77cb3de59b15b26ecb0ef4488bf4c99626b7b709` (initial commit).
- The selected `main` revision contained only a 13-byte README. No Android project, CI, agent instructions, tests, lockfiles, or user changes existed.
- Implemented in the existing checkout; no worktree was created.

## Current cloud build

- Full Temurin JDK 17.0.20+8; Android platform 35; build-tools 35.0.0; Gradle 8.11.1.
- Android Gradle Plugin 8.9.2; Kotlin 2.1.20; JUnit 4.13.2; Robolectric 4.14.1.
- `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`: **passed**.
- **16 tests executed, 16 passed, 0 failed, 0 skipped** (3 state, 10 IME/service, 3 setup/manifest tests).
- Android lint: **0 issues** with warnings treated as errors.
- Debug APK generated; Android v2 APK signature verified using `apksigner`.
- APK permission inspection: **no requested permissions**, including no internet permission.
- `python3 scripts/bootstrap-cloud.py`, environment activation, and the same build command were exercised again successfully; unchanged build outputs were reused.

Tests exercise visible key callbacks through the actual service class and Android `InputConnection` editing
under Robolectric. They cover rapid repeated input (440 characters), spaces/newlines, one-shot Shift,
Caps Lock, symbols/numbers, supplementary Unicode deletion, selection deletion, editor actions/fallbacks,
numeric/password fields, held Backspace cancellation, absent connections, setup launch, and manifest privacy.
Robolectric simulates Android; it cannot establish physical-device compatibility or touch latency.

## Installed APK / emulator

An Android 9 (API 28) AOSP software emulator was prepared using official checksum-verified artifacts.
The cloud machine has no `/dev/kvm`; hardware acceleration is unavailable.

- APK installation: **passed** (`adb install` reported Success).
- Android IME registration and enable/select operations: **passed**.
- Setup activity launched and displayed its test fields and selected-keyboard status: **passed**.
- Android's input-method service reported Raabta selected and input shown after focusing the test field.
- Full visible touch/typing validation: **blocked in this machine**. The initial software emulator encountered System UI ANRs and a system-service restart (`DeadSystemException` across Android processes), interrupting UI automation. This is not recorded as a passing typing check or a proven application defect. A retry with one CPU core, 2 GB guest RAM, and the direct SwiftShader renderer installed the final APK successfully, but the input command timed out during UI testing. Both test emulators were stopped. Complete touch/typing acceptance on the phone or a hardware-accelerated emulator.

## GitHub Actions

The committed workflow reproduces the same SDK/packages and test/lint/build commands and uploads APK/checksum
and validation reports. Its currently supported action versions were verified against official documentation and tags.
Source and workflow were successfully pushed to the `phase-1-keyboard` branch. Local success is not evidence
that a hosted Actions run completed. Hosted run status and artifact availability remain **unverified**
because the current proxy rejected the GitHub API request, and the private Actions page is unavailable
through an unauthenticated web request. Open the repository's Actions page while logged into GitHub to
view the latest branch run and download its artifact after success.

The current cloud network policy initially excluded `api.github.com`, and the GitHub API request was rejected
by the proxy. Existing native HTTPS Git read and push access worked. No credential values were extracted or requested.
Reusable `install_script` and `start_skill`, plus additive `api.github.com` access, were saved in the environment draft;
the draft must be reviewed/saved and published in environment settings. Saving alone does not apply or publish it.

## Delivered APK

- File: `/workspace/artifacts/raabta-keyboard-debug.apk`
- Size: 885,600 bytes (about 865 KiB)
- SHA-256: `c2b98607e85ed7ff56ad7f80961b311d9449ac65d89c752b3ba331ff287811d4`
- Android 8.0+; debug signed; package `dev.raabta.keyboard`; version `0.1.0`.
- A matching copy of the tested APK was published on the separate `apk-phase-1-0.1.0` download branch as a one-time fallback for blocked Actions API access. The source branch still excludes generated binaries; signing keys were never committed.
- Direct download: https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-phase-1-0.1.0/raabta-keyboard-debug.apk (requires repository access when private).

## Real phone gate

**Basic phone functional acceptance: passed, reported by the user on 2026-10-09.**
After the installation/enable/select and typing-test instructions, the user reported completing the tests
and that every tested feature works properly. This is user-reported real-device evidence, not a cloud test result.
No immediate keyboard failure or functional issue was reported.

The phone model, Android version, tested host apps, and individual results for rotation, rapid typing,
airplane mode, numeric/password fields, and sustained daily use were not supplied. Keep those extended
checks in [ROADMAP.md](ROADMAP.md) open rather than inventing coverage. Hosted Actions verification also
remains open as documented above. Phase 2 can build on the accepted basic typing foundation.

All later product phases remain tracked in the roadmap and original requirements.
