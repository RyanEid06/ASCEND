# ASCEND Security Architecture

Version: 2.2 Architecture Freeze
Status: Canonical implementation contract
Owners: Ryan + Eddy

## 1. Security posture

ASCEND handles face photographs, face-derived measurements, account sessions, and potentially procedure-related recommendation context. Treat these as high-sensitivity product data even where a specific jurisdiction may classify them differently.

Security must not depend on secrecy of the APK, source code, client-side checks, obfuscation, or hidden endpoints. Assume an attacker can decompile the APK, inspect network traffic from their own device, automate requests, modify the client, and understand the database schema.

Core principles:
- local-first raw face privacy
- least privilege
- server-side authorization for privileged actions
- deterministic scoring independent of AI
- data minimization
- explicit versioning
- fail closed for security-sensitive paths
- no secret credentials in the APK or Git repository
- no unnecessary permissions
- deletion that actually removes associated data
- release builds are non-debuggable and cleartext traffic is disabled

## 2. Trust boundaries

### Trusted client components
Trusted only for local computation, never for authorization:
- geometry engine
- scoring engine
- on-device vision models
- encrypted local storage
- UI

A modified client can lie about its local state. Therefore server-side entitlements, rate limits, research consent/eligibility, account ownership, and any persisted self-declared 13+ product-gate state used by server features must be independently checked server-side. This is still self-declaration, not age verification.

### Trusted server components
- Supabase Auth
- Postgres with explicit grants + Row Level Security
- private-schema functions
- Supabase Edge Functions
- provider secrets
- future Play Billing verification endpoint
- future Play Integrity verification

### Untrusted inputs
Treat all of these as hostile:
- gallery images
- camera files and metadata
- deep links
- local intents
- AI responses
- network responses
- remote configuration
- user-modified local state
- client-submitted ownership IDs
- client-submitted entitlement state
- client-submitted self-declared age-gate / eligibility state

## 3. Secrets policy

Never commit or package:
- OpenAI/other AI provider API keys
- Supabase secret/service-role keys
- Google OAuth client secrets
- service-account private keys
- ASCEND signing keystore
- keystore passwords
- production database passwords
- webhook secrets
- private test credentials

Allowed in the APK:
- Supabase project URL
- Supabase publishable/anon key as appropriate to the SDK
- Google public Android/OAuth client identifiers
- public feature/config identifiers

All server secrets live in the relevant service secret store. GitHub Actions receives only secrets required for build/deploy jobs.

## 4. Android storage security

### Raw/standardized face assets
V1 face photos remain local by default and are never uploaded by the normal sync path.

Pipeline:
1. acquire from CameraX or Android Photo Picker
2. safe decode with size/dimension bounds
3. correct orientation
4. strip EXIF/location metadata
5. validate face count/pose/quality
6. crop/normalize to the internal analysis asset
7. encrypt using AES-GCM with a unique random nonce/IV per file
8. store only in app-private storage
9. remove unnecessary plaintext/original temporary data

Encryption key material must be generated/protected with Android Keystore. Plaintext face assets should exist only in memory or narrowly scoped temporary processing where unavoidable.

Encrypted asset format is versioned. Persist enough non-secret metadata to migrate safely:
- formatVersion
- keyVersion
- nonce/IV
- ciphertext + authentication tag
- algorithm identifier only if the format ever supports more than one algorithm

Use a random content/data key or passphrase where the storage library requires one, wrapped/protected by a Keystore key. Define atomic write, rotation, missing/invalid key, logout, Delete All and uninstall/reinstall behavior before implementation. Never treat a raw Keystore alias string as the database secret itself.

### Structured local data
Sensitive structured state uses Room with a **supported** SQLCipher-for-Android integration proven compatible with the chosen Room version in WP00. The deprecated `android-database-sqlcipher` package is forbidden. Database passphrase/key-envelope handling follows the versioned Keystore contract above.

### Session storage
Do not use deprecated Android Security Crypto wrappers as the permanent design. Store Supabase session material in an AES-GCM encrypted blob with a Keystore-protected key.

### Backup
Sensitive ASCEND application data must not silently enter Android Auto Backup/device-transfer backup. Release configuration must explicitly disable or exclude:
- face images
- auth/session material
- database encryption keys
- landmark/measurement data
- private cache

## 5. Network security

Release configuration:
- HTTPS only
- cleartext traffic disabled
- no trust-all certificate managers
- no disabled hostname verification
- no debug network overrides in release
- bounded request/response sizes
- timeouts and cancellation
- schema validation at API boundaries

Certificate pinning is not required for V1 unless a later threat review justifies its operational cost.

## 6. Authentication

