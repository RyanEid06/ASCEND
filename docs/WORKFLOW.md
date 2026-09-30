# Ryan + Eddy Development Workflow

ASCEND is intentionally organized for parallel work with mandatory synchronization gates. WP00 is a one-time architecture/security freeze that must complete before Phase 0 implementation.


## WP00 — Architecture, security and release freeze

WP00 is planning/setup work, not a facial-scoring implementation phase. It exists to prevent expensive security, signing, authentication and data-lifecycle rewrites later.

Mandatory WP00 workstreams:

### WP00A — Repository + governance
- decide whether the implementation repository remains public or becomes private
- protect main against force-push/deletion
- require PR + required CI for implementation merges where repository settings permit
- define emergency bypass ownership
- establish dependency/security scanning plan
- never store private face fixtures or secrets in Git

### WP00B — Android identity + signing
- freeze production applicationId
- create separate dev/debug identity
- generate ASCEND-only signing key
- make two encrypted/offline backups
- document certificate fingerprints
- establish monotonic versionCode policy
- establish signed QA APK update chain
- plan Play App Signing + separate upload key

### WP00C — Local privacy/security
- AES-GCM encrypted app-private face assets
- Android Keystore-protected keys
- explicit encryption envelope: formatVersion + keyVersion + nonce + ciphertext/authentication tag metadata
- random DB/session passphrases wrapped/protected by Keystore keys; define rotation, missing-key and deletion semantics
- prove a compatible modern Room + supported SQLCipher-for-Android integration; do not use deprecated `android-database-sqlcipher`
- prove encrypted DB create/reopen/migrate/process-death behavior and native 16 KB page-size compatibility before WP06
- encrypted sensitive Room/session storage
- Android backup/device-transfer exclusions
- Photo Picker instead of broad gallery permissions
- EXIF stripping and hostile image validation
- sensitive-screen/share behavior
- deletion/cleanup semantics

### WP00D — Backend/auth
- local/staging/production environment contract
- Google Credential Manager -> Supabase Auth
- guest/account ownership model
- guest -> account explicit migration
- account switching
- schema/grants/RLS
- negative authorization tests
- server-issued monotonic sync revisions + tombstones; deletion wins over stale updates
- idempotency-key contract for durable/retryable mutations
- WorkManager durable-job contract for sync/delete/research operations
- account deletion

### WP00E — Threat model
Review THREAT_MODEL.md and confirm mitigations for:
- APK reverse engineering
- modified client
- stolen JWT/session
- broken RLS/IDOR
- database-function privilege escalation
- malicious media
- AI/API abuse
- CI/signing compromise
- database loss
- free-tier outage/quota exhaustion
- sync resurrection
- research re-identification
- dependency compromise

### WP00F — AI/service boundary
- backend-only provider credentials
- minimized structured AI input
- strict response schema
- approved recommendation allowlist
- server age/entitlement enforcement
- rate limits + spending circuit breaker
- kill switches
- future Play Integrity seam

### WP00 sync gate
Do not begin permanent WP01 implementation until:
- canonical Architecture Freeze 2.2 documents are merged to main
- `main` is actually protected by repository rules/branch protection against direct force-push/deletion and implementation merges require PR + CI when CI exists
- final applicationId is recorded
- signing/update chain is ready
- development environment checklist passes
- backend/auth/data contracts are frozen enough for Phase 0
- threat model has no unresolved architecture blocker

Final metric ranges, weights, anchors and rank thresholds are NOT a WP00 blocker.


## Branch model

Protected baseline:
- main

For every phase:
- phase/PXX-integration
- ryan/PXX-WPYY-short-name
- eddy/PXX-WPZZ-short-name

Flow:
1. main must be green.
2. Create phase/PXX-integration from main.
3. Freeze the shared phase contract.
4. Ryan and Eddy branch from that exact integration commit.
5. Work in parallel.
6. Each lane opens a PR into the phase integration branch.
7. Run lane tests + shared integration tests.
8. Perform the human sync checkpoint.
9. Fix integration defects.
10. PR phase/PXX-integration -> main.
11. Only after main is green may the next phase begin.

No “I already started the next WP” while the sync gate is unresolved.

## Shared-contract rule

