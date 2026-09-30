# Ryan + Eddy Development Workflow

ASCEND is intentionally organized for parallel work with mandatory synchronization gates.

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

Shared goal: a clean Android project, architecture, CI, baseline navigation, and stable core data contracts.

Ryan — WP01 Android Foundation
- Kotlin Android project
- Jetpack Compose + Material 3
- portrait lock
- dependency injection
- navigation shell
- light ASCEND theme
- baseline app icon/branding placeholder
- feature flag framework
- local debug menu

Eddy — WP02 Core Contracts + CI
- core model types
- ScanSession / ReferenceModel / AgeBand / IntentMode
- metric-result/category-result/overall-result contracts
- analysis version structure
- CI for build, unit tests, lint
- secrets/config strategy
- no-secret sample environment docs

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
- implement a first representative subset of front/profile formulas
- pure-Kotlin unit tests
- synthetic fixtures

Eddy — WP04 Reference Config + Scoring Engine
- JSON/config schema for Male/Female reference models
- T1-T5 evaluator
- hidden 0-100 interpolation
- metric weighting
- category /10 calculation
- equal 25% overall calculation
- rank mapper with TBD thresholds disabled until supplied
- config validation + hash/versioning

Sync Gate P1:
- deterministic fixture flows geometry -> metric values -> tiers -> categories -> overall
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
- app-private photo storage
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
- profile overlay/angle rendering
- prevent anatomically absurd movement

Eddy — WP10 Profile Extractors
- profile landmark adapter
- profile formula implementations
- required-point confidence model
- manual-confirmation requirements
- regression fixtures for profile metrics
- soft-tissue proxy labeling where appropriate

Sync Gate P4:
- one side profile can be completed without freehand “score editing”
- every profile metric clearly knows whether it is auto, assisted, or unavailable

### Phase 5 — Full result experience

Shared goal: ASCEND starts to look like the product rather than a tech demo.

Ryan — WP11 Results Dashboard
- Overall /10
- community rank
- four category /10 cards
- premium-lock placeholders/feature flags
- metric navigation by category
- share entry point
- original ASCEND white/blue visual system

Eddy — WP12 Metric Detail Experience
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

Sync Gate P5:
- full scan can be explored from Overall down to a metric explanation
- score shown in UI matches engine fixtures exactly

### Phase 6 — Misc visual features

Shared goal: implement non-ratio appearance features without turning the system into an opaque AI judge.

Ryan — WP13 Visual Feature Extraction
- standardized ROI extraction
- initial hairline/hair-density appearance
- brow density/shape
- beard coverage
- under-eye/lip/facial-leanness feature hooks
- skin clarity/evenness under controlled capture
- confidence outputs
- device/lighting sensitivity tests

Eddy — WP14 Misc Scoring + UX
- Misc feature schema
- visual-feature hidden 0-100 -> T1-T5 where applicable
- category weighting
- “cannot assess reliably” behavior
- separate explanation content
- fairness checks preventing skin-color ranking
- no health-diagnosis wording

Sync Gate P6:
- Misc is deterministic/versioned for the selected supported features
- unreliable lighting or confidence produces retry/unavailable, not fabricated certainty

### Phase 7 — Advice and AI explanation

Shared goal: useful recommendations without letting AI control scores.

Ryan — WP15 Recommendation Engine
- curated recommendation database
- map metric/feature deviations -> recommendation tags
- Softmax rules
- Hardmax 18+ rules
- minor-safe filtering
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
- under-18 Hardmax remains impossible in both client and server paths

### Phase 8 — Accounts, sync, history, consent, sharing

Shared goal: turn a local scanner into a real product while preserving local-first privacy.

Ryan — WP17 History + Share
- scan history
- reopen old results
- date/model/version display
- share card generation
- privacy-safe export
- foundation for later Scan A vs Scan B
- guest one-scan limit

Eddy — WP18 Auth + Supabase Sync
- optional account creation/login
- sync scan metadata/results/normalized landmarks
- no raw-photo upload
- RLS policies
- consent records
- explicit anonymous-dataset opt-in
- adult-only dataset contribution in V1
- sync conflict strategy

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
- CameraX
- MediaPipe Face Landmarker for frontal landmarking
- Room
- DataStore
- Coroutines / Flow
- Hilt or equivalent DI
- Supabase for auth/Postgres/Edge Functions/sync
- Firebase Crashlytics + lightweight analytics if used, with strict event redaction
- GitHub Actions
- JUnit + Android instrumentation/Compose UI tests

External service SDKs must sit behind repository/provider interfaces. Free-tier pricing/limits change; ASCEND must not be architecturally trapped by one vendor.

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
