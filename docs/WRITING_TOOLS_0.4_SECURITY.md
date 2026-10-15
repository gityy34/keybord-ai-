# Writing tools 0.4: dependency and data-handling review

## Reviewed references and reuse

- Official Google ML Kit sample, `googlesamples/mlkit`, commit `f1047837fe1e02d58063bfc30b830fad23ffbc35`: reviewed translation API, model downloads, translator cleanup and Apache-2.0 sample licence. No sample source/assets were copied. https://github.com/googlesamples/mlkit/tree/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/translate
- FlorisBoard, commit `fe1241f4921b3eae923571ff7a3e113a7e10d677`: existing Apache-2.0 reference checkout; original Raabta gesture implementation retained. No code/assets copied. https://github.com/florisboard/florisboard/tree/fe1241f4921b3eae923571ff7a3e113a7e10d677
- Google/CleverType proprietary application code was not obtained or copied.
- Reused **Google ML Kit Translate 17.0.3**, downloaded through the official Google Maven HTTPS repository. Its public `TranslateLanguage` API explicitly includes `URDU` and `ENGLISH`. SDK licence is **ML Kit Terms of Service**, distinct from the sample's Apache licence: https://developers.google.com/ml-kit/terms
- SDK declares OkHttp 3.0.0. An explicit dependency resolves **OkHttp 4.12.0**, with Okio 3.6.0, instead of shipping that old version. This is a risk reduction, not a claim that every component is vulnerability-free.
- Gradle SHA-256 dependency verification now pins resolved build/runtime dependencies. Missing lint/tool checksum entries were compared with exact bytes fetched from official Google Maven / Maven Central before adding those entries. Hashes ensure reproducibility/integrity; they do not prove absence of malware.
- Bundled ML Kit third-party notices and Apache-2.0 licence are included in APK assets under `licenses/`.

## Data handling

| Feature | Data stored or sent | Control |
| --- | --- | --- |
| Normal typing | No text upload | Commit directly to the current Android editor |
| Learned words | Up to 500 word-frequency entries, language separated, in app-private preferences; no sentences, context, timestamps or voice draft | Learn switch and Clear learned words; password/private/email/URL/no-suggestions fields excluded; only words completed by keyboard typing are counted |
| Explicit dictionary/recent emoji | Local app-private preferences | Existing dictionary/privacy controls |
| Roman Urdu | Original small offline common-word lexicon | Optional Roman Urdu suggestions; explicit converter; unknown words unchanged |
| Translation | Text processed by downloaded model on-device | Explicit Download model action; remove Urdu model control; translation does not trigger model download automatically |
| Translation SDK | Google model downloads and possible SDK usage/device diagnostics | Disclosed in settings and download status; SDK's automatic initialization provider removed; SDK starts only for explicit translation/model actions |
| System voice | Audio handled by Android's selected recognizer, potentially online | Microphone action/permission, language tabs, Stop/Cancel; provider controls recognition quality and language switching |
| AI Write | Only the tool draft plus fixed chosen editing instruction/model sent to the user's configured backend | Explicit Generate; no bundled provider/key; preview then Insert; disabled in private fields |
| Optional AI token | AES-GCM encrypted with a device Android Keystore key; no plaintext preference | Non-exported connection activity, secure window, no autofill/view-state persistence; endpoint change drops old token; remove connection deletes token/key |

Internet and network-state permissions are required for explicit model downloads and optional AI. They do not mean ordinary typing is transmitted. Google SDK diagnostics may continue through its transport services after translation use. The SDK is partly closed source; this review cannot prove its complete internal behaviour or certify it malware-free. No analytics service was added by Raabta itself.

## Request and editor protections

AI endpoint must be HTTPS, end in `/chat/completions`, and have no embedded credentials/query/fragment or literal loopback/IP host. TLS/hostname verification use platform defaults. Redirects and automatic retries are disabled, header tokens reject control characters, connection/read timeouts are bounded, request text is limited to 2,000 characters and response bytes to 64 KiB. No request bodies, credentials or provider error bodies are logged. Returned content is plain text, never executed or rendered in a web view.

Drafts/results stay in memory and clear on close, editor change, hiding or teardown. Asynchronous work uses generation/revision/connection checks; inserting a result also verifies the editor anchor. Selection over 2,000 characters is rejected rather than silently replacing a larger selection with a truncated result. Swipe deletion snapshots bounded extracted text, selects while dragging, checks the selection/text before deletion and cancels safely on text/editor changes.

## Verified limits and phone gates

This is a source/dependency/manifest review with regression tests, not a full independent malware audit or a live provider penetration test. Automated network tests use a controlled HTTPS transport; no real AI provider is configured. Android Keystore encryption and model translation quality require phone verification. The ARM64 translation library's ELF LOAD segments have `0x4000` (16 KiB) alignment; APK alignment/signature checks are recorded in validation. No package scanners or unknown GitHub binaries were executed.

The SDK explicit initializer was inspected for repeat-call behaviour. A synchronized initialize-once wrapper prevents its already-initialized exception on subsequent download/conversion/model-removal actions. The real SDK initialization and two translator creations/closures are exercised in a regression test without downloading models; translation text/quality remains a phone gate.