Once a phase begins, these are treated as shared API contracts:
- data models
- database schema touched by both lanes
- scoring config schema
- repository interfaces
- navigation routes shared across features
- analytics event names
- public interfaces between geometry, scoring, storage, and UI

A lane may not casually change them. If a shared contract must change:
1. stop both lanes
2. document the change
3. update both branches
4. resume

This prevents parallel work from becoming merge-conflict roulette.

## Ownership philosophy

Do not permanently split “Ryan = frontend, Eddy = backend.”

Each phase has complementary ownership chosen to minimize conflicts. Both developers must understand the entire pipeline.

## Commit/PR standards

- Small, descriptive commits.
- No raw user face images in Git.
- No API keys or service secrets in Git.
- Every WP PR includes:
  - scope completed
  - tests run
  - known limitations
  - screenshots/video when UI changed
  - migrations/config changes
  - contract changes
- Shared tests must pass before merge.
- Prefer squash merge for individual WP PRs into phase integration, then a clean phase merge to main.

## Sync checkpoint checklist

At each phase boundary:
- both lanes merged into phase integration
- app builds from clean checkout
- unit tests green
- lint/static checks green
- instrumentation/smoke tests for touched flows green
- no duplicated implementation ownership
- no stale TODO that blocks the next phase
- migrations apply cleanly
- metric/reference config loads
- no raw face data leaked to logs/analytics
- docs updated
- both Ryan and Eddy manually exercise the integrated feature
- build the signed QA APK from integrated/main baseline when an APK exists
- if native libraries exist, verify 16 KB page-size compatibility/alignment
- install it over the previous signed QA APK without uninstalling
- verify migrations/settings/history/encrypted assets survive the update
- tag or record the phase baseline commit

## Recommended repo structure

- app/
- core/
  - model/
  - geometry/
  - scoring/
  - vision/
  - data/
  - ui/
- feature/
  - onboarding/
  - capture/
  - analysis/
  - results/
  - history/
  - account/
  - advice/
- reference-models/
- docs/
- test-fixtures/

Keep geometry/scoring as pure Kotlin wherever possible so it can be unit-tested without Android.

## Phase plan

### Phase 0 — Foundation and contracts

Prerequisite: WP00 complete.

Shared goal: a clean Android project, architecture, CI, baseline navigation, and stable core data contracts.

Ryan — WP01 Android Foundation
- Kotlin Android project
- production + dev application identity from WP00
- minSdk 26; target current Play-required API (API 36 at architecture freeze, re-check at implementation/release)
- Jetpack Compose + Material 3
- portrait-first **adaptive/resizable** layouts; compact/medium/expanded window handling and state preservation
- no correctness dependency on manifest portrait locking
- Navigation 3 using the current stable release at implementation time
- UDF screen architecture: Compose -> ViewModel/StateFlow -> repositories/use-cases where justified
- dependency injection
- light ASCEND theme
- baseline app icon/branding placeholder
- feature flag framework
- local debug menu
- keep modules intentionally small: `app`, `core:model`, `core:geometry`, `core:scoring`, and `core:data` only when needed; feature packages stay packages until module boundaries earn their cost

Eddy — WP02 Core Contracts + CI
- core model types
- ScanSession / ReferenceModel / AgeConfirmation / IntentMode
- metric-result/category-result/overall-result contracts
- analysis version structure
- CI for build, unit tests, lint
- dependency verification/locking baseline
- native-library inventory + automated 16 KB compatibility/alignment gate once any native dependency appears
- secrets/config strategy
- no-secret sample environment docs
- consume SECURITY/BACKEND/DATA lifecycle contracts rather than inventing new security rules

Sync Gate P0:
- clean clone builds
- CI green
- both lanes compile against same core contracts

### Phase 1 — Deterministic geometry and scoring foundation

Shared goal: prove the math engine independently of camera/ML.

Ryan — WP03 Geometry Engine
- point/vector/angle utilities
- pose-normalized coordinate helpers
- formula registry
- metric feasibility matrix for every candidate: formula/extractor, view, automatic/assisted/manual/unsupported, required landmarks, resolution/pose/lighting dependencies, calibration requirement, confidence and reliability status
- implement only a first representative subset of technically feasible front/profile formulas
- pure-Kotlin unit tests
- synthetic fixtures

