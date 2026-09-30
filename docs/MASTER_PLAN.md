# ASCEND Master Plan

Version: Architecture Freeze 2.1
Owners: Ryan + Eddy
Target: Android / Google Play
Status: WP00 architecture/security freeze required before Phase 0 implementation


## 0. Architecture freeze — WP00 (mandatory before implementation)

The product plan is now split into canonical contracts. These documents are mandatory implementation constraints, not optional notes:

- [Security Architecture](SECURITY.md)
- [Threat Model](THREAT_MODEL.md)
- [Backend and Authentication Contract](BACKEND_CONTRACT.md)
- [Data Lifecycle](DATA_LIFECYCLE.md)
- [Release, Signing and Update Contract](RELEASE_SIGNING.md)
- [Development Environment](DEVELOPMENT_ENVIRONMENT.md)
- [Scoring Contract](SCORING_CONTRACT.md)
- [Frozen Product Decisions](DECISIONS.md)
- [Ryan/Eddy Workflow](WORKFLOW.md)

### WP00 completion requirements

Before WP01 begins as permanent implementation work:

1. choose and freeze the production Android applicationId
2. define the separate development/debug application identity
3. generate a brand-new ASCEND signing key; do not reuse another app's key
4. create and verify two encrypted/offline signing-key backups
5. verify the Windows/Android/Docker/Supabase development environment
6. freeze local encryption, backup exclusion and session-storage contracts
7. freeze Supabase schemas, grants, RLS ownership rules and negative-test requirements
8. freeze Google Credential Manager -> Supabase Auth flow
9. freeze guest -> account migration and account-switch behavior
10. freeze account/data deletion behavior
11. freeze the AI minimized-input/structured-output contract and server-only secrets rule
12. freeze the pseudonymous opt-in research contribution contract
13. freeze phase-by-phase signed APK update testing
14. review the threat model
15. enable repository governance appropriate for implementation before secrets or code accumulate

WP00 does not require the final facial metric statistics. Those remain versioned product/reference data and may arrive later.

### Security posture

ASCEND must assume the APK can be reverse-engineered and a client can be modified. Therefore:
- client-side checks are never the sole authorization mechanism
- server secrets never enter the APK
- raw face photos remain local in V1 normal operation
- local face assets and sensitive structured state are encrypted at rest
- sensitive app data is excluded from platform backup
- normal cloud sync stores numeric history, not full landmark meshes
- AI receives minimized structured results, never raw face photos
- privileged server operations are authenticated, rate-limited and schema-validated
- historical analyses retain the software/config versions that generated them


## 1. Product thesis

ASCEND is a looksmaxxing-focused facial-analysis app. Its value proposition is not “ask an AI whether I am attractive.” Its value proposition is:

1. standardize the photo-taking process
2. measure facial geometry and supported visual features
3. compare the measurements against a versioned ASCEND reference model
4. classify each metric T1-T5
5. calculate Harmony, Dimorphism, Angularity, Misc and Overall deterministically
6. explain why the result occurred
7. give practical Softmax guidance and optional Hardmax/procedure information within the 13+ product

The score is therefore reproducible relative to ASCEND's configured reference model. AI may explain results, but it does not own the numerical truth of the product.

## 2. Product promise

A successful ASCEND scan should answer:

- What did the app measure?
- Where exactly on my face did that number come from?
- What tier is it?
- What is the reference/ideal region used by ASCEND?
- How much did it contribute to a category?
- What are my Harmony, Dimorphism, Angularity and Misc results?
- What is my Overall result and community rank?
- What can realistically change the appearance of a weaker area?

If ASCEND cannot show the measurement or explain the calculation, it should not confidently score it.

## 3. Non-goals for V1

ASCEND V1 is not:
- a medical diagnostic tool
- a sex/gender detector
- a universal scientific proof of human attractiveness
- a social network
- an iOS product
- a cloud photo-storage service
- an LLM-based face rater
- a fake percentile generator

## 4. Core user journey

### First launch
1. ASCEND branding/onboarding
2. simple self-declared “I am 13 or older” gate
3. choose Male reference model or Female reference model
4. choose intent
   - Softmax
   - Hardmax
