# ADR-0001: Stage 1 Match notification storage and provider boundary

- Status: Accepted decision; core Stage 1 implementation exists, normative acceptance open
- Date: 2026-07-29
- Decision scope: `server` Match notification vertical slice only
- Related: [Stage 1 technical contract](../server-fcm-stage1.md), [Matches product contract](../../feature/matches/README.md)

## Context at decision time

Match notifications needed durable subscription intent, one START and one END intent per target/Match, and safe recovery around an external push-provider call. At the time of this decision, the Ktor server had no notification implementation, database, Firebase Admin dependency, or background-job lifecycle. Stage 1 had to remain locally verifiable without live credentials or a network connection and could not turn a push address into identity or authority.

## Current implementation status

Stage 1 Wave A/B/C now has the accepted boundary's core implementation: H2/Flyway persistence, local/private desired-state routes, fixed-delay tracking, durable START/END delivery intent, offline-testable Firebase provider adapter, claim/retry processing and owned lifecycle. Normative acceptance remains open because the default delivery failure logger exposes an intent ID and the contract's bounded startup/lifecycle/provider observability is incomplete. This does not supersede the decision, weaken its redaction requirements, or expand its authority. Stage 2 live credential/project and App-supplied target proof, and all Stage 3 public/production concerns, remain deferred.

## Decision

Stage 1 uses a provider-neutral public/domain boundary (`registrationValue` and opaque `PushTarget`) with one internal Firebase Admin adapter. Firebase target mode is an internal, persisted selector: `FID` by default, or `LEGACY_TOKEN`; public DTOs and domain contracts do not expose either provider-specific term. The adapter is real and named-FirebaseApp based. Offline tests replace notification runtime factories and the async SDK boundary, so those paths do not resolve real ADC or make a network call; they do not directly inject and validate a credential.

Stage 1 uses a file-backed H2 store with Flyway migrations, bounded owned JDBC pool, and H2-specific claim SQL behind portable repository contracts. It is disposable, local, single-JVM, and non-production: it is not a production stepping stone and cannot be selected with public/production exposure or multi-instance ownership.

An external 32-byte lookup-digest key belongs to one Stage 1 store. The store retains a keyed HMAC lookup digest and non-secret key metadata, while an active raw provider value remains unencrypted at rest behind the storage interface. On provider-proven permanent invalidity, the raw value is logically erased and a keyed tombstone supports only conservative `target-refresh-required` re-sync; it never proves equality, reactivates the target, or restores subscriptions.

Delivery persists intent and call boundaries before asynchronous provider work. `UNKNOWN` is never automatically resent, so the design deliberately prefers possible loss in the post-marker/pre-call window over duplicate sending. The complete state, retry, lifecycle, and verification contract is normative in the linked Stage 1 technical contract.

## Consequences

- Stage 1 can be tested offline and retains a provider-replacement seam, but does not prove device delivery, live Firebase credentials, or FID compatibility.
- H2 crash/reopen tests prove only committed-state readability for the selected local JVM/filesystem configuration; they do not prove power-loss, PostgreSQL, or multi-process behavior.
- Active provider values require strict redaction but are not encrypted. Encryption, KMS/rotation, backup handling, and secure-deletion policy are Stage 3 gates.
- Retained production data, a second process, immediate Stage 3, or reliable local/CI PostgreSQL provisioning invalidates this H2 decision and requires PostgreSQL-first.

## Deferred decisions

Stage 2 owns the credential/project smoke and selected-mode compatibility preflight. Stage 3 owns PostgreSQL, public API authority, public deployment, encryption/KMS, multi-instance worker ownership, and production operations.
