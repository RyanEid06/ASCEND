# Architecture Freeze 2.0 — independent review notes

Status: **review proposal, not an amendment to the frozen contracts**. Ryan and Eddy should decide whether to accept each change. This review is based on the Architecture Freeze 2.0 decision list and the repository's current planning documents.

## Overall assessment

I agree with the direction of most engineering decisions: standardized capture, deterministic and versioned calculations, honest unavailable states, local-first face photos, explicit consent, server-side authorization, negative security tests, and signed in-place update testing. These are sound foundations.

My main reservation is about **what the scores mean**, especially for minors. A reproducible calculation can still encode subjective or weakly supported assumptions. The release gate should require evidence that each published metric is repeatable and that its tier boundaries, weights, and labels are appropriate for the claims shown to users.

## Proposed CHANGE decisions

| Decision area | Proposed change | Reason / acceptance condition |
| --- | --- | --- |
| 13+ scoring and community ranks | Review whether ages 13–17 should receive numerical attractiveness scores and rank labels. Until that review is complete, do not treat the current minor experience as release-ready. | The potential wellbeing impact of appearance rankings on minors warrants a dedicated product, safety, and store-policy review. Softmax information can be assessed separately from ranking. |
| Product language: “objective” / “ideal” | Call extracted geometry *measurements* and the result an **ASCEND model score**. Explain that ideal regions, weights, and ranks are model choices, not universal facts about attractiveness. | Determinism establishes reproducibility for fixed inputs and configuration; it does not validate the chosen reference values or their interpretation. |
| Four category weights at 25% each | Keep equal weights as an initial test configuration only. Freeze production weights after metric reliability, category coverage, and sensitivity analyses. | Misc features may depend heavily on lighting, makeup, grooming, and device processing. Automatically giving Misc one quarter of Overall could dominate the result with unstable inputs. |
| Male/Female reference selection | Retain explicit user selection, but label these as **reference models**, not an inferred identity. Define the experience for users who do not identify with either label or do not want to choose one. | The selected model changes score interpretation. The UI should communicate that choice clearly without claiming to determine sex or gender. |
| Community rank labels and thresholds | Keep labels and thresholds provisional until research and user testing support them. Consider a less derogatory label than “Sub 5,” particularly if minors remain eligible. | Rank names are product judgments, not population percentiles. Versioned thresholds are necessary but are not, by themselves, evidence that a rank is useful or appropriate. |
| Hardmax 18+ rule | Keep the client and backend age gates, but document the assurance limit of self-declared DOB. Do not claim the architecture makes under-18 access impossible without an age-assurance mechanism that supports that claim. | A user can enter a different DOB. Server enforcement prevents a modified client from simply hiding the gate; it cannot verify the truth of self-declared age. |

## Release evidence needed

1. **Per-metric validation:** publish only metrics with defined extraction failure rules, repeated-capture error bounds, pose/lighting/device sensitivity results, and an evidence trail for the scoring direction and boundaries.
2. **Overall-score sensitivity:** test how missing or unstable metrics, category weights, and small landmark shifts change a user's score and rank. Choose visible score precision from this error, as the current plan proposes.
3. **Fairness review:** test performance and failure rates across the populations and capture conditions the app intends to support. Skin color must not be directly rewarded or penalized; this also requires checking whether lighting and extractor errors create indirect disparities.
4. **User comprehension:** verify that users understand the score is relative to a selected ASCEND reference model, that ranks are not population percentiles, and that photo-derived traits have limits.
5. **Minor experience:** decide the age-appropriate result and recommendation experience before release, including rank visibility and procedure-related content.

## Decisions I would retain

- AI remains outside scoring and receives minimized structured context.
- Unreliable measurements fail or become unavailable; missing values are never fabricated.
- Raw face images stay local in normal V1 operation, encrypted and excluded from backup.
- Historical results remain tied to the model and configuration that produced them.
- Research contribution stays separate, explicit, adult-only, and accurately described as pseudonymous when linkable.
- RLS, explicit grants, backend entitlement checks, abuse limits, malicious-image handling, deletion tests, and signed upgrade testing remain mandatory.

The repository's `reference-data/v1/` is valuable research input. Its own README correctly separates source-backed benchmark records from final runtime scoring constants. That distinction should remain a public-release gate.
