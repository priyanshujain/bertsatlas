# Bert's Atlas Product Specification

- Status: V1 implementation baseline
- Product name: Bert's Atlas
- Repository codename: funwithflags
- Tagline: Getting nerdier every day.

## 1. Product summary

Bert's Atlas is a lightweight, offline geography trivia game for adults who enjoy knowing oddly specific things. It starts with flags and core world geography, presents them through fast quiz runs, and makes every answer entertaining enough that learning happens naturally.

The app is a relaxing game for nerds, not a course, study plan, career tool, or school product. It should feel playful, quick, and welcoming while still rewarding genuine knowledge.

The first release runs entirely offline on Android and iOS. It is built with Kotlin Multiplatform and Compose Multiplatform, with Compottie used for bundled character and interface animations.

## 2. Product principles

### Start immediately

A new player should reach their first question with one tap. Configuration, accounts, tutorials, and category menus must not stand between launch and play.

### Fun first, learning underneath

The primary promise is an enjoyable trivia game. Immediate corrections, memorable facts, intelligent repetition, and progress tracking create learning without presenting the experience as a lesson.

### Pressure without punishment

Time affects scoring and creates energy, but running out of time never locks the player out of answering. Mistakes are useful and do not cost lives or create waiting periods.

### Personality without imitation

The app can share the immediacy, warmth, and polished reactions associated with successful learning games, but its travel-scrapbook identity, mascot, language, illustrations, animation, and mechanics must be original.

### Offline means offline

Once installed, every V1 feature works in airplane mode. No account, backend, advertisements, analytics service, remote configuration, or downloaded asset is required.

## 3. Audience and positioning

### Primary audience

- Adult casual trivia fans
- People who enjoy flags, maps, countries, and surprising facts
- Players looking for a fun 10–30 minute daily activity
- Beginners and experienced geography nerds, supported by adaptive difficulty

### Voice

- Clever, warm, concise, and occasionally irreverent
- Celebrates curiosity rather than academic achievement
- Comfortable laughing with the player, never at them
- Avoids childish praise, corporate productivity language, and teacher-like lectures
- Uses plain international English in V1

Example tone:

- Correct: "Bert's glasses remain undefeated."
- Wrong: "A noble guess. Unfortunately, Belgium has entered the chat."
- Slow but correct: "The clock gave up. You didn't. Half points."

Final copy will be reviewed separately for clarity, repetition, cultural sensitivity, and localization readiness.

## 4. V1 goals

- Make starting a satisfying quiz nearly effortless.
- Make flag recognition the strongest and deepest category.
- Include enough broader geography to keep repeated sessions varied.
- Support short, repeatable play sessions totaling roughly 10–30 minutes per day.
- Reward both correctness and speed.
- Teach something memorable after mistakes and at pleasant moments during a run.
- Track knowledge locally and revisit weak material without exposing a complicated learning system.
- Deliver equivalent core functionality on Android and iOS.

## 5. V1 non-goals

- Guided world journey or level map
- Accounts, sign-in, profiles, or cloud sync
- Backend services or runtime network access
- Online multiplayer, leaderboards, or social feeds
- Local pass-and-play competition
- Lives, energy, gems, purchasable currency, streak repair, or waiting timers
- Advertisements or behavior tracking
- User-created quizzes or downloadable content packs
- Map-tapping questions
- Street View, satellite imagery, or a 3D globe
- Historical geography, subnational regions, or territory packs
- Live statistics such as current population, GDP, or political office holders
- Push notifications
- Localization beyond English

These may be reconsidered after V1 usage and quality are understood. They are not architectural requirements for the first release.

## 6. Core experience

### 6.1 First launch

The first screen contains:

- Bert and the Bert's Atlas wordmark
- The tagline, "Getting nerdier every day."
- One prominent `Start` button

`Start` is the only interactive choice on the first-launch screen. System accessibility and silent-mode preferences are honored without requiring setup.

Tapping `Start` immediately begins a quiz with these defaults:

- Duration: 5 minutes
- Geography: worldwide
- Question mix: flags-heavy mixed geography
- Difficulty: adaptive

No configuration questionnaire or modal tutorial appears before the first quiz. The interface teaches mechanics contextually without delaying the first question.

### 6.2 Returning launch

From the second app launch onward, the home screen contains:

- `Start`, which immediately uses the most recently saved quiz configuration
- `Options`, a secondary action
- Current streak and a compact recent-progress summary
- Settings