5. guest or account
6. privacy/data notices
7. optional pseudonymous derived-data contribution consent
8. capture tutorial

### New scan
1. choose Camera or Gallery
2. front tutorial
3. capture/import front
4. quality validation
5. crop/center if needed
6. profile tutorial
7. capture/import one profile
8. quality validation
9. constrained landmark confirmation only where needed
10. analysis
11. result

### Result
1. Overall /10
2. overall community rank
3. Harmony /10
4. Dimorphism /10
5. Angularity /10
6. Misc /10
7. Strongest measured feature / Largest improvement opportunity when reliable and cross-metric comparable
8. category metric lists
9. per-metric T1-T5
10. overlay/reference/explanation
11. recommendations
12. share
13. history if signed in/allowed by product gate

## 5. Capture specification

### Standardization

The capture tutorial should visibly demonstrate:
- rear camera preferred
- camera at eye level
- neutral facial expression
- remove major occlusions
- even lighting
- roughly two metres of distance
- head level
- front view for the first image
- true side profile for the second

The app should avoid pretending it can measure physical distance perfectly from every phone. Use instructions plus face-size/pose/quality heuristics.

### Live validation

Use minimal status messaging, not clutter over the face:
- Too close / too far
- Too dark
- Too blurry
- Face not centered
- Multiple faces
- Turn slightly
- Hold head level
- Neutral expression

A bad image should fail early instead of generating a polished bad score.

### Profile side

Store which side was used. V1 may accept either left or right if the profile extractor supports it; history comparisons should prefer the same side across scans.

## 6. Computer-vision architecture

### Frontal path

Primary V1 frontal pipeline:
- decode and orient image
- detect exactly one valid face
- run frontal landmark model
- estimate head pose
- normalize roll/scale
- validate yaw/pitch
- transform landmarks into normalized analysis coordinates
- compute geometry
- produce confidence and failure reasons
- render overlay from the same normalized points used in math

MediaPipe Face Landmarker is the initial implementation candidate for frontal landmarks.

### Profile path

Profile is a separate path.

Do not treat a full 90-degree profile as “just another front mesh frame.”

V1 profile strategy:
- profile orientation detection
- dedicated visible-point extraction where reliable
- propose critical landmarks
- show constrained correction/confirmation
- compute profile angles/ratios only after required points are valid
- mark unavailable when anatomy is not visible

The correction control is a validation aid, not a way to redesign the user's face.

## 7. Measurement engine

Keep the measurement engine pure Kotlin wherever possible.

Responsibilities:
- vector/angle math
- 2D normalized distances
- pose-normalized vertical/horizontal projections
- ratio functions
- formula registry
- confidence propagation
- unavailable/failure states

A measurement result should contain:
- metricId
- rawValue
- normalizedValue if different
- confidence
- source landmarks/features
- auto/manual-assisted state
- failure reason if unavailable
- geometryEngineVersion

## 8. Scoring engine

See SCORING_CONTRACT.md for canonical rules.

Key design:
- reference data is configuration
- the enabled production metric set is versioned configuration; no source code assumes 33, 34, 147, or any other fixed metric count
- formula code is not full of magic constants
- tier labels are data
- score curves are data
- weights are data
- ranks are data
- category/scan coverage rules are versioned data
- cross-metric comparability and measurement-uncertainty rules are versioned data
- Strongest/Weakest selection is deterministic and outside AI
- every result stores the versions/config hash used

This lets Ryan/Eddy insert the final reference values later without rewriting UI or geometry.

## 9. Recommended reference-model file layout

reference-models/
- schema.json
- v1/
  - male.json
  - female.json
  - ranks-male.json
  - ranks-female.json
  - recommendations.json
  - content/
    - metrics-en.json

No final ideal/tier numbers should be invented merely to make the app look finished.

## 10. Local data model

Suggested Room entities:

UserSettings
- local id
- age band
- reference model
- intent
- consent flags
- theme/settings
- guest scan consumed