Eddy — WP04 Reference Config + Scoring Engine
- consume `reference-models/schema.json`; research CSVs are not runtime config
- validate that draft/invalid runtime models fail closed
- explicit versioned enabled metric IDs; no hard-coded 33/34/147 count
- enabled metrics must resolve to technically supported WP03 formula/extractor entries
- explicit benchmark-purpose, definition-compatibility and age-applicability review before any research evidence becomes a runtime constant
- T1-T5 evaluator
- hidden 0-100 interpolation
- metric weighting
- category /10 calculation
- initial configurable 25% category weights
- category/scan coverage policy + insufficient-reliable-measurements state
- cross-metric score-scale/comparability contract
- measurement-uncertainty / meaningful-difference hooks
- deterministic strongest/weakest extrema selector with tie sets
- rank mapper with TBD thresholds disabled until supplied
- config validation + hash/versioning
- no production `male.json`/`female.json` assets until scoring constants are explicitly reviewed/frozen

Sync Gate P1:
- deterministic fixture flows geometry -> metric values -> tiers -> categories -> overall
- enabled metric membership is config-driven
- low-confidence/missing metrics obey coverage rules and can fail honestly
- strongest/weakest selection only uses explicitly comparable reliable metrics
- uncertainty ties are deterministic
- no UI-specific scoring logic
- every boundary covered by tests

### Phase 2 — Local scan lifecycle and capture UX

Shared goal: a user can create a scan, capture/import both required images, retake one side, and persist locally.

Ryan — WP05 Capture Experience
- animated pre-capture tutorial
- CameraX rear-camera flow
- front capture
- profile capture
- gallery import
- crop/center screen
- timer/retake flow
- non-obstructive guidance UI

Eddy — WP06 Local Data + Quality Validation
- Room schema
- encrypted app-private standardized photo storage
- supported SQLCipher-for-Android integration proven in WP00; never the deprecated library
- Keystore-backed encryption envelope/key versioning from WP00
- backup exclusions
- scan lifecycle/state machine
- blur/brightness/resolution checks
- face count and basic pose/centering validation hooks
- retake reason model
- delete scan/delete all
- migration tests

Sync Gate P2:
- one complete local scan works offline
- restarting app preserves it
- failed front/profile can be retaken independently
- no media is uploaded

### Phase 3 — Frontal computer vision

Shared goal: reliable automatic frontal landmarks and initial real measurements.

Ryan — WP07 Front Landmark Pipeline
- integrate MediaPipe Face Landmarker
- detect/store landmark set
- pose normalization
- confidence/quality output
- front overlay renderer
- extraction adapter into geometry engine

Eddy — WP08 Front Measurement Set + Validation Harness
- implement/finalize front metric mappings
- human-annotation fixture format
- measurement debug screen
- expected-value/tolerance regression tests
- low-confidence rules
- manual-correction bounds for eligible front landmarks

Sync Gate P3:
- real frontal photo -> stable landmarks -> visible overlays -> deterministic metrics
- low-confidence cases fail cleanly rather than guessing

### Phase 4 — Profile analysis and constrained correction

Shared goal: make the profile path accurate enough for V1 without pretending a frontal mesh solves 90-degree anatomy.

Ryan — WP09 Profile Capture/Assist UI
- detect profile orientation
- profile-side metadata
- guided key-point confirmation
- constrained draggable correction zones
- usable assisted fallback even when automatic profile extraction cannot provide a trusted point
- profile overlay/angle rendering
- prevent anatomically absurd movement

Eddy — WP10 Profile Extractors
- profile landmark adapter where reliable; automatic extraction is an optimization, not a release dependency
- profile formula implementations
- required-point confidence model
- manual-confirmation requirements
- regression fixtures for profile metrics
- soft-tissue proxy labeling where appropriate

Sync Gate P4:
- one side profile can be completed through automatic proposal **or the constrained assisted fallback** without freehand “score editing”
- every profile metric clearly knows whether it is auto, assisted, or unavailable

### Phase 5 — Misc / Appearance Details foundation

Shared goal: finish every category required by Overall before building the final result experience.

