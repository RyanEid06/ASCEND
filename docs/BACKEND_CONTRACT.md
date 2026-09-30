# ASCEND Backend and Authentication Contract

Version: 2.0 Architecture Freeze
Status: Canonical backend contract

## 1. Environment model

### Local
Use Supabase CLI + Docker-compatible local stack for migrations, RLS tests and Edge Function development.

### Hosted staging
One hosted Supabase project for integrated mobile QA and Google auth testing.

### Production
Separate project created/configured before public release. Production data must never be used as a casual development fixture.

Do not create extra providers/environments without a concrete need.

## 2. V1 authentication

Supported:
- Guest
- Sign in with Google using Android Credential Manager and Supabase Auth

Not supported in V1:
- custom username/password
- direct Google API access
- home-grown token issuance

The app stores ASCEND/Supabase session state only. It does not retain Google access tokens.

## 3. Guest to account conversion

After a guest finishes the one allowed guest scan and signs in:
- prompt: Save this result to my account?
- if accepted: change local owner scope to the authenticated account, then sync allowed numeric history
- if declined: do not upload; retain guest-scoped local result until deletion/product cleanup rules apply
- never silently upload a guest scan merely because the user authenticated

## 4. Suggested Postgres schemas

### public/exposed
profiles
- user_id UUID PK -> auth.users
- age_band
- selected_reference_model
- intent
- created_at
- updated_at

analyses
- id UUID PK generated client/server-safe
- user_id UUID
- created_at
- completed_at
- profile_side
- analysis_status
- overall_score
- rank_label
- harmony_score
- dimorphism_score
- angularity_score
- misc_score
- analysis_engine_version
- front_landmark_model_version
- profile_model_version
- reference_model_version
- metric_config_hash
- recommendation_version
- app_version
- sync_revision
- deleted_at nullable

analysis_measurements
- analysis_id
- metric_id
- raw_value/normalized_value where required
- tier
- hidden_score where product policy allows cloud persistence
- confidence
- available
- auto_assisted_state
- composite PK/unique(analysis_id, metric_id)

consents
- id
- user_id
- consent_type
- state
- policy_version
- created_at
- revoked_at

entitlements
- user_id
- entitlement_type
- source
- state
- valid_from
- valid_until
- updated_at

### research
dataset_contributions
- contribution_id random UUID
- created_at
- age_band
- reference_model
- approved derived metric subset
- engine/config versions
- withdrawn_at nullable

### private
contribution_links
- user_id
- contribution_id

ai_usage
- user_id
- usage_bucket
- request_count
- estimated/provider tokens where necessary
- updated_at

security_events
- coarse allowlisted security/abuse events only; no face payload

## 5. What is deliberately not synced

Normal V1 cloud sync excludes:
- front/profile photos
- thumbnails
- EXIF
- full MediaPipe face mesh
- full profile landmark payload
- arbitrary AI chat transcripts

If later features truly require a derived geometry subset, that addition requires a privacy/threat review first.

## 6. RLS/grant principles

profiles:
- SELECT/UPDATE own row only
- INSERT own row only if policy allows provisioning path
- cannot change user_id

analyses:
- SELECT/INSERT/DELETE own
- updates narrowly constrained; completed analyses should be treated as effectively immutable
- cannot change user_id

analysis_measurements:
- access only if parent analysis belongs to auth.uid()

consents:
- user can read own consent history
- writes must preserve audit/version semantics

entitlements:
- user can read own
- no direct user INSERT/UPDATE/DELETE

research/private schemas:
- no direct normal client access
- Edge Function/database function path only

Every policy has positive and negative integration tests.

## 7. Sync contract

IDs:
- UUIDs to permit offline creation without collision

Revisions:
- track a server/monotonic revision or updated_at discipline sufficient for deterministic conflict resolution

Deletion:
- deletion wins over stale offline updates
- use tombstone/revision semantics as needed to prevent resurrection
- raw local assets are deleted immediately on local delete
- remote delete syncs when connectivity returns

Completed historical results:
- do not silently recompute or mutate them when reference models change
- new analysis uses the then-current supported reference model version
- historical result continues to display its stored version

## 8. Account deletion

In-app route:
Settings -> Account -> Delete ASCEND account

Flow:
1. explain consequences
2. re-authenticate/verify fresh session where needed
3. call server-side deletion endpoint
4. delete/withdraw linked research contribution when policy requires and linkage exists
5. delete analyses and dependent measurements
6. remove entitlements/consents/profile
7. delete auth user
8. revoke sessions
9. clear local account-scoped session/data/assets
10. show completion/failure state

Also provide the external deletion/support route required by store policy.

Deletion endpoints must be idempotent/recoverable from retry.

## 9. Research contribution contract

V1:
- adults only
- separate explicit opt-in
- fresh standardized capture preferred/required for research-quality submissions
- no raw photo contribution
- derived subset only
- contribution called pseudonymous, not anonymous, while revocation link exists

Identity link:
private.contribution_links maps account to random contribution ID.
Research table does not contain user_id/email/name.

Revocation:
- stop future submissions immediately
- delete or mark withdrawn according to published consent/data policy
- remove mapping as appropriate

## 10. AI Edge Function contract

Endpoint receives:
- authenticated account/session context where feature requires account
- scan/result identifier or minimized structured result
- requested explanation type
- idempotency key

Server:
1. validate JWT
2. load/validate user ownership
3. check age/intent where Hardmax-related
4. check entitlement/rate quota
5. construct minimized allowlisted prompt payload
6. call configured provider
7. validate strict structured response
8. reject recommendation IDs not in approved set
9. return sanitized response
10. update quota/caching metadata

No provider secret reaches the client.

## 11. Remote flags

Server-controlled operational flags may include:
- registration_enabled
- ai_enabled
- dataset_contribution_enabled
- premium_enabled
- minimum_supported_app_version

Feature flags are not a substitute for server authorization.

Scoring configuration is separate from feature flags and must not silently mutate historical or in-progress analyses.

## 12. Free-tier policy

Supabase Free is acceptable for development/closed beta if its current quotas and pause/backup limitations remain suitable.

Before public release:
- re-check current pricing/quotas
- ensure production availability/backup expectations are met
- test database backup and restore
- define alerting/operational owner

The architecture must not depend on a provider being free forever.