ScanSession
- id
- createdAt
- completedAt
- profileSide
- analysis status
- analysisEngineVersion
- landmarkModelVersion
- referenceModelVersion
- config hash

LocalPhotoAsset
- id
- scanId
- FRONT | PROFILE
- app-private URI/path
- crop metadata
- width/height
- capture/import metadata

LandmarkSet
- scanId
- view
- normalized landmark payload
- confidence summary
- manual-confirmation metadata

MeasurementResult
- scanId
- metricId
- raw value
- tier
- hidden score
- weight used
- confidence
- available flag
- score scale / comparability version where applicable
- measurement uncertainty / meaningful-difference metadata where validated

CategoryResult
- scanId
- category
- score /10
- coverage
- coverage policy version

OverallResult
- scanId
- overall /10
- rank label
- enabled metric IDs/config snapshot reference
- overall coverage state
- strongest metric ID tie set
- weakest metric ID tie set
- extrema selection version
- score scale/comparability version

RecommendationResult
- scanId
- recommendation version
- recommendation IDs
- AI explanation cache where allowed

SyncState
- entity id
- remote revision
- state

## 11. Cloud data model

Supabase/Postgres is the initial backend target. Local development uses the Supabase CLI + Docker-compatible local stack; hosted staging is separated from production. Production readiness requires a deliberate backup/availability decision rather than assuming the free tier is permanent infrastructure.

Suggested tables:

profiles
- user_id
- confirmed_13_plus
- selected_reference_model
- intent
- created_at

analyses
- id
- user_id
- created_at
- profile_side
- analysis_engine_version
- landmark_model_version
- reference_model_version
- scoring_model_version
- config_hash
- enabled metric IDs/config snapshot reference
- coverage policy/version + completed coverage state
- score scale/comparability version
- extrema selection version
- strongest metric ID tie set
- weakest metric ID tie set
- category scores
- overall score
- rank

analysis_measurements
- analysis_id
- metric_id
- raw/normalized value
- tier
- hidden score
- confidence

consents
- user_id
- consent type
- granted/denied
- timestamp
- version

dataset_contributions
- randomized contribution id
- approved derived metric subset only
- reference model
- self-declared 13+ eligibility state/policy version where required for audit
- engine/config versions
- no raw photo

A private linkage table may temporarily map account -> random contribution ID solely to support consent withdrawal/deletion. While that link exists, describe the dataset as pseudonymous rather than truly anonymous.

entitlements
- user_id
- entitlement type
- source
- valid_from/to
- state

No V1 raw face-photo table.

Every user-owned table must use Row Level Security.

## 12. Privacy and security rules

Hard rules:
- raw face photos local by default
- no face photo in analytics
- no face photo in crash logs
- no face photo in AI prompts
- no API/service secrets in the APK
- no test face photos in the public GitHub repo unless they are synthetic or explicitly licensed/consented for public use
- redact measurement payloads from normal logs in release builds
- AES-GCM encrypted app-private face asset storage with keys protected by Android Keystore
- sensitive Room/session state encrypted at rest
- sensitive data excluded from Android backup/device transfer
- user deletion deletes associated local assets
- cloud deletion flow must exist for account data
- pseudonymous dataset contribution is a separate explicit opt-in
- research contribution follows the same self-declared 13+ product gate plus separate explicit consent

## 13. Misc feature strategy

Misc needs its own pipeline because hair/skin/brow/beard features are image properties rather than landmark ratios.

V1 priorities should favor features that can be measured reproducibly:
1. eyebrow density/shape
2. beard coverage where applicable
3. hairline geometry
4. hair-density appearance
5. under-eye contrast
6. skin clarity/evenness
7. lip appearance
8. facial leanness proxy

Each extractor must emit:
- numeric/categorical value
- confidence
- capture-quality dependencies
- failure state

If lighting destroys validity, ask for a better photo.

Do not infer “health indicators” as medical facts. ASCEND can say “visible under-eye darkness” but not “iron deficiency.” It can say “visible skin unevenness” but not diagnose disease.

## 14. Advice architecture

Recommendation engine first; AI second.

Recommendation table concept:
- recommendationId
- trigger metric/feature tags
- minimum deviation/tier
- Softmax/Hardmax
- reference-model applicability
- contraindication/guardrail tags
- title
- structured explanation
- source/notes
- version

Example flow:

measurement deviation
-> recommendation tags
-> intent + applicability filter
-> approved recommendation IDs
-> UI cards
-> optional AI explanation using only those approved cards + scan data

This is much safer and more consistent than asking an LLM to invent individualized procedures. Hardmax/procedure content remains curated and informational; medical or surgical decisions belong with a qualified clinician.

## 15. AI architecture

Create a provider abstraction such as:
- explainAnalysis(report)
- explainMetric(metricResult)
- explainRecommendations(approvedRecommendations, report)
- answerQuestion(question, allowedContext)

Server-side provider adapter:
- authenticates user if needed
- strips disallowed data
- sends structured JSON
- validates structured response
- rate limits
- caches where useful
- records prompt/model version
- fails closed

If AI is down, numerical analysis still works instantly.

Choose the actual runtime AI provider during WP16 based on then-current pricing, limits, privacy/data-use terms, latency and JSON reliability. Do not select a free tier merely because it is free if its data-use terms are inappropriate for face-derived user information. Do not couple core code to one vendor.

## 16. UI/UX direction

Initial language:
- clean white backgrounds
- blue/teal ASCEND accent
- strong dark navy text
- large score hierarchy
- restrained gradients
- thin geometric measurement overlays
- rounded cards
- minimal clutter
- motion only where it teaches/clarifies

Result hierarchy:
1. Overall and rank
2. category scores
3. strongest / weakest areas
4. metrics
5. explanation/advice

Per-metric screen should borrow the useful information architecture seen in the supplied competitor screenshots:
- clear metric name
- user value/tier card
- visual overlay
- ideal/reference position visualization
- about section
- tutorial/how measured
- scoring/reference curve
- previous/next navigation

But ASCEND should not reproduce the competitor's exact graphics, copy, tabs, or proprietary scoring.

## 17. Account/guest design

Guest:
- no account required
- one full scan and one result
- local state records that the guest scan was used
- product must define reasonable reinstall/reset behavior later; do not build invasive device fingerprinting

Account:
- Google sign-in through Android Credential Manager + Supabase Auth in V1
- history
- multi-scan support
- cloud sync of numeric data
- future premium
- future comparisons

Guest -> account must be explicit: after sign-in, offer to save the existing guest result to the account. Never silently upload it. Account switching must never expose another account's local scans.

Do not force login before the user understands the product.

## 18. Premium design

During development:
- DEV_UNLOCK grants full feature access
- beta can remain fully unlocked

Future Free:
- can perform scan
- receives limited overall result
- exact final free-card scope TBD

Future Premium:
- detailed exact ratios
- metric tiers/details
- deeper category analysis
- other advanced features as later decided

Scoring is always computed consistently; entitlement controls visibility/features, not the underlying truth.

## 19. Analytics

Track product behavior, not faces.

Allowed examples:
- onboarding_completed
- capture_started
- capture_failed(reason category)
- analysis_completed
- result_category_opened
- share_card_created
- account_created
- consent_dataset_changed

Do not send:
- raw landmarks
- exact facial measurements
- photos
- free-text AI chat content by default
into general analytics events.

## 20. Testing strategy

### Pure unit tests
- geometry formulas
- scoring interpolation
- tier boundaries
- enabled metric-set membership
- low-confidence/missing metric exclusion
- category/overall coverage and insufficient-reliable-measurements behavior
- cross-metric score-scale eligibility
- strongest/weakest selection and uncertainty ties
- historical selection/version preservation
- weight normalization
- rank mapping
- 13+ onboarding/intent gates
- entitlement visibility

### Golden regression tests
Keep versioned synthetic/consented private fixtures with expected:
- landmark coordinates
- measurement values
- tiers
- category scores
- overall
- rank

### Android tests
- onboarding
- age gate
- camera permission
- capture/retake
- gallery import
- rotation/portrait enforcement
- history
- deletion
- account flows
- premium gates

### Device tests
At minimum test multiple:
- Samsung class device
- Pixel/reference device
- another OEM
- low/mid-range modern device

### Upgrade/migration tests
Every phase that can produce an installable application must be tested as an in-place update over the previous signed QA APK. Do not uninstall between phase gates. Verify Room migrations, settings, encrypted local assets, scan history and app launch after the update.

Automated target: install prior APK -> seed representative local data -> install new APK with update semantics -> assert migration and historical-result readability.

### Reliability tests
Same user, repeated standardized captures:
- metric variance
- category variance
- pose sensitivity
- distance sensitivity
- lighting sensitivity
- profile-side consistency

Define acceptable error per metric before claiming that metric is production-ready.

## 21. Public-repo hygiene

This repo is public. Therefore:
- no real private test faces
- no secrets
- no raw backend dumps
- no proprietary competitor assets
- no copyrighted competitor screenshots committed as app assets
- use synthetic/permissioned fixtures
- local-only private QA fixture path belongs in .gitignore

## 22. CI/CD

CI is also part of the security boundary. Release secrets must never be exposed to untrusted PR code.

Early CI:
- Gradle build
- unit tests
- lint
- formatting/static checks

Later:
- debug APK artifact
- emulator smoke tests
- release candidate build
- signed QA APK/release bundle via protected secrets
- APK/AAB inspection for embedded secrets, debug flags, signing identity and unexpected endpoints
- dependency/security scanning as the project matures
- Play internal/closed track deployment can be added after signing setup

Do not store signing key passwords in the repo.

## 23. Play Store readiness

Before public listing:
- privacy policy URL
- Terms
- accurate Data Safety form
- age/target audience configuration consistent with actual app
- content rating
- clear handling of face photos and derived data
- delete account/data support as applicable
- screenshots
- feature graphic
- icon
- store description
- closed testing requirements satisfied
- physical-device QA

## 24. What must be supplied later by Ryan/Eddy

The architecture deliberately leaves these as product data:
- exact enabled V1 metric set for each scoring/reference model version
- exact T1-T5 ranges for every enabled metric
- metric-specific hidden 0-100 scoring anchors
- cross-metric score calibration/eligibility for Strongest/Weakest
- validated measurement uncertainty / meaningful-difference rules where extrema comparison is enabled
- category/scan coverage thresholds and blocking required metrics
- metric weights
- final category membership
- overall community-rank thresholds
- final Male/Female reference copy
- recommendation catalogue
- final premium visibility rules
- final branding assets

These should be inserted through versioned configuration, not random constants inside screens.

## 25. Definition of V1 done

ASCEND V1 is done only when:

- fresh install onboarding works
- guest can complete one scan
- account flow works
- front + profile capture/import works
- bad captures are rejected cleanly
- front landmarking is reliable
- profile assistance is constrained and usable
- configured metric set is measured
- T1-T5 results are deterministic
- all four category scores are correct
- overall and community rank are correct
- result drill-down shows overlay + explanation/reference
- Misc supported features work or fail honestly
- Softmax works
- the self-declared 13+ gate is consistently enforced by the product flow
- Hardmax/procedure content uses curated approved content and is clearly informational rather than individualized medical advice
- AI explanation cannot change scores
- history works for accounts
- local raw photos stay local
- opt-in dataset flow is explicit
- share card works
- beta entitlement unlock works
- CI is green
- release build is stable
- privacy/store declarations match implementation
- Ryan + Eddy have completed physical-device final QA

## 26. Execution order

Start with WP00 architecture/security freeze from WORKFLOW.md. Only after WP00 is complete does Phase 0 implementation begin.

Do not jump to pretty result screens before:
- core contracts
- geometry
- scoring
- scan lifecycle

Do not implement final scoring constants before Ryan/Eddy supply the actual reference data.

Do not add paid billing before the full product is useful.

Do not add iOS before Android is stable.

The correct first milestone is not “8.3/10 appears on screen.” The correct first milestone is “the same standardized scan produces explainable, repeatable numbers through a tested pipeline, and refuses to overstate results when coverage, confidence, comparability, or measurement uncertainty does not support them.”
