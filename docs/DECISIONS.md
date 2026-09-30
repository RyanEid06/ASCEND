# ASCEND Frozen Product Decisions

Version: Architecture Freeze 2.2
Status: baseline product decisions for implementation. A change to one of these items should be deliberate and documented.

## Identity and positioning

- Name: ASCEND.
- Android first; Google Play is the first public-store target.
- Production Android applicationId: `app.ascend.mobile`.
- Development/debug applicationId: `app.ascend.mobile.dev`.
- English first.
- Phone UX is portrait-first, but the app architecture is adaptive/resizable. Large screens, foldables, landscape, multi-window and desktop windowing must remain usable; do not make correctness depend on a portrait lock.
- Initial visual direction: light, premium, clean white/blue/teal interface.
- The current logo direction is an A / upward-ascent mark. Branding can be refined without blocking architecture.
- FaceIQ Labs is a competitive UX reference, not a template to copy. ASCEND should learn from useful patterns such as measurement overlays, per-ratio pages, scoring curves, tutorials, and prev/next navigation while using original UI, wording, assets, and logic.

## Core scan

- One frontal image + one side-profile image.
- Camera capture and gallery import are supported.
- Guided capture recommends the rear camera, eye-level framing, neutral expression, good lighting, and roughly two metres of distance.
- Pre-capture tutorial uses a simple animated dummy/person example.
- Live validation is allowed if it stays clean and non-obstructive.
- If one view is bad, only that view is retaken.
- Crop/center assistance is allowed.
- Landmark correction is constrained: the app proposes an anatomically plausible point/path and the user can make limited correction inside a valid region. Users must not be able to drag landmarks arbitrarily to fake ratios.
- Profile V1 has a guaranteed assisted fallback: automatic extraction may propose visible points, but critical profile points can require constrained user confirmation/correction before geometry is accepted. No profile metric may depend on an automatic extractor that has no validated fallback.

## Scoring model

- User selects the scoring reference model rather than the app inferring sex/gender.
- Two initial comparison models: **Male reference model** and **Female reference model**.
- These labels describe ASCEND scoring/reference configurations. They are not an inferred sex/gender result, identity classification, or biological diagnosis.
- Per-metric UI: T1, T2, T3, T4, T5 only.
- T1 is best/ideal; T5 is worst/farthest from target.
- Internally, metrics may use a 0-100 precision score so two values inside the same tier can contribute differently.
- Individual 0-100 scores are hidden from users.
- Category outputs are /10:
  - Harmony
  - Dimorphism
  - Angularity
  - Misc
- Initial overall weighting is 25% each category.
- Equal category weighting is an initial product configuration, not a claim that research has proven the four categories are equally predictive or equally important. Category weights remain versioned configuration and require validation before a production scoring-model freeze.
- Overall is /10 and may be displayed with up to two decimals.
- Penalties come from distance from the configured ideal/T1 target; there is no special arbitrary “T5 catastrophe” multiplier.
- Metric weights exist inside categories and are configurable data.
- Missing/unreliable required measurements should trigger a retake or be marked unavailable; the app must not invent values.
- The runtime metric set is defined by versioned configuration (`enabledMetricIds` or equivalent), never by a hard-coded count. The current 34 geometry measurements are candidates, not an automatic V1 enabled set.
- `reference-data/` is research inventory only. Production scoring may load only a validated model from `reference-models/`; it must never fall back to research/community rows when runtime configuration is missing.
- Research benchmarks carry an explicit purpose (for example aesthetic preference, population norm, community convention, competitor reference, or measurement validation), definition compatibility, age applicability, and runtime-scoring eligibility. Population averages are not automatically attractiveness ideals.
- Every completed category/result must satisfy configured coverage rules. If reliable coverage is insufficient, publish an explicit **Not enough reliable measurements** state rather than a partial score presented as complete.
- Metric-specific hidden 0-100 scores may be compared across metrics for Strongest/Weakest selection only when those metrics are explicitly calibrated to the same cross-metric score semantics/version. Deterministic scores alone do not make unlike metric curves comparable.

## Strongest / largest improvement opportunity

- A completed scan may show **Strongest measured feature** and **Largest improvement opportunity**.
- Selection is deterministic and uses only enabled, applicable, available, sufficiently confident metrics that are explicitly eligible for cross-metric comparison under the active scoring model.
- Raw ratios, angles, T1-T5 labels alone, or uncalibrated metric-specific scores must not be used to rank metrics against each other.
- Metrics whose score difference is smaller than their configured/validated measurement uncertainty are treated as effectively tied. The result stores the tied metric IDs deterministically rather than pretending one is clearly better/worse.
- If coverage or cross-metric comparability is insufficient, omit the extrema labels and explain that there are not enough reliable/comparable measurements.
- AI may explain the selected metrics but cannot choose, replace, reorder, or invent them.
- Historical completed results store the enabled metric set, selected extrema metric IDs/tie sets, score-scale/config versions, coverage state, and selection-algorithm version so later model updates cannot silently change an old result.

## Community overall ranks

The rank vocabulary is community terminology, not a scientific diagnosis or statement of identity. Rank thresholds remain versioned scoring configuration.

Male final-rank vocabulary:
- Sub 5
- LTN
- MTN
- HTN
- Chadlite
- Chad
- True Adam

Female final-rank vocabulary:
- Sub 5
- LTB
- MTB
- HTB
- Stacylite
- Stacy
- True Eve

Rank thresholds are TBD and must be stored as configuration, not scattered through UI code.

## Measurements and reference content

- Detailed metric ranges, T1-T5 boundaries, internal scoring anchors, and weights will be provided/researched later by Ryan/Eddy.
- Each metric page should ultimately support:
  - user value
  - T1-T5 tier
  - ideal/reference region
  - visual overlay on the photo
  - “about this metric”
  - “how it is measured”
  - reference/ideal visualization
  - optional scoring curve visualization
- Geometry is ratios/degrees unless a measurement has a validated physical scale.
- The app must version the analysis implementation even if the product team considers the ideal ranges fixed.

## Misc category

Misc is intentionally retained as the internal/community category name. Normal user-facing copy should prefer **Appearance Details** (with “Misc” as secondary/context copy where useful) so the category is understandable without community jargon.

Candidate V1 visual-feature inputs include:
- hairline
- hair density/quality appearance
- eyebrow thickness/shape
- eyelash visibility
- skin clarity
- skin evenness
- beard density/coverage
- under-eye appearance
- lip appearance
- facial leanness

These are not treated as ordinary geometric ratios. They require a separate deterministic/on-device visual-feature pipeline where possible.

ASCEND must not reward one race-associated skin color over another. Skin-related scoring should focus on visible qualities such as clarity/evenness under standardized capture, not “lighter/darker is better.”

ASCEND must not diagnose disease, hormone levels, vitamin deficiency, or other medical conditions from appearance.

## AI

- AI does not decide the facial geometry score.
- AI does not replace the deterministic measurement engine.
- V1 AI may:
  - explain results
  - answer questions from structured scan data
  - help present curated recommendations
- V1 should not send raw face photos to a general-purpose cloud LLM.
- AI receives structured measurements/results and allowed recommendation data.
- Provider access must sit behind an interface so a free/paid model can be swapped later.

## Softmax / Hardmax

- Softmax can include grooming, hairstyle, skincare/presentation, sleep, fitness/presentation, and similar non-procedure guidance.
- Do not ask Softmax/Hardmax intent during first-run onboarding. Ask when the user first opens Advice/Recommendations, then remember the preference.
- Hardmax/procedure content is available within the same 13+ product experience.
- Hardmax is based on a curated recommendation database; AI may explain approved content but must not invent treatment, diagnosis, dosage, or unsupported procedures.
- Procedure content is informational and educational, not individualized medical advice. The app must clearly encourage consultation with a qualified clinician for decisions involving medical or surgical care.

## Age and onboarding

- Product is 13+ for the full V1 feature set.
- V1 uses a simple self-declared **I am 13 or older** confirmation. No DOB collection, ID verification, or additional age-assurance system is required by the product architecture.
- This confirmation is a product gate, not proof of a user's true age.
- First-run path is deliberately short:
  - 13+ confirmation
  - selected Male/Female reference model
  - concise essential privacy/data notice
  - capture guidance when the user starts the scan
- Guest is the automatic default. Sign-in, research contribution consent, and advice intent are deferred until the user reaches the feature that needs them.

## Guest and account behavior

- Guest:
  - one scan
  - one result
  - limit is best-effort per installation; clearing app data/reinstall may reset it and V1 must not use invasive device fingerprinting to prevent this