Ryan — WP11 Visual Feature Extraction
- standardized ROI extraction
- initial hairline/hair-density appearance
- brow density/shape
- beard coverage
- under-eye/lip/facial-leanness feature hooks
- skin clarity/evenness under controlled capture
- confidence outputs
- device/lighting sensitivity tests

Eddy — WP12 Misc Scoring + UX Contract
- internal category remains MISC; normal user-facing label is **Appearance Details**
- visual-feature hidden 0-100 -> T1-T5 where applicable
- category weighting
- “cannot assess reliably” behavior
- separate explanation content
- fairness checks preventing skin-color ranking
- no health-diagnosis wording

Sync Gate P5:
- Misc/Appearance Details is deterministic/versioned for the selected supported features
- unreliable lighting or confidence produces retry/unavailable, not fabricated certainty
- all four categories required by Overall now have an implementable data path

### Phase 6 — Full result experience

Shared goal: expose the completed four-category engine through a fast progressive-disclosure phone UX.

Ryan — WP13 Results Dashboard
- Overall /10 + community rank
- Strongest measured feature / Largest improvement opportunity with tie/insufficient-data states
- four category cards, including user-facing Appearance Details
- small relevant-insight section
- **See all measurements**
- save/share entry points
- responsive compact/medium/expanded Compose layout
- original ASCEND white/blue visual system

Eddy — WP14 Metric Detail Experience
- category metric list
- user value + T1-T5
- reference/ideal range
- overlay image
- scoring curve visualization
- About this metric
- How it is measured/tutorial
- reference/ideal visualization
- prev/next navigation
- content models so copy is data-driven

Competitive benchmark:
FaceIQ-style strengths observed in supplied screenshots include a prominent result/value card, visual measurement overlay, ideal-region curve, explainer card, tutorial entry, and fast previous/next metric navigation. ASCEND should retain those useful information-design ideas without cloning its exact visuals/text.

Sync Gate P6:
- full scan can be explored from Overall down to a metric explanation
- score shown in UI matches engine fixtures exactly
- no completed Overall is shown if required category/coverage rules fail
- rotation/window resizing preserves the result/navigation state

### Phase 7 — Advice and AI explanation

Shared goal: useful recommendations without letting AI control scores.

Ryan — WP15 Recommendation Engine
- ask Softmax / Hardmax / Both intent here on first Advice entry, not during onboarding; persist the preference
- curated recommendation database
- map metric/feature deviations -> recommendation tags
- Softmax rules
- Hardmax/procedure rules for the same self-declared 13+ product
- informational/educational procedure-content guardrails
- structured advice cards
- no AI required for core recommendation selection

Eddy — WP16 AI Explanation Layer
- InsightProvider abstraction
- backend proxy/Edge Function
- no provider key in APK
- input = structured scan/result/advice data
- output schema validation
- prompt/version tracking
- provider fallback/offline behavior
- prevent AI from changing numerical results or inventing unsupported procedures

Sync Gate P7:
- app remains fully functional if AI is unavailable
- AI only explains approved facts/recommendations
- Hardmax/procedure content follows the same simple self-declared 13+ gate and remains curated/informational

### Phase 8 — Accounts, sync, history, consent, sharing

Shared goal: turn a local scanner into a real product while preserving local-first privacy.

Ryan — WP17 History + Share
- scan history
- reopen old results
- date/model/version display
- share card generation
- privacy-safe export
- foundation for later Scan A vs Scan B
- guest one-scan limit is best-effort per installation; no invasive device fingerprinting

Eddy — WP18 Auth + Supabase Sync
- optional account creation/login offered after first result / Save my results
- sync scan metadata/results only by default; no full landmark mesh
- no raw-photo upload
- explicit grants + RLS policies + negative cross-user tests
- consent records
- explicit pseudonymous dataset opt-in
- private revocation/deletion linkage
- research contribution eligibility follows the same self-declared 13+ product gate plus separate explicit consent
- server-issued monotonic revision + tombstone conflict strategy; deletion wins
- unique WorkManager CoroutineWorkers for durable sync/delete/research operations with stable work names and idempotency keys

Sync Gate P8:
- guest works offline
- account restores synced numeric history
- raw images remain local
- turning dataset contribution off stops future contributions

### Phase 9 — Premium architecture and remote configuration