The app must distinguish first launch from later launches using local state. Completing a quiz is not required for the returning layout to appear on the next launch.

### 6.3 Options flow

Options are configured as a short sequence of full-screen questions, one at a time. It is not a dense settings form.

The initial V1 sequence is:

1. "How much time have you got?"
   - 5 minutes
   - 10 minutes
   - 20 minutes
   - Unlimited — just for fun
2. "What are we nerding out about?"
   - Mostly flags
   - Capitals
   - Mixed geography

The final answer is saved and immediately starts the quiz. All answers are saved as one configuration. Abandoning the flow leaves the previously saved configuration unchanged.

Difficulty is always adaptive in V1 and has no player-facing configuration.

V1 does not ask the player to choose a question count or continent. These controls may be added later only if evidence shows they improve play without adding friction.

### 6.4 Quiz run

A run repeatedly moves through:

1. Present a question and start its countdown.
2. Accept an answer or an optional hint request.
3. Show immediate correct, incorrect, or overtime feedback.
4. Briefly explain the answer when explanation adds value.
5. Continue automatically or through one obvious action.
6. Occasionally present a full-screen fun fact.
7. End with a result screen when the selected duration expires.

Timed runs finish the question currently on screen when the session clock expires. They do not begin another question. Unlimited runs continue until the player chooses to finish; elapsed time and score remain visible.

The selected duration measures active answering time. The session and question clocks run only while an unanswered question is interactive. Both clocks pause during answer feedback, the hint chooser, fun-fact cards, lifecycle interruptions, and backgrounding. This gives the player the full chosen duration for play rather than charging them for reading.

An interrupted active run is saved locally and can be resumed after process death or relaunch. A `Finish` action is always available in unlimited mode. Finishing after at least five answered questions produces results and maintains the daily streak. Leaving any mode early, or finishing unlimited mode before five answers, is an abandoned run: answered questions still update mastery, but it produces no result, personal best, or daily-streak credit.

## 7. Question system

### 7.1 V1 question formats

#### Four-option multiple choice

Examples:

- Show a flag and choose its country.
- Show a country and choose its flag.
- Show a country and choose its capital.
- Show a capital and choose its country.
- Choose the true statement about a flag or country.
- Identify which country matches a durable geography fact.

Distractors must be plausible. Prefer the same region, visually similar flags, similar country names, or commonly confused capitals. Random obviously incorrect choices do not provide a worthwhile challenge.

#### Country-name letter bank

The player sees a flag, a row of empty character slots, and a shuffled bank containing every required letter plus decoy letters. Tapping letters fills the answer slots; filled letters can be removed before submission.

Rules:

- Spaces, hyphens, punctuation, and accepted short names are handled explicitly.
- The interface must not require accent marks unavailable in the presented letter bank.
- The content record defines the displayed answer and accepted aliases.
- The timer continues while the player composes the answer.
- Accessibility alternatives must expose letters and slot positions clearly to screen readers.

Letter-bank questions use one canonical display name; aliases are not separate valid slot patterns. Each letter tile has its own identity so repeated letters behave correctly. The bank contains three decoy tiles in addition to the required letters. The player can remove individual placed letters or clear the answer, and explicitly taps `Submit`; filling the final slot does not submit automatically.

### 7.2 Content mix

The default first-run catalog is weighted approximately as follows:

- 60% flag recognition and flag-to-country questions
- 15% reverse country-to-flag questions
- 15% countries and capitals
- 10% durable mixed geography and flag-symbolism questions

This is an editorial target rather than a promise that every short run has the exact ratio. The generator avoids immediate repetition and balances familiarity, difficulty, and weak topics.

### 7.3 Difficulty

Each content item has an editorial difficulty rating. The adaptive mode also considers the player's local history for the specific country and skill.

- Casual favors well-known countries and visually distinct flags.
- Tricky introduces less familiar countries and stronger distractors.
- Brutal favors commonly confused flags, less familiar capitals, reverse prompts, and letter-bank questions.
- Adaptive begins approachable, raises its selection tier after three consecutive unhinted correct answers, lowers it after two consecutive incorrect answers, and revisits weak items after errors.

Difficulty affects question selection and distractor quality, not arbitrary reduction of the response timer.

### 7.4 Repetition and mastery

The app stores mastery separately for each country and skill, such as recognizing its flag or recalling its capital. A skill is:

- `new` before its first attempt
- `learning` after it has been attempted but does not meet a higher threshold
- `familiar` after two unhinted correct answers in separate runs
- `mastered` after four unhinted correct answers across at least three local calendar days, provided none of its last three attempts was an unhinted error

