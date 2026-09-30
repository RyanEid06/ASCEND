# ASCEND Data Lifecycle

Version: 2.0 Architecture Freeze
Status: Canonical privacy/data contract

## 1. Data classes

### A. Raw/standardized face media
Examples:
- front analysis image
- profile analysis image

Default location: local device only.
Default retention: until scan/user deletion or app uninstall.
Cloud sync: prohibited in V1 normal flow.

### B. Derived local geometry
Examples:
- landmarks required for overlays/recomputation
- crop transforms
- pose metadata

Default location: encrypted local DB/files.
Cloud sync: full landmark payload prohibited by default in V1.

### C. Measurement/result data
Examples:
- metric values
- T1-T5
- category scores
- overall
- rank
- confidence
- version hashes

Guest: local only.
Account: eligible for cloud numeric-history sync.

### D. Account/profile data
Examples:
- Supabase user ID
- age band
- selected reference model
- intent
- consent states
- entitlement

Cloud: yes as required for account operation.

### E. Research contribution
Adults only, separate consent, derived subset only.
Pseudonymous while an account-to-contribution deletion link exists.

### F. AI context
Ephemeral minimized structured context only.
No raw photo/full mesh/direct account identifiers.

## 2. Image acquisition lifecycle

Camera:
- use CameraX
- prefer in-memory processing where practical
- do not publish captures to public gallery by default

Gallery:
- Android Photo Picker
- no broad media-library permission
- consume only selected URI

Both:
1. validate input limits
2. normalize orientation
3. strip metadata
4. quality/face validation
5. create standardized internal analysis image
6. encrypt/store private asset
7. remove unnecessary plaintext originals/temp files

## 3. Internal analysis asset

Store a bounded standardized representation rather than arbitrary phone-camera originals.

Requirements:
- enough resolution for validated metrics
- deterministic normalization/crop metadata
- encoded/decode format explicitly versioned if it affects results
- encrypted app-private storage
- no EXIF/GPS
- image dimensions/pixel budget capped

The exact resolution is finalized by CV validation, not guessed for visual convenience.

## 4. Scan state and interruption recovery

Persist scan lifecycle:
- FRONT_PENDING
- FRONT_CAPTURED
- FRONT_VALID
- PROFILE_PENDING
- PROFILE_CAPTURED
- PROFILE_VALID
- LANDMARKING
- MEASURING
- SCORING
- COMPLETE
- FAILED_RECOVERABLE

After process death/crash:
- detect resumable scan
- offer resume/discard
- validate required local assets
- clean abandoned temporary artifacts

## 5. Local ownership

Every scan belongs to:
- GUEST
or
- ACCOUNT:<UUID>

Account switch/sign-out:
- hides/locks data belonging to other account scopes
- does not leak previous user history
- applies explicit product rules for whether account-scoped local data remains encrypted on device or is removed on sign-out

V1 default recommendation: remove/deauthorize account-scoped local access on sign-out unless a clearly tested secure multi-account local model is implemented.

## 6. Cloud restoration

A cloud-restored analysis contains numeric history, not the original local photo.

UI modes:
Full local result:
- original standardized local image
- overlays
- metrics
- explanations

Cloud-restored result:
- stored scores/metrics/version
- reference diagrams
- explicit notice that original local image is unavailable
- no fabricated photo overlay

## 7. Delete Scan

Must remove:
- encrypted front/profile assets
- thumbnails
- local landmark data
- local measurements/results
- local AI explanation cache
- temporary share files associated with the scan where tracked
- queued sync work
- cloud rows/tombstone as required for signed-in owner

Deletion must be idempotent and resilient to offline retries.

## 8. Delete All / Account deletion

Delete All local:
- all local scan assets/results
- abandoned temp/share cache
- relevant local queues

Account deletion:
- follow BACKEND_CONTRACT.md
- then clear local account-scoped assets/session

Research contribution handling follows the exact published consent policy and revocation linkage.

## 9. Android backup/device transfer

Sensitive local ASCEND state is excluded/disabled from platform backup unless a later reviewed design explicitly permits a narrow subset.

Cloud numeric history is the controlled restoration mechanism for accounts.

## 10. Sharing

Default share card contains only selected result information and ASCEND branding.

Raw/local face inclusion is separate explicit user intent.

Implementation:
- render generated bitmap/PDF/image asset to temporary app cache
- share via FileProvider/content URI
- grant temporary read permission
- schedule cleanup

Never expose app-private path strings.

## 11. Logs/crash/analytics

General telemetry never receives:
- raw face
- thumbnails
- exact landmark arrays
- exact facial measurement payload as generic event properties
- free-text AI conversations by default
- account auth tokens

Use coarse event/failure categories.

## 12. Retention and cleanup

Define periodic cleanup for:
- abandoned scan temp files
- expired share files
- obsolete AI caches
- failed partial writes

Do not automatically delete completed user history without a clear documented retention rule.

## 13. Version provenance

Each completed analysis stores enough provenance to explain/reproduce the result:
- app version
- analysis engine version
- front landmark model + artifact hash/version
- profile model/extractor version
- reference model version
- metric config hash
- recommendation version

Historical records retain their original provenance even after app/config updates.
