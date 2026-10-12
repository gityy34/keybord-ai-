# AI connection review — 12 October 2026

## Choice and current evidence

The existing configurable OpenAI-compatible connection is retained. The new **Groq** preset uses
`https://api.groq.com/openai/v1/chat/completions` with editable model `openai/gpt-oss-20b`.
It uses the common `AIProvider` / `AIService` layer for grammar, rewriting, tones, replies, summaries,
explanations and AI language detection/translation. No provider-specific SDK or new dependency was added.

The current official [Groq Python SDK README](https://github.com/groq/groq-python/blob/main/README.md)
and [client](https://github.com/groq/groq-python/blob/main/src/groq/_client.py) were fetched over verified
HTTPS (200) on this date. They document developer-key authentication, the chosen model and the HTTPS
service. The Android adapter implements the documented compatible request/response format independently.
This verifies documentation compatibility, **not successful live inference**.

Groq is a candidate developer-tier service; current numerical free quotas, eligibility and trial/pricing
terms could **not** be verified in this cloud runtime: requests to the official
[rate-limit documentation](https://console.groq.com/docs/rate-limits), model documentation and API were
denied by the managed CONNECT proxy (403). Check these official pages and the account dashboard before use.
This APK promises neither free access nor unlimited requests. Model availability and account limits can change.

A possible anonymous service was also investigated. The current default `main` branch of
[Pollinations](https://github.com/pollinations/pollinations), checked at
`b3ae927f511e6348b80ca6935f11ed156db7af6b`, directs users to sign up and create API keys.
Its current [API documentation](https://github.com/pollinations/pollinations/blob/main/APIDOCS.md)
describes authenticated access/BYOP and discourages newly embedding legacy raw publishable keys.
The older `master` documentation describes anonymous endpoints and is not current evidence.
No undocumented/shared key or anonymous endpoint was shipped.

## Live-test gate

There is no usable AI API credential in this environment. The user confirmed they have no key yet.
The coding assistant's own authentication is not a developer API key for this app. No account was created,
key invented, public secret reused, private key bundled or plaintext credential saved.
The required **one live verified provider remains outstanding**, as do current free-tier verification and
real-phone end-to-end AI acceptance. Controlled HTTPS transport regressions do not satisfy that gate.

The saved cloud configuration draft adds `api.groq.com` and `console.groq.com` while retaining previous
destinations. It declares optional user-provided `RAABTA_GROQ_API_KEY`, destination `api.groq.com` only.
Saving a draft does not apply its network policy or supply a key. Review/save/publish through environment
settings is required before cloud live tests. Phone BYOK configuration is independent of this cloud draft.

## Secure phone setup and later provider changes

1. Open **More → Connections → Change Provider / Use My Own API Key** (or full **Settings → Connections**).
2. Choose **Groq**. The endpoint/model preset fills the form; it does not silently replace a saved connection.
3. Use **Get Groq developer key** to open the official dashboard, verify current eligibility/limits and create
   your own key. Enter it only in the phone's password field; do not send it in chat or commit it.
4. **Save**, then **Test Connection**. This makes a real small API request on a worker. A saved form alone is
   shown as configured/not verified. Only a successful response records the last successful connection test.
5. **Activate**, return to your app, select/type a sentence and tap **Fix Grammar**. Review before Replace/Insert.
6. Use **Deactivate** to keep credentials but stop AI, **Delete** to erase credentials, or choose **Custom** to
   use a trusted HTTPS OpenAI-compatible backend/model. An optional token supports secure proxy deployments.

The preset requires a key. A connection error never blocks ordinary typing. For a production service used
by many people, deploy an authenticated backend/proxy with server-side provider credentials and quotas;
this update does not deploy or pay for such infrastructure.

## Security review and practical limits

- Existing tokens remain AES-GCM ciphertext with non-exportable Android Keystore keys; suffix-only display.
  Key entry is excluded from screenshots, autofill and view-state saving; it clears on save/stop.
  Endpoint changes discard the previous token; model/key/endpoint changes invalidate successful-test state.
- HTTPS only, normal TLS trust, no permissive certificates, HTTP fallback or credential redirects.
  Bounded input/output, fixed timeouts, no automatic retries and no request/key/editor logging.
- Network requests run off the UI thread; generation/field guards discard late results after cancellation
  or editor changes. AI runs only through explicit AI tool actions. Grammar taps and rewrite/tone choices
  intentionally submit their displayed draft. Typing, learning, emoji and suggestions do not use AI.
- Password/private/non-text editors block writing/voice and automatic clipboard previews. Drafts and results
  stay in memory. Cloud/device backup is disabled. Explicit result Copy uses the system clipboard.
- Authentication, unavailable models, quota, network, provider outages, malformed responses and timeout
  errors map to friendly messages; stack traces and raw provider responses are not displayed.
- No third-party source implementation was copied. Existing verified dependencies and the
  [0.4 dependency/privacy review](WRITING_TOOLS_0.4_SECURITY.md) remain applicable. This is not a certification
  of closed SDK internals or remote-provider privacy practices.
- A last successful test is historical status, not a claim of permanent connectivity. AI quality, Urdu
  accuracy, latency and formatting depend on the provider/model and require actual phone testing.