Missed and hinted questions return later, but not immediately enough for the answer to remain in short-term memory. The question generator favors a mix of:

- Due weak-item review
- Material appropriate to the chosen difficulty
- Unseen material
- Occasional easy confidence-building questions

Within a run, a missed or hinted item may return only after at least five other questions. Across runs, learning items become eligible the next day, familiar items after seven days, and mastered items after 30 days. These are initial tuning rules, but any replacement must preserve the visible state meanings and be deterministic under a fixed random seed and fake clock.

## 8. Timing and scoring

Time pressure is central to scoring, but never prevents an answer.

### 8.1 Question timer

- Multiple choice starts with a 10-second response window.
- Letter-bank questions start with a 20-second response window.
- The interface warns visually and haptically near expiration without relying on color alone.
- When the timer reaches zero, the question stays active and the player may still answer.

Initial timer values are tuning defaults and may change after playtesting. A timing change must preserve the overtime rule.

### 8.2 Points

Each correct unhinted answer is worth up to 100 points.

- Before timeout: `50 + floor(50 × remaining milliseconds / response-window milliseconds)`, capped at 100
- At or after timeout: 50 points
- Incorrect: 0 points

Examples for either question format are 100 points at the initial instant, 75 points with exactly half the window remaining, 50 points just before zero after rounding, and 50 points at or after zero. The result view also shows accuracy so that a fast wrong player does not appear to have learned more than a careful correct player.

### 8.3 Hints

Each question permits at most one hint. Opening the hint chooser pauses the question timer. The player chooses among the hint types supported by that question:

- Reveal one correct letter
- Remove decoy letters or incorrect choices
- Reveal the continent
- Show a funny factual clue

All hint types use the same debit rule. When a hint is chosen, the app first deducts up to 25 points from the score already earned in the current run. The score can never become negative: a zero balance makes the hint free, while a balance below 25 is reduced to zero. A subsequent correct answer then awards points using the normal speed or overtime formula. An incorrect hinted answer awards zero.

A hinted correct answer is recorded as correct for accuracy and keeps the item in `learning`, but it does not satisfy the unhinted thresholds for `familiar` or `mastered`. Using a hint never blocks progression or triggers negative copy.

The hint cost and mastery weight are tuning values to verify in playtesting. The fixed player promise is that every offered hint type costs the same and a player with zero points can always obtain one.

## 9. Feedback and fun facts

### 9.1 Answer feedback

Every answer produces immediate feedback using text, iconography, motion, and optional sound/haptics.

- Correct feedback identifies the answer and celebrates briefly.
- Incorrect feedback clearly shows both the chosen answer and correct answer.
- Overtime correct feedback explicitly says that half points were awarded.
- A concise explanation appears when it can teach a useful distinction, such as how to separate the flags of Chad and Romania.

Feedback should normally take less than three seconds to read. The player can continue immediately and can disable automatic advance in accessibility settings.

### 9.2 Full-screen fun facts

Fun facts are celebratory palate cleansers rather than questions. They appear:

- Occasionally after a randomized gap of several answered questions
- At most once within any five-question span
- Once after the final answered question of every completed run and before the results
- Only when related to a country, flag, or concept recently shown

The card has no countdown. It includes a short heading, one concise fact, original supporting art where available, a Bert reaction, and one clear continue action. The random schedule must never show fact cards back-to-back or interrupt answer feedback.

Facts favor durable information, surprising comparisons, flag symbolism, geography oddities, and useful memory hooks. Facts that can become stale require a source and an `asOf` date and should generally be excluded from V1. Fact-card reading time never consumes the selected session duration.

## 10. End-of-run experience

The result screen shows:

- Total score
- Correct answers and accuracy
- Average response time
- Best streak of correct answers within the run
- Number of new or improved mastery items
- Countries the player should revisit
- `Play Again` using the same configuration
- `Home`

Personal bests are recorded separately for each duration and topic configuration. Unlimited runs are not directly ranked against timed runs.

## 11. Habit and progression

### 11.1 Gentle daily streak

- Finishing any quiz run counts toward the current local calendar day.
- Multiple runs in one day do not inflate the streak.
- Missing a day resets the number without guilt-oriented language or loss animation.
- There are no streak freezes, repairs, purchases, or notifications in V1.
- Date-boundary behavior uses the device's local calendar and is manually verified, including timezone changes.

### 11.2 Passport stamps

Players receive original collectible passport-style stamps for meaningful milestones, such as:

- First completed run
- Recognition of flags from every continent
- A sustained accuracy milestone
- Mastering a set of commonly confused flags
- Seven active days

Stamps celebrate progress and do not function as currency. No stamp is permanently missable.

### 11.3 Progress view

A lightweight progress view shows:

- Current and best daily streak
- Overall accuracy and total questions answered
- Flag, capital, and mixed-geography mastery
- Strong and weak countries or groups
- Earned passport stamps

It must avoid school-like grades and false claims about expertise.

## 12. Bert and visual direction

### 12.1 Mascot

Bert is an original anthropomorphic brain with oversized glasses. He is an enthusiastic fellow nerd, not a tutor, authority figure, or player avatar.

Core traits:

- Curious and delighted by obscure facts
- Expressive, slightly awkward, and good-natured
- Supportive after mistakes without excessive praise
- Never smug about a player's lack of knowledge

The provided brain-with-glasses reference establishes only the broad character archetype. Final artwork must use an original silhouette, facial design, proportions, clothing/accessories, poses, and rendering style. Its licensing status is unknown and it must not be shipped or traced.

### 12.2 Art direction

- Colorful travel scrapbook and sticker-atlas aesthetic
- Warm paper-inspired backgrounds with high-contrast content cards
- Bold, tactile shapes that remain readable at a glance
- Passport stamps, luggage labels, map fragments, pins, and flag motifs
- Original identity with no visual imitation of Duolingo characters, layouts, iconography, or signature green palette

### 12.3 Motion

Compottie animations support state and personality:

- Bert reacts to correct, incorrect, overtime, hint, streak, and milestone events.
- Answer cards respond immediately to selection.
- Passport stamps land with a short satisfying animation.
- Fun facts can use a restrained entrance and supporting loop.

Animations never carry essential information alone, never delay answering, and have static or simple-fade fallbacks. Every exported animation must be validated on both target platforms.

### 12.4 Sound and haptics

V1 may include a small bundled set of short interface and feedback sounds. It does not include spoken narration or voice quips.

- Sound and haptics have separate toggles.
- Device silent-mode conventions are respected.
- Sounds are short, compressed, and preloaded where appropriate.
- Correctness is always understandable with sound and haptics disabled.

## 13. Accessibility

- Support platform screen readers with meaningful labels and reading order.
- Never distinguish correct and incorrect states using color alone.
- Meet WCAG AA contrast targets for essential text and controls.
- Support scalable text without clipping core quiz content.
- Maintain practical touch targets, including letter-bank controls.
- Honor the operating system's Reduce Motion preference and provide an in-app motion override.
- Provide independent sound and haptic controls.
- Pause timers while system permission dialogs, app interruptions, or the hint chooser are active.
- Offer an accessibility setting that disables automatic feedback advance.
- Do not require rapid repeated tapping or drag gestures.

## 14. Geography catalog and editorial policy

### 14.1 Country canon

The V1 catalog uses the familiar 195-country convention:

- 193 United Nations member states
- Vatican City
- Palestine

Territories, dependencies, constituent countries, and partially recognized states are excluded from the main V1 answer pool. They may appear later in clearly labeled bonus packs.

### 14.2 Sensitive and ambiguous material

- Maintain a written, versioned policy for disputed borders, country names, and capitals.
- Use neutral wording and avoid questions whose correctness depends on taking a contested political position.
- Prefer familiar English display names while storing stable identifiers and accepted aliases.
- Document countries with multiple capitals or unusual capital arrangements instead of forcing an inaccurate single answer.
- Identify transcontinental classification decisions in catalog metadata.
- Review jokes and facts for stereotypes, conflict trivialization, and cultural disrespect.

### 14.3 Content quality

Each question or reusable fact has:

- Stable identifier
- Schema version
- Topic and difficulty
- Prompt and answer
- Supported question formats
- Plausible distractor rules or curated distractors
- Explanation or memory hook where relevant
- Source reference for factual claims
- Optional `asOf` date
- Accepted aliases
- Relevant country and continent identifiers
- Required asset identifiers

Build-time catalog validation rejects:

- Duplicate identifiers
- Missing answers or assets
- Duplicate or invalid choices
- Multiple valid answers presented as one-answer questions
- Letter banks that cannot construct the accepted answer
- Unsupported country references
- Unattributed nontrivial facts
- Incompatible question and hint combinations

### 14.4 Assets and licensing

