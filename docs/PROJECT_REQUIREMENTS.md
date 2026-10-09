I am continuing a project from a previous ChatGPT conversation. Treat everything below as the full project context and requirements. Do not reduce, simplify, or ignore requirements unless technically necessary. I am not an experienced developer, so you should act as the senior Android engineer, software architect, UI/UX designer, QA engineer, and DevOps engineer for this project.

## 1. Main Goal

I want to build my own Android AI keyboard inspired by the concept of CleverType AI Keyboard.

I do NOT want to illegally copy CleverType's proprietary code, assets, branding, exact UI, or protected design.

I want an original keyboard with similar useful capabilities, but ideally:

- faster
- smoother
- lighter
- more private
- more useful for English + Urdu + Roman Urdu
- easier to maintain long-term
- usable without paying for infrastructure during the first stage

For now, this keyboard is primarily for my own Android phone.

The first priority is not publishing it commercially.

The first priority is:

**A fully working, very fast, smooth Android keyboard that I can install as an APK and use daily on my own phone.**

---

# 2. What We Learned From CleverType

CleverType is not simply a keyboard.

Its concept is approximately:

**Normal keyboard + AI writing assistant + voice typing + translation + custom AI actions**

Important CleverType-style features discussed were:

- normal typing
- grammar correction
- spelling correction
- rewrite/paraphrase
- professional tone
- friendly tone
- shorten text
- expand text
- translation
- AI assistant
- custom prompts / AI assistants
- voice typing
- themes
- emoji / GIF / stickers
- floating keyboard
- split keyboard
- tablet/foldable features

The main concept that matters to us is the AI toolbar.

Typical flow:

User types something in WhatsApp/Gmail/Instagram/etc.

Example:

`i am not coming office today`

User taps:

**Grammar Fix**

AI returns:

`I am not coming to the office today.`

Or user taps:

**Professional**

AI may return a more professional version.

The keyboard should be capable of reading selected/current text when permitted, sending only the required text to the AI action, receiving the result, and inserting/replacing the text in the active app.

However, AI should NOT run on every key press.

That would make the keyboard slow, expensive, dependent on the network, and bad for privacy.

---

# 3. Core Product Principle

The keyboard must have two clearly separated systems.

## Layer 1 — Fast Local Keyboard

This must work without internet.

It includes:

- letters
- space
- backspace
- enter
- shift
- caps
- numbers
- symbols
- punctuation
- basic suggestions
- language switching
- emoji access
- clipboard access
- settings
- haptic feedback
- optional key sound

This layer must remain extremely fast.

AI must never block normal typing.

---

## Layer 2 — Optional AI Actions

AI should run only when the user explicitly chooses an AI feature.

Example toolbar:

`Fix | Rewrite | Professional | Friendly | Shorten | Expand | Translate | Ask AI`

Possible future actions:

- Reply
- Summarize
- Explain
- Improve
- Formal
- Casual
- Social Caption
- Email
- Custom Prompt

The keyboard must remain usable even if:

- internet is unavailable
- Gemini/API is unavailable
- free quota is exhausted
- AI provider changes

---

# 4. Languages

The keyboard should ultimately support:

1. English
2. Urdu
3. Roman Urdu

There must be an easy language-switch button.

Possible switch flow:

`English → Urdu → Roman Urdu`

or another clean UX that you recommend.

Roman Urdu is especially important.

Examples:

`ap kahan ho`

`main ghar ja raha hun`

`kal meeting kis time hai`

Eventually we want useful Roman Urdu suggestions/corrections, but do not make advanced prediction block the first working version.

---

# 5. Version 1 Feature Target

The first useful version should ideally contain:

- English keyboard
- Urdu keyboard
- Roman Urdu support
- Numbers
- Symbols
- Shift
- Caps Lock
- Backspace
- Enter
- Space
- Language switch
- Emoji
- Clipboard
- Voice typing
- Grammar Fix
- Rewrite
- Professional tone
- Friendly tone
- Shorten
- Expand
- Translate
- Ask AI
- Light theme
- Dark theme
- vibration toggle
- sound toggle
- privacy settings
- secure API configuration

Do not overcomplicate Version 1 if a feature would make the keyboard unstable.

A reliable keyboard is more important than having every feature immediately.

---

# 6. Features That Can Wait Until Later

These are lower priority:

- swipe/glide typing
- very advanced word prediction
- GIF search
- sticker system
- cloud accounts
- login system
- subscription/payment system
- shared cloud history
- multi-device sync
- public marketplace
- custom AI assistant marketplace
- advanced floating keyboard
- tablet/foldable optimization
- Windows version

They can be added after the basic Android keyboard becomes stable.

---

# 7. Architecture We Discussed

Long-term architecture can eventually become:

**Android Keyboard**
↓
**Main Android App / Control Center**
↓
**AI layer / optional backend**
↓
**AI provider**

The keyboard is the quick interface.

The main Android app can eventually contain:

- settings
- themes
- API settings
- prompt manager
- custom AI commands
- privacy controls
- model selection
- history if the user enables it
- language settings
- future account features

But for the first free personal version, avoid unnecessary infrastructure.

Do NOT introduce a backend server unless it is genuinely required.

---

# 8. Free-First Requirement

This project currently needs to be built with almost zero financial resources.

Prefer:

- open-source libraries
- Android native APIs
- free services
- on-device processing
- free-tier APIs when necessary
- GitHub
- GitHub Actions
- Gradle

Avoid unnecessary paid services.

Before using any AI API, verify its current free-tier availability and current limits.

Do not assume an old pricing/free-tier structure is still valid.

If one AI provider becomes paid later, architecture should make it easy to replace the provider.

---

# 9. AI Provider Strategy

The previous idea was to use a free-tier AI provider such as Gemini for actions like:

- grammar correction
- rewrite
- professional tone
- friendly tone
- shorten
- expand
- Ask AI
- translation fallback

But implementation must be provider-independent where reasonably possible.

Create an abstraction such as:

`AiProvider`

with an implementation such as:

`GeminiAiProvider`

so a future provider can be substituted without rewriting the keyboard.

Example conceptual API:

```kotlin
interface AiProvider {
    suspend fun process(
        action: AiAction,
        text: String,
        customPrompt: String? = null
    ): Result<String>
}
```

You may improve this architecture.

---

# 10. API Key Security

The first personal version may use my own API key.

Do not hard-code the API key directly into a public GitHub repository.

Use an appropriate secure local configuration method.

For production-level architecture later, API keys should not be exposed inside a distributed client application.

However, because Version 1 is primarily for my own phone, choose the safest practical free method and clearly explain any remaining security limitation.

GitHub secrets may be used where appropriate for CI.

Never accidentally commit secrets.

Add sensitive files to `.gitignore`.

---

# 11. Translation

Use a local/on-device translation approach where practical.

Previously we discussed options such as Google ML Kit on-device translation.

The principle is:

**Prefer local translation where supported.**

Use AI translation only when required.

Again, verify current library/API availability before implementation.

---

# 12. Voice Typing

Voice typing should preferably use Android/system speech recognition where possible.

Requirements:

- English voice input
- Urdu voice input if supported by the device/service
- simple microphone button
- permission handled correctly
- failure states handled gracefully
- voice feature must not crash normal keyboard typing

---

# 13. Android Keyboard Technical Foundation

Use Kotlin.

Build this as a real Android Input Method Editor.

Use the proper Android IME APIs such as:

`InputMethodService`

Use `InputConnection` appropriately for:

- committing text
- deleting text
- reading available context
- replacing selected/current text
- handling editor actions

Be extremely careful around app compatibility.

The keyboard should eventually be tested in:

- WhatsApp
- Gmail
- Chrome
- Instagram
- Messages
- Notes/text editors
- search fields

---

# 14. Important Privacy Requirements

Privacy is a major feature.

Requirements:

- Do not permanently save everything the user types.
- Never silently upload all keystrokes.
- AI only receives text when an AI action actually requires it.
- Do not send passwords.
- Disable AI features in password fields.
- Respect sensitive input types.
- Do not log sensitive text.
- Avoid storing clipboard contents unnecessarily.
- Provide optional clipboard auto-clear later if practical.
- Add an incognito/private typing mode if practical.
- Clearly explain what information is sent to the AI provider.
- Keep normal typing local.

When detecting sensitive fields, use Android input type/editor information correctly.

---

# 15. Performance Requirements

Performance is one of the highest priorities.

I want the keyboard to feel very fast.

Requirements:

- keyboard should open quickly
- keystrokes should appear instantly
- rapid typing should not drop letters
- backspace should not lag
- long-press backspace should behave correctly
- shift should respond immediately
- language switching should not freeze
- AI network calls must never block the keyboard UI
- normal keyboard must still work offline
- RAM usage should stay reasonable
- battery usage should stay reasonable
- avoid unnecessary background services
- avoid unnecessary analytics
- minimize app startup overhead