V1 account authentication:
- guest mode
- Sign in with Google through Android Credential Manager
- Supabase Auth as the account/session authority

Do not implement a home-grown password system in V1.

Google authentication should use nonce/state protections supported by the platform/provider. The app does not store Google access tokens because ASCEND does not need access to Google user data.

Every local scan has an ownership scope:
- GUEST
- ACCOUNT:<Supabase user UUID>

Account switching must never expose the previous account's local data.

## 7. Authorization and RLS

Every exposed user-owned table must:
- have Row Level Security enabled
- use explicit grants
- validate ownership from auth.uid(), not from a trusted client claim
- prevent ownership reassignment
- prevent clients from writing privileged fields

Examples:
- profiles: user may read/update only own profile
- analyses: user may read/insert/delete only own analyses
- measurements: access follows owned analysis
- entitlements: user may read own; client cannot grant/update
- dataset contributions: no normal direct client writes
- AI usage/security events: private schema/backend only

SECURITY DEFINER functions must use the minimum privilege necessary and an explicitly safe search_path.

Automated negative tests are mandatory:
- User A cannot read/write User B
- anonymous access cannot read private account data
- client cannot grant premium
- client cannot spoof another owner
- client cannot directly write research tables
- client cannot bypass server-side entitlement, research-consent, or stored product-gate checks

## 8. AI security

AI is outside the scoring authority.

The AI layer may receive only minimized structured context necessary for explanation. Never send:
- raw face photos
- thumbnails
- full face mesh/landmarks
- account UUID
- name/email
- exact date of birth
- authentication tokens
- device identifiers

AI responses are untrusted input. Validate against a strict schema and approved recommendation IDs.

AI may not:
- alter metric values
- alter tiers
- alter category/overall scores
- invent medical diagnoses
- invent unsupported procedures
- bypass the configured product-gate, entitlement, or curated-content restrictions
- create recommendation IDs outside the curated catalogue

Provider keys are backend-only.

## 9. Abuse controls

Protect expensive or privileged server routes using layered controls:
- valid Supabase JWT where account is required
- schema and size validation
- per-user/IP/device-appropriate rate controls
- idempotency keys for retryable expensive operations
- server-side entitlement verification
- server-side validation of stored self-declared 13+ state where an account/server feature requires it
- global AI provider spending circuit breaker
- future Play Integrity for production-distributed sensitive requests

Play Integrity enforcement must not block pre-Play sideload QA. Introduce it in report-only mode first and enforce after internal/closed Play testing is established.

## 10. Image ingestion hardening

Validate:
- supported MIME/content signatures
- encoded byte size
- decoded dimensions
- pixel budget
- color/profile handling
- exactly one valid face
- minimum usable face resolution

Use sampled/bounded decoding to prevent memory exhaustion from very large or malformed images. Do not trust filename extensions.

## 11. Logging and telemetry

Release logs must never include:
- face image bytes/paths intended to be private
- landmarks
- exact measurements unless explicitly needed in a local developer-only tool
- AI free text
- JWTs
- API keys
- name/email

Crash/analytics systems, if added later, use allowlisted metadata only:
- app version
- device/OS class
- exception category
- screen/feature identifier
- engine/config versions
- coarse failure reason

Automatic collection should be deliberately configured rather than accepted blindly.

## 12. Screen/privacy handling

Sensitive capture/crop/manual-landmark surfaces should prevent casual exposure in recents/screenshots where practical.

Do not globally block screenshots on all result screens because explicit result sharing is a product feature.

Share flow must render a dedicated share asset and use a temporary content URI/FileProvider grant. Never expose raw private filesystem paths.

## 13. Software supply chain

CI hardening target:
- dependency update monitoring
- native-library inventory and 16 KB page-size/alignment validation whenever the APK/AAB contains native libraries
- CodeQL/static analysis where supported
- secret scanning
- Gradle dependency verification/locking where practical
- pinned GitHub Action versions/SHAs for sensitive workflows
- no automatic major dependency upgrades
- no automatic merge without tests

Before release, inspect the built APK/AAB for:
- embedded secrets
- debuggable=true
- unexpected cleartext/network config
- private fixtures
- test credentials
- production localhost endpoints
- signing certificate mismatch

## 14. Security validation gate

Before public release:
- threat model reviewed
- RLS negative tests green
- account-deletion path tested
- malicious image/boundary tests performed
- release APK reverse-engineered by the team using tools such as JADX/MobSF
- no server secret recoverable from APK
- Play Integrity production policy tested if enabled
- AI abuse/rate-limit tests green
- database restore procedure tested
- signing-key backup verified
- dependency/security scans reviewed

No claim of being “unhackable” is allowed. The goal is a minimized attack surface, layered controls, and recoverable operations.