- Track source, license, modification status, and attribution requirements for every externally sourced asset.
- Prefer compact vector flags and public-domain or permissively licensed geographic data.
- Store required attribution inside the app when a license requires it.
- Do not use the provided Bert reference image as a production asset.
- Avoid landmark photography in V1 because it adds licensing and application-size complexity.

## 15. Offline technical specification

### 15.1 Stack

- Kotlin Multiplatform for shared application and domain code
- Compose Multiplatform for shared Android and iOS UI
- Compottie for bundled Lottie-compatible animations
- Kotlin serialization for the bundled catalog
- Preferences DataStore for small settings and onboarding state
- Room KMP for structured progress, mastery, streaks, milestones, and interrupted runs

The immutable quiz catalog remains a versioned bundled resource rather than a prepackaged database. Mutable records use stable catalog identifiers so catalog updates do not erase player progress.

### 15.2 Shared boundaries

Shared code owns:

- Screens and presentation state where practical
- Quiz generation and deterministic randomization
- Timing state and score calculation
- Hint rules
- Mastery and review scheduling
- Streak and milestone rules
- Catalog parsing and validation
- Local repository contracts

Thin platform adapters own:

- Persistent-store construction and file locations
- Audio playback
- Haptics
- Operating-system Reduce Motion state
- Lifecycle and interruption signals

Interfaces should describe each platform capability directly. `expect`/`actual` declarations are reserved for small platform seams.

### 15.3 Bundled resources

The installed application contains:

- Versioned question and fact catalog
- Flag assets
- Bert and interface illustrations
- Compottie animation files and required fonts/assets
- Sound effects
- English strings

Use `compottie-lite` when the approved animations do not require expressions, plus resource-backed loading. Do not include the Compottie network module. Prefer vector-only animations and measure the effect of every asset group on release size.

### 15.4 Offline contract

A production V1 build:

- Declares no runtime network dependency.
- Does not request Android internet permission.
- Makes no HTTP requests.
- Contains no advertising, remote analytics, crash-upload, authentication, or remote-config SDK.
- Completes every core flow in airplane mode immediately after installation.
- Ships content corrections and additions through app releases.

Local state has explicit schema migrations. Corrupt or missing learner state recovers safely to defaults without affecting the bundled catalog.

## 16. Privacy

- All usage and progress data remain on the device.
- No account or personal data is requested.
- The app embeds no telemetry SDK and sends no product telemetry. Operating-system or store diagnostics outside the app's control are governed by the user's platform settings.
- Clearing app data or deleting the app may remove progress; the app states this plainly.
- Platform privacy declarations must match the no-collection implementation.

## 17. Performance and quality targets

- On the release test matrix, a cold launch becomes interactive within 2.5 seconds and the first question appears within 300 ms of tapping `Start` after launch.
- Tapping an answer produces visible feedback within 100 ms on supported devices.
- Quiz generation never blocks interaction or repeats a question back-to-back.
- Relaunch restores saved preferences and progress.
- An interrupted run resumes without changing its questions, score, or remaining time.
- Core screens remain usable on a 320 dp-wide Android viewport and a 320 pt-wide iOS viewport at 200% text scaling.
- Essential controls meet minimum touch targets of 48 dp on Android and 44 pt on iOS.
- Release size is measured for Android and thinned iOS builds before the first asset set is finalized.
- Formal download and installed-size budgets are set after the first representative build rather than guessed in this document.

The initial compatibility target is Android 8.0/API 26 and iOS 15.0 or newer. Project setup must verify these versions against the selected Kotlin, Compose Multiplatform, Compottie, and persistence releases before implementation begins.

### 17.1 Implementation baseline

- Android application ID and iOS bundle ID: `app.bertsatlas`
- Android compile and target SDK: 36; minimum SDK: 26
- iOS deployment target: 15.0
- Dependencies use the newest mutually compatible stable releases available when V1 implementation begins and are pinned exactly. Preview, snapshot, and dynamic versions are excluded.
- The repository contains `:domain`, `:composeApp`, and `:catalogTool` Gradle modules plus a thin `iosApp` Xcode host.
- A debug-build-only QA panel is opened by long-pressing the Settings version label. It provides deterministic controls for local state, catalog content, timers, phases, date/timezone, and accessibility previews, and is absent from release builds.
- Android physical-device verification targets OnePlus CPH2467 device `663c91b1`. iOS milestone builds target the installed iOS 26.1 simulator, followed by full physical-iPhone acceptance before release.
- Verification is manual and supported by the non-test catalog validator. The project contains no automated test source sets, test dependencies, or test tasks authored for V1.

## 18. Verification and release acceptance

V1 is ready only when:

- A fresh install can start and finish every quiz configuration in airplane mode on Android and iOS.
- First launch shows only the immediate-start experience; later launches show `Start` and `Options`.
- `Start` correctly reuses the most recently completed options configuration.
- Each timed duration ends after the current question, and unlimited mode ends only by player choice.
- Question and session clocks pause in every specified non-answering state.
- Abandoned and completed runs apply mastery, results, personal-best, and streak rules correctly.
- Correct-before-timeout, correct-after-timeout, incorrect, and hinted scoring follow the specified rules.
- Manual scoring checks cover full time, half time, just before zero, exactly zero, overtime, and every hinted-balance case through deterministic debug controls.
- Hints never make the score negative and remain available at zero points.
- Multiple-choice and letter-bank questions are solvable and accessible.
- Wrong and overtime answers always communicate the correct answer.
- Fun facts obey relevance and frequency rules.
- Weak items return according to the review rules without immediate rote repetition.
- Streaks behave correctly across relaunch, midnight, and timezone changes.
- Progress, settings, stamps, and interrupted runs survive relaunch.
- Corrupt local learner state recovers safely.
- Reduce Motion, screen-reader labeling, large text, mute, and haptic settings are verified on both platforms.
- Every bundled animation and asset loads on both platforms without network access.
- Catalog validation passes with no missing, ambiguous, duplicate, or unattributed records.
- The catalog contains usable flag-recognition questions for all 195 countries, a reviewed capital record or documented exception for each country, and at least one sourced durable fact per country.
- Release builds pass for both platforms without executing automated tests.
- Manual platform smoke checks cover launch, persistence, lifecycle interruptions, sound, haptics, and airplane mode.
- The merged Android release manifest contains no internet permission, and runtime inspection records no app-originated network request.
- Store metadata does not claim collection, connectivity, or educational outcomes the app does not have.

## 19. Post-V1 direction

### V2 candidate: guided journey

A guided world journey may organize existing content into regions and progressively harder stops. It should reuse the V1 catalog, mastery, scoring, feedback, and Bert systems rather than creating a separate learning engine.

Other candidates, ordered only after observing V1:

- Territory and dependency bonus packs
- Country outlines, neighbors, currencies, landmarks, and physical geography
- Additional question formats such as ordering and odd-one-out
- Local pass-and-play modes
- Additional languages
- Optional downloadable content, if the offline promise can remain clear

Accounts, online competition, and cloud sync require a separate product and privacy decision and are not assumed future work.

## 20. Research basis

The product direction was informed by:

- BradyYourTutor's short-form Geography + Flag Quiz as evidence for quick, reveal-driven geography trivia pacing: https://www.youtube.com/watch?v=-5DyPN9rynw
- StudyGe's breadth of maps, quiz modes, country data, and progression as both competitive context and a warning against V1 scope growth: https://apps.apple.com/us/app/studyge-world-geography-quiz/id1444539710
- Research supporting retrieval practice and useful feedback: https://link.springer.com/article/10.1007/s10648-021-09595-9
- Research cautioning that points, rankings, and other extrinsic systems do not automatically improve motivation: https://pmc.ncbi.nlm.nih.gov/articles/PMC10448467/
- Kotlin Multiplatform and Compose Multiplatform platform guidance: https://developer.android.com/kotlin/multiplatform and https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html
- Compottie's supported modules and offline resource loading: https://github.com/alexzhirkevich/compottie
- ISO's description of country and geopolitical-area code maintenance: https://www.iso.org/iso-3166-country-codes.html
- Unicode CLDR guidance favoring customary localized territory names: https://cldr.unicode.org/translation/displaynames/countryregion-territory-names
- Natural Earth's public-domain terms for potential geographic datasets: https://www.naturalearthdata.com/about/terms-of-use/

The public name "Bert's Atlas" replaces "Fun with Flags" because a preliminary search found several existing mobile games using the latter name and an existing game-software trademark registration: https://trademarks.justia.com/791/80/fun-with-79180064.html. Legal and store-name clearance for Bert's Atlas is still required before public release.

## 21. Remaining product decisions

These decisions can wait until implementation planning or playtesting:

- Final Bert character design and voice guide
- Exact approved color, type, icon, and motion system
- Final timer and hint-cost tuning
- Final source and license for flag artwork
- Minimum Android and iOS versions
- Formal application-size budget
- Distribution and pricing model