Use coroutines/background work correctly where required.

Do not perform network activity on the UI thread.

---

# 16. UI Direction

Do not clone CleverType's exact visual appearance.

Make our own clean professional UI.

Style goal:

- modern
- minimal
- comfortable key size
- readable
- clean spacing
- fast
- Android-friendly
- good dark mode
- good light mode

Main keyboard approximately:

```text
[ Suggestions / AI Toolbar ]

Q W E R T Y U I O P
 A S D F G H J K L
  ⇧ Z X C V B N M ⌫

🌐   😊   ,   SPACE   .   ↵
```

This is only conceptual.

Design it properly.

AI toolbar can initially scroll horizontally if needed.

---

# 17. Suggested AI Toolbar

Initial buttons:

```text
Fix
Rewrite
Professional
Friendly
Shorten
Expand
Translate
Ask AI
```

If all buttons make the UI crowded, create a compact arrangement.

Example:

```text
Fix | Rewrite | Tone | Translate | AI
```

Then Tone can open:

- Professional
- Friendly
- Formal
- Casual

You may choose the better UX as long as functionality remains easy.

---

# 18. Text Processing Behavior

AI features should work on text approximately in this priority:

1. selected text, if text is selected
2. otherwise meaningful text around the cursor
3. otherwise current composing text
4. otherwise clearly inform the user that text is required

Do not blindly send an entire private conversation when only one sentence is necessary.

Prefer the smallest relevant text context.

For replacement:

- preserve cursor behavior
- avoid duplicating text
- allow cancellation where practical
- avoid replacing unrelated text

---

# 19. Roman Urdu

Roman Urdu is a key differentiator.

Eventually I want features such as:

- Roman Urdu suggestions
- common spelling normalization
- optional Roman Urdu → Urdu conversion
- Urdu → Roman Urdu
- English → Urdu
- Urdu → English
- context-aware AI rewrite

Do not try to perfect an entire NLP system before getting the keyboard working.

Start with a simple maintainable approach.

---

# 20. Main App / Settings Screen

The APK may contain both:

1. IME keyboard service
2. normal Android configuration app

The app can provide screens such as:

## Setup
- Step 1: Enable keyboard
- Step 2: Select keyboard
- Step 3: Test typing

## Languages
- English
- Urdu
- Roman Urdu

## Appearance
- Light
- Dark
- System
- key vibration
- key sound

## AI
- enable/disable AI
- API configuration
- model configuration if required
- test AI connection

## Privacy
- private mode
- clear stored data
- clipboard options
- explanation of AI data usage

## About
- version
- GitHub/build info if useful

Keep the settings app simple.

---

# 21. Setup Experience

Because I am not an advanced developer, the app should make keyboard setup easy.

On first launch, show something like:

1. Enable keyboard
2. Choose this keyboard as current input method
3. Open test field
4. Start typing

Where Android requires the user to manually approve keyboard access, guide the user clearly.

Do not attempt insecure workarounds.

---

# 22. GitHub Workflow

We decided to use GitHub as the project repository.

I will connect GitHub to Codex.

You should work directly inside the connected repository.

If the repository is empty:

- initialize the Android project
- add `.gitignore`
- create clean folder structure
- configure Gradle
- add README
- add CI

Do not create unnecessary generated/binary files in Git.

---

# 23. GitHub Actions

One major goal is that I do not need to keep Android Studio running locally just to generate test APKs.

Create GitHub Actions so the cloud can build the project.

Desired flow:

**Codex writes code → pushes/commits to GitHub → GitHub Actions builds APK → I download/install APK on Android phone → I test → I report problems → you fix them**

Create CI that at minimum:

- checks out project
- sets up Java
- configures Gradle
- runs relevant tests/lint where practical
- builds debug APK
- uploads APK as workflow artifact

Use currently supported GitHub Actions versions.

Do not use obsolete actions.

---

# 24. APK Goal

At the end of each stable stage I want an installable APK.

Initially:

`debug APK`

Later, if needed:

`signed release APK`

Do not expose signing secrets in the repository.

---

# 25. Development Phases

Follow development in controlled phases.

## Phase 0 — Repository Inspection

Before changing anything:

- inspect repository
- understand existing files
- report what currently exists
- identify any broken/incomplete setup
- do not overwrite useful existing work unnecessarily

If repo is empty, initialize it.

---