Shared goal: prepare monetization without charging during development.

Ryan — WP19 Entitlement-aware UX
- FREE / PREMIUM / DEV_UNLOCK state
- result-detail gating
- premium upsell placeholders
- free limited-result UX
- no ads
- debug override for testers

Eddy — WP20 Entitlement Backend + Remote Config
- entitlement repository contract
- Supabase entitlement table
- future Google Play Billing verification seam
- remote feature flags
- reference-config delivery/cache strategy
- safe offline entitlement cache

Sync Gate P9:
- all beta testers can remain unlocked
- switching an entitlement changes visibility without changing scoring
- no billing code is required yet to continue

### Phase 10 — Validation, hardening, and Play Store readiness

Shared goal: prove reliability, privacy, performance, and release quality.

Ryan — WP21 Device/UI QA
- modern Android device matrix
- compact/medium/expanded windows, landscape, tablet/foldable, multi-window/desktop resizing and state preservation
- camera orientation/device quirks
- accessibility
- touch targets/text scaling
- capture retry UX
- performance/memory
- offline behavior
- visual polish
- light mode finalization

Eddy — WP22 Engine/Backend QA
- formula and tier-boundary audit
- fixed-landmark geometry/scoring exact determinism tests
- same-image vision reproducibility tests with validated tolerances across supported device/delegate classes
- enabled-metric-set/config audit
- coverage and insufficient-reliable-measurements audit
- strongest/weakest comparability, uncertainty, tie and historical-version audit
- repeated-capture reliability tests
- profile correction abuse tests
- DB migration tests
- RLS/security tests
- AI failure/timeout tests
- analytics/log redaction
- crash monitoring
- release configuration

Final Sync Gate P10:
- release candidate built from main
- deterministic scoring audit passed
- no raw face images in backend/logs/analytics
- consent/age gates verified
- signed release build
- Play Store Data Safety/privacy documentation matches reality
- closed-test physical-device pass
- store listing/screenshots/branding ready

## Post-V1 backlog

Do not block first public release on:
- Scan A vs Scan B deep comparison
- validated percentiles
- dark mode
- iOS
- social feed
- cloud photo backup
- fully automatic 90-degree profile anatomy
- extra profile side
- advanced AI chat
- public community rankings
- large-scale reference-dataset research

## Initial technology baseline

- Kotlin
- Jetpack Compose / Material 3
- Navigation 3 current stable release
- UDF + ViewModel/StateFlow for screen state
- CameraX
- MediaPipe Face Landmarker for frontal landmarking
- Room + **supported** SQLCipher-for-Android integration proven by WP00 compatibility spike; never deprecated `android-database-sqlcipher`
- DataStore for non-secret settings
- Android Keystore for face/session encryption keys
- Coroutines / Flow
- WorkManager / CoroutineWorker for persistent retryable sync/delete work
- Hilt or equivalent DI
- Supabase for Auth/Postgres/Edge Functions/sync; local CLI/Docker stack for development
- Firebase Crashlytics + lightweight analytics if used, with strict event redaction
- GitHub Actions
- JUnit + Android instrumentation/Compose UI tests

External service SDKs must sit behind repository/provider interfaces. Free-tier pricing/limits change; ASCEND must not be architecturally trapped by one vendor.

Do not over-modularize a two-developer app. Begin with a small set of core modules only; split feature Gradle modules only when build performance, independent ownership, or dependency boundaries justify the maintenance cost.

## Phase release/update policy

At every phase that produces an installable app:
1. merge both lanes to phase integration
2. green shared CI
3. merge phase to main
4. increment versionCode
5. generate signed QA APK using the permanent ASCEND signing identity
6. install over the prior QA APK without uninstalling
7. perform the phase-appropriate physical-device smoke/visual test
8. fix migration/update regressions before starting the next phase

Fresh-install testing still exists, but it never substitutes for update testing.

## Release philosophy

“Feature complete” is not “measurement trustworthy.”

A public release requires:
1. working UX
2. deterministic engine
3. measurement validation
4. stable repeated captures
5. privacy/security validation
6. physical-device testing
7. clean failure modes when a metric cannot be measured

If any of those are missing, the score may look polished while being garbage. The validation gates are therefore part of the product, not optional cleanup.