- Account:
  - Google sign-in through Android Credential Manager + Supabase Auth in V1
  - multiple scans
  - saved history
  - result restoration/sync of allowed numeric data
  - future premium entitlement support
- Guest -> account migration is explicit: after the user sees value, offer **Save my results** / Google sign-in and then offer to save the guest result; never silently upload it.
- Local data is ownership-scoped so account switching cannot expose another account's scans.

## Local-first data

- Raw face photos remain on-device by default and are stored as encrypted app-private analysis assets.
- Do not upload raw scan photos to the backend in V1.
- Account sync may contain:
  - scan metadata
  - numeric measurement results
  - category/overall scores
  - model/scoring version
- Full landmark meshes are not part of normal V1 cloud sync. A later derived-geometry sync feature requires a privacy/threat review first.
- Local history stores prior scans so users can reopen old ratings/results.
- Imported/captured photos are normalized, metadata-stripped, encrypted, and stored in app-private storage rather than dumped into the public gallery by default.
- Sensitive local data is excluded from Android Auto Backup/device transfer in V1.
- Delete Scan / Delete All must remove associated local assets.

## Pseudonymous dataset contribution

- Explicit one-time opt-in is required.
- Research contribution consent is not part of the mandatory first-run path. Offer it after the first completed result or from Settings/Research, where the user can understand what they are contributing.
- “Not now” must remain a valid path.
- V1 dataset contribution should use only an approved derived numeric/geometry subset, not raw photos.
- V1 research/reference contribution eligibility follows the same self-declared 13+ product gate, subject to the published consent flow and any release-time legal/store requirements that the team must satisfy.
- Consent state is auditable and revocable. A private account-to-random-contribution linkage may exist solely so withdrawal/deletion can be honored; therefore V1 contribution data is described as pseudonymous rather than truly anonymous while that linkage exists.
- This consent is separate from Terms acceptance.

## Result UX

- The phone result screen uses progressive disclosure rather than dumping every metric at once.
- Default hierarchy: Overall + community rank -> Strongest measured feature / Largest improvement opportunity -> four category cards -> a small set of relevant insights -> **See all measurements**.
- Category screens reveal metric lists; metric detail screens carry the technical depth (measured value, tier, overlay, reference, how measured, scoring/reference visualization, explanation, recommendations).
- Every screen must preserve state and remain usable under resizing/orientation/window changes.

## History and sharing

- Save scan date/time, reference model, scoring version, enabled metric IDs/config hash, category scores, overall score, rank, metrics, coverage state, strongest/weakest metric ID tie sets, extrema-selection version, and local image links.
- Side-by-side Scan A vs Scan B is planned after initial V1, not required for first usable scan.
- Generate a clean share card for friends/social media.
- Shared assets must never expose the full private local photo unless the user explicitly chooses that option.
- No internal social feed, comments, followers, or public profiles in V1.

## Monetization direction

Development/alpha/beta are free-first.

Future business model:
- Free scan gives a limited overall result.
- Premium unlocks detailed ratios/tiers/analysis.
- Exact premium bundle can expand later.
- No ads.
- Build entitlement interfaces now so adding Google Play Billing later does not require rewriting result logic.

## Percentiles

- Do not fabricate “top X%” claims.
- Percentiles are deferred until ASCEND has a legitimate comparison dataset or a properly licensed external one.
- When added, the UI must identify the reference population/dataset used.


## Security and release decisions

- ASCEND assumes the APK can be reverse-engineered and the client can be modified.
- No privileged authorization may depend only on client-side checks.
- Provider/server secrets never ship in the APK.
- Production face assets use AES-GCM encryption with key material protected by Android Keystore.
- Sensitive structured/session state is encrypted at rest.
- Release builds disable cleartext traffic and are non-debuggable.
- Google authentication uses the platform Credential Manager and Supabase Auth; no custom password system in V1.
- Row Level Security plus explicit grants protect user-owned Supabase data; negative cross-user authorization tests are mandatory.
- AI receives minimized structured result context and cannot change deterministic scores or introduce recommendation IDs outside the curated catalogue.
- ASCEND gets its own permanent signing key; no key reuse from other apps.
- Every implementation phase must be tested as an in-place signed APK update over the previous phase build, in addition to fresh-install testing.
- Historical completed analyses preserve the engine/model/config versions that produced them, including the enabled metric set and deterministic strongest/weakest selection provenance.