## Phase 1 — Minimum Working Keyboard

Create a genuinely functioning Android keyboard.

Required:

- InputMethodService
- alphabet typing
- space
- backspace
- enter
- shift
- caps
- numbers/symbols
- IME registration
- setup screen
- enable/select keyboard flow
- basic test field

Goal:

**I can install APK, select keyboard, open WhatsApp or a text field, and type reliably.**

Do NOT add complex AI before this works.

---

## Phase 2 — UI + Stability

Add:

- professional key layout
- key press feedback
- light theme
- dark theme
- settings
- vibration toggle
- sound toggle
- long-press/backspace behavior
- better orientation handling
- performance optimization

Goal:

**Fast daily-usable keyboard foundation.**

---

## Phase 3 — Languages

Add:

- English
- Urdu
- Roman Urdu mode
- language switch
- Unicode Urdu layout
- basic word suggestions where practical

Do not sacrifice typing reliability.

---

## Phase 4 — Productivity Features

Add:

- clipboard
- emoji
- suggestion strip
- useful shortcuts if appropriate

Keep performance good.

---

## Phase 5 — AI Layer

Add AI toolbar:

- Grammar Fix
- Rewrite
- Professional
- Friendly
- Shorten
- Expand
- Translate
- Ask AI

Requirements:

- asynchronous
- loading state
- error handling
- timeout handling
- retry where sensible
- network failure does not affect typing
- API quota error shown clearly
- provider architecture separated from UI

---

## Phase 6 — Voice + Translation

Add:

- microphone
- speech recognition
- language-aware voice typing
- local translation where feasible
- AI fallback where necessary

---

## Phase 7 — Privacy Hardening

Add/test:

- password detection
- sensitive input behavior
- private/incognito mode
- no sensitive logs
- clipboard privacy
- secure API configuration
- clear data controls

---

## Phase 8 — Optimization and Testing

Test:

- fast repeated typing
- fast backspace
- punctuation
- emoji
- Urdu
- Roman Urdu
- rotation
- app switching
- keyboard reopening
- no internet
- weak internet
- AI timeout
- API errors
- memory pressure
- light/dark mode
- password fields
- text selection and replacement

Test in multiple common apps.

---

# 26. Quality Standard

Do not consider a phase complete simply because the code compiles.

Each phase should have acceptance criteria.

A feature should be considered finished only if:

- it compiles
- basic tests pass
- app/keyboard does not crash
- it works in a real IME flow
- error cases are considered
- README is updated
- build pipeline still works

---

# 27. Code Quality

Please use professional engineering practices:

- clean Kotlin
- clear naming
- modular structure
- comments only where useful
- no huge monolithic classes
- avoid duplicated logic
- centralized constants
- dependency version management where appropriate
- proper error handling
- no swallowed exceptions
- no secrets in source control
- reusable components
- future maintainability

Do not over-engineer unnecessarily.

---

# 28. Long-Term Requirement

I want this project to remain maintainable for many years.

Therefore:

- avoid unnecessary vendor lock-in
- isolate third-party APIs
- document important architecture decisions
- keep dependencies minimal
- avoid abandoned libraries if possible
- prefer official Android components
- keep data formats migratable
- do not design something that becomes impossible to update in two years

---

# 29. Future Expansion

Later the same project may expand into:

- advanced AI assistant
- chat mode
- work/productivity mode
- custom prompts
- personal AI commands
- cloud backend
- synchronized settings
- Windows companion app
- possibly other platforms

Long-term conceptual architecture may become:

```text
Android Keyboard
       |
       v
Android Main App
       |
       v
Shared AI / Service Layer
       |
       +---- Gemini
       +---- Other AI provider
       +---- Local model
       |
       v
Future Windows App
```

But do NOT build unnecessary cloud systems now.

---

# 30. What I Expect From Codex

Do not only explain how I could build it.

Actually build the project inside the connected GitHub repository.

You are responsible for:

- architecture
- Android project setup
- Kotlin code
- Gradle configuration
- manifest
- IME XML/configuration
- layouts/UI
- settings
- language files
- AI abstraction
- API integration
- voice integration
- translation integration
- privacy controls
- tests
- CI
- GitHub Actions
- documentation
- debugging
- fixing build failures

When something can be done directly in the repo, do it instead of merely telling me to do it.

Only ask me to perform tasks that genuinely require user interaction, such as:

- connecting GitHub
- providing an API key
- enabling the keyboard on my Android phone
- selecting it as the default keyboard
- installing an APK
- testing behavior on the real phone
- reporting a device-specific issue

---

# 31. How To Communicate With Me

I am not an expert developer.

Explain user-facing steps simply.

For example:

Instead of:

`Register the IME through system input method settings.`

Say:

`Open Settings → System/Additional Settings → Keyboard & Input Method → Manage Keyboards → turn on our keyboard.`

When the Android menu differs by manufacturer, explain that names may vary.

Do not overwhelm me with raw technical jargon unless it is necessary.

---

# 32. Important Working Rule

Do not try to build the entire final product in one giant change.

Work phase-by-phase.

At the end of each phase provide:

- what was built
- files changed
- what was tested
- whether build passed
- APK/workflow status
- exact next step for me
- known limitations
- next development phase

If GitHub Actions fails, inspect the logs and fix the root cause.

Do not repeatedly ask me to copy large amounts of code manually if you have repository write access.

---

# 33. Important Product Decisions Already Made

These decisions have already been discussed and should be treated as agreed unless there is a serious technical reason to change them:

- Android first
- personal-use first
- free-first
- Kotlin
- proper Android IME
- normal typing works offline
- AI only on demand
- no AI on every key press
- English + Urdu + Roman Urdu
- voice typing
- translation
- emoji
- clipboard
- grammar correction
- rewriting
- tone change
- Ask AI
- dark/light theme
- privacy-focused
- password fields protected
- no permanent capture of everything typed
- GitHub repository
- GitHub Actions
- APK artifacts
- phone used for real-device testing
- no backend needed for the earliest version unless technically necessary
- architecture should allow a backend later
- no CleverType branding/code copying
- first priority is speed and reliability

---

# 34. Things CleverType May Do Better Today, But We Want To Improve

Our differentiation should eventually be:

### Better speed

Normal typing should never wait for AI.

### Better offline behavior

Core keyboard works without internet.

### Better Urdu / Roman Urdu experience

This is a major priority.

### Better privacy

Send only text explicitly required for an AI action.

### Simpler architecture

No unnecessary cloud dependency in personal Version 1.

### Better maintainability

AI provider can be replaced.

### Cleaner UX

Do not overcrowd the keyboard with features.

---

# 35. Do Not Do These Things

Do NOT:

- clone CleverType source code
- copy their logo/name/assets
- copy their exact UI pixel-for-pixel
- run AI on every key press
- log every keystroke
- send password fields to AI
- hard-code private API keys
- block typing while AI is working
- require internet for normal typing
- introduce paid services without discussing them
- add a backend only because it is fashionable
- build dozens of features before the keyboard foundation works
- claim something is tested if it has not actually been tested
- silently ignore build errors
- make the project dependent on one AI vendor unnecessarily

---

# 36. First Task To Start Right Now

After GitHub is connected:

1. Inspect the repository completely.
2. Tell me whether it is empty or already contains project files.
3. If empty, initialize a clean native Android Kotlin project.
4. Configure a real `InputMethodService`.
5. Build the minimum working English keyboard.
6. Add a simple setup/configuration activity.
7. Create GitHub Actions to build a debug APK.
8. Run the build/tests.
9. Fix all build errors you encounter.
10. Make the resulting debug APK available through the GitHub Actions artifact.
11. Update README with exact installation/testing steps.
12. Tell me exactly what I need to do on my Android phone to test it.

Do not add Gemini or advanced AI until this minimum keyboard is genuinely stable enough to type with.

---

# 37. Phase 1 Acceptance Test

Phase 1 should not be called finished until all reasonably testable criteria below are satisfied:

- project builds successfully
- debug APK is generated
- keyboard appears in Android's keyboard management screen
- keyboard can be enabled
- keyboard can be selected
- pressing a letter inserts that letter
- space works
- enter works appropriately
- backspace works
- shift works
- caps works
- numbers/symbols work
- keyboard does not immediately crash
- regular typing does not require internet
- GitHub Actions can reproduce the APK build
- README explains installation

For things requiring my real phone, tell me what to test and wait for my actual result before claiming they passed.

---

# 38. Current User Situation

I am currently working from ChatGPT and intend to move the actual development into Codex with GitHub connected.

This prompt contains the context from the prior discussion so we do not need to restart requirement gathering.

Please continue from here as the implementation engineer.

Start by inspecting the connected repository and then begin **Phase 0 / Phase 1**.

Do not only give me another plan.

Perform the repository work that is possible now.