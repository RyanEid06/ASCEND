# ASCEND Threat Model

Version: 2.1 Architecture Freeze
Status: Required before implementation and revisited before release

## 1. Protected assets

Highest sensitivity:
- raw/standardized face images
- auth refresh/session tokens
- signing keys
- server secret keys

High sensitivity:
- face-derived metric results
- normalized geometry/landmarks
- self-declared 13+ product-gate state and selected reference model
- history tied to an account
- research contribution linkage

Integrity-sensitive:
- scoring/reference model configuration
- category/overall results
- entitlements
- recommendation catalogue
- AI usage limits
- analysis/version metadata

## 2. Primary attackers and failures

### APK reverse engineering
Attacker decompiles the app to discover endpoints, algorithms, strings and public identifiers.

Control: nothing security-critical depends on APK secrecy. Secrets remain server-side; privileged checks are server-side.

### Modified client
Attacker changes local entitlement/age/score state or scripts API requests.

Control: backend re-validates ownership, entitlement, stored self-declared product-gate state where relevant, research consent/eligibility, quotas and schemas. The 13+ state is self-declared and is not treated as verified age.

### Stolen session token
Control:
- encrypted local session storage
- short-lived access tokens/refresh model supplied by auth provider
- logout/revocation
- re-authentication for destructive/high-risk actions where appropriate
- no tokens in logs

### Broken RLS / IDOR
Control:
- explicit grants + RLS
- ownership derived from auth.uid()
- negative cross-user tests in CI/staging
- no trust in client user_id fields

### Privilege escalation through database functions
Control:
- minimal SECURITY DEFINER usage
- fixed safe search_path
- function-specific grants
- tests for unauthorized invocation

### AI API theft or cost abuse
Control:
- provider key only in Edge Function secret store
- auth + rate limit + spending ceiling
- schema validation
- server-controlled model/token limits
- kill switch

### AI hallucination / unsafe advice
Control:
- deterministic recommendation allowlist
- structured response schema
- recommendation ID validation
- no scoring authority
- Hardmax/procedure output limited to the curated approved catalogue and structured informational wording
- fail closed to static deterministic explanations

### Malicious/corrupt media
Control:
- MIME/signature checks
- size/dimension/pixel budget
- safe decoding
- bounded memory
- reject unsupported/corrupt files
- no arbitrary file execution

### Metadata/privacy leak
Control:
- strip EXIF/GPS
- no image analytics
- no raw photo sync
- encrypted local asset
- backup exclusions
- redacted logs

### Lost/stolen phone
Control:
- Android sandbox
- encrypted local files/database
- Keystore-protected keys
- optional future app lock is separate UX
- deletion on uninstall remains platform behavior

### Rooted/fully compromised device
Residual risk: ASCEND cannot guarantee secrecy against a fully compromised OS/device. Document this limitation; do not claim otherwise.

### CI/CD secret leak
Control:
- least-privilege GitHub secrets
- protected release environments when available
- no echoing secrets
- no PR-from-untrusted-code access to release secrets
- signed release only from approved workflow/main

### Signing-key loss
Control:
- two encrypted/offline backups
- Play App Signing
- separate upload key after Play enrollment
- documented recovery/rotation process

### Supply-chain compromise
Control:
- dependency verification/locking
- update monitoring
- static scans
- pin sensitive GitHub Actions
- review dependency changes

### Database loss/corruption
Control:
- migrations
- tested backup/restore
- local development DB resetability
- production backup policy before public launch
- immutable/versioned historical analysis records

### Free-tier backend pause/quota exhaustion
Control:
- beta-only acceptance of free-tier constraints
- user-friendly offline/degraded states
- monitoring/manual operational checklist
- production hosting decision before public launch

### Replay/duplicate requests
Control:
- idempotency keys for submissions/AI operations
- nonce/state in OAuth
- server-side unique constraints and revision handling

### Sync resurrection
Failure: an offline device re-uploads a scan deleted elsewhere.

Control: server-authoritative deletion tombstones/revisions. Deletion wins.

### Account switching data leak
Control: local rows/assets are owner-scoped. Switching accounts filters/locks data and never exposes prior-account scans.

### Research re-identification
Control:
- collect only necessary derived fields
- separate account→contribution linkage in private schema
- call the data pseudonymous while revocation remains possible
- do not promise true anonymity when a deletion link exists
- research contribution follows the same self-declared 13+ product gate plus separate explicit consent and release-time legal/store review

### Unauthorized scoring-config changes
Control:
- versioned config
- SHA-256 hash
- schema validation
- historical config retention
- no silent remote production scoring changes in V1

### Deep-link/intent abuse
Control:
- allowlisted routes
- validate parameters
- no privileged action solely from a deep link
- exported Android components minimized

### Screenshot/recents leak
Control:
- protect capture/crop/manual-correction surfaces
- obscure sensitive recents where practical
- sharing requires explicit generated export

## 3. Security assumptions

ASCEND assumes:
- Google/Supabase/OpenAI or chosen providers may experience outages
- users may reinstall or clear app data
- users can manipulate a client they control
- the public application logic can be understood by attackers
- network availability is not guaranteed
- app process death can occur at any point

The design must remain safe under those assumptions.

## 4. Re-review triggers

Re-open this threat model when:
- adding cloud photo storage
- adding password auth
- adding public profiles/community features
- adding payments
- changing AI provider/data terms
- collecting raw images for research
- enabling percentiles/community datasets
- adding iOS/web clients
- adding new sensitive permissions
- changing signing/release ownership
