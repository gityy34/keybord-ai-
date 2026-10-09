# Phase 0 / phase 1 validation

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
- Full visible touch/typing validation: **pending**. The initial software emulator encountered System UI ANRs and a system-service restart (`DeadSystemException` across Android processes), interrupting UI automation. This is not recorded as a passing typing check or a proven application defect. A retry uses one CPU core and the supported direct SwiftShader renderer.

## GitHub Actions

The committed workflow reproduces the same SDK/packages and test/lint/build commands and uploads APK/checksum
and validation reports. Its currently supported action versions were verified against official documentation and tags.
Local success is not evidence that a hosted Actions run completed. Hosted run status and artifact availability
will be recorded after successful publication and verification; until then they remain **unverified**.

The current cloud network policy initially excluded `api.github.com`, and the GitHub API request was rejected
by the proxy. Existing native HTTPS Git read access worked. No credential values were extracted or requested.

## Real phone gate

**Pending user testing.** Do not call phase 1 fully accepted or daily-use stable until the phone checklist
in [ROADMAP.md](ROADMAP.md) is completed. WhatsApp/Gmail/Chrome/Instagram/Messages and real-device orientation,
latency, and vendor-specific behavior have not been validated here.

All later product phases remain tracked in the roadmap and original requirements.
