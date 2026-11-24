# COLLAB-00 — 공동 여행·클라우드 안전 계약

2026-11-17. Scope: provider-neutral contracts and fail-closed role, revision and disabled-gateway tests. This PR does NOT enable real account sign-in, invitations or online data synchronization.

## Local data is authoritative until explicit opt-in

The existing TripParticipantEntity IDs and ExpenseShareEntity links must remain unchanged when connecting to cloud identities. Starting shared travel must be optional. DisabledTripCollaborationGateway throws on all remote operations rather than pretending to create an invite or sync data. No new backend account, credentials, dependency, Room schema version, migration or billing setup is introduced in this PR.

## Server responsibilities before COLLAB-01 can ship

- Auth provider selection (Firebase Auth/Firestore is a candidate, not approved), cost limits, security rules and app configuration by GPT Work.
- Server validates the authenticated actor for each trip and operation. OWNER can invite, change roles and delete; EDITOR can edit permitted shared data; VIEWER is read-only. Never trust a client-provided role.
- Revoke invitation/role must take effect at server immediately; offline clients cannot claim changes synchronized.
- Idempotency key, per-record base revision and transactional conditional update. A revision mismatch returns a CONFLICT with both copies retained for user selection, no silent last-write-wins.
- Offline edits must be queued durably on device and encrypted in transit; queued/active/synced/conflict/revoked statuses exposed by real gateway.
- Online migration requires opt-in, identity mapping preserving local participant UUIDs and a rollback/backup plan.
- Change logs include safe identifiers and operation types, never reservation number, photo bytes, token, private note or plaintext expense payload.

## Next implementation

COLLAB-01: OAuth/login, server-side membership rules and invitation lifecycle; COLLAB-02: Room outbox, revision reconciliation and conflict UI; COLLAB-03: two-device account removal, revoke, offline conflict and data restoration QA. None may be marked complete from the policy helper alone.

No Google Play upload; CI and PR review required before merge.
