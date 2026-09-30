# ASCEND Frozen Product Decisions

Status: baseline product decisions for implementation. A change to one of these items should be deliberate and documented.

## Identity and positioning

- Name: ASCEND.
- Android first; Google Play is the first public-store target.
- English first.
- Portrait-only for V1.
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

## Scoring model

- User selects the scoring reference model rather than the app inferring sex/gender.
- Two initial reference models: Male and Female.
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
- Overall is /10 and may be displayed with up to two decimals.
- Penalties come from distance from the configured ideal/T1 target; there is no special arbitrary “T5 catastrophe” multiplier.
- Metric weights exist inside categories and are configurable data.
- Missing/unreliable required measurements should trigger a retake or be marked unavailable; the app must not invent values.

## Community overall ranks

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

Misc is intentionally retained because it is community terminology.

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

- Onboarding asks the user's intent.
- Softmax can include grooming, hairstyle, skincare/presentation, sleep, fitness/presentation, and similar non-procedure guidance.
- Hardmax is restricted to users 18+.
- 13-17 users still receive the full measurement/scoring experience and softmax guidance.
- Hardmax should be based on a curated recommendation database; AI may explain approved content but must not invent medical treatment.
- Procedure content is informational, not individualized medical advice.

## Age and onboarding

- Product is 13+.
- Simple self-declared age/DOB gate; no ID verification in V1.
- Hardmax unavailable to under-18 users.
- Initial wizard includes:
  - age/DOB gate
  - selected Male/Female reference model
  - Softmax/Hardmax intent where age-eligible
  - guest vs account path
  - privacy/consent notices
  - capture tutorial

## Guest and account behavior

- Guest:
  - one scan
  - one result
- Account:
  - Google sign-in through Android Credential Manager + Supabase Auth in V1
  - multiple scans
  - saved history
  - result restoration/sync of allowed numeric data
  - future premium entitlement support
- Guest -> account migration is explicit: offer to save the guest result after authentication; never silently upload it.
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
- “Not now” must remain a valid path.
- V1 dataset contribution should use only an approved derived numeric/geometry subset, not raw photos.
- Do not include under-18 users in the research/reference dataset in V1.
- Consent state is auditable and revocable. A private account-to-random-contribution linkage may exist solely so withdrawal/deletion can be honored; therefore V1 contribution data is described as pseudonymous rather than truly anonymous while that linkage exists.
- This consent is separate from Terms acceptance.

## History and sharing

- Save scan date/time, reference model, scoring version, category scores, overall score, rank, metrics, and local image links.
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
- Historical completed analyses preserve the engine/model/config versions that produced them.
