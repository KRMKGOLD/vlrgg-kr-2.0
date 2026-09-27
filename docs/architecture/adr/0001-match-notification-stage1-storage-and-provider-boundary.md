# ADR-0001: Stage 1 Match notification storage and provider boundary

- Status: Superseded by [ADR-0002](0002-match-notification-stage1-1-offline-firestore-boundary.md); retained as Stage 1 history
- Date: 2026-07-29
- Scope: `server` Match notification vertical slice

## Context and decision

Stage 1 needed durable subscription and START/END intent, safe recovery around an external provider call, and credential-free local verification before the server had a database or notification lifecycle.

It selected a disposable single-JVM H2/Flyway store, bounded JDBC pool, process-owned observation/delivery loops and an internal Firebase Admin adapter. Public/domain contracts used an opaque provider value rather than treating a push address as identity or authority. Offline tests replaced runtime factories and the async SDK boundary so they did not resolve ADC or make network calls.

Delivery persisted intent and a committed call marker before provider work. A result that became ambiguous after that marker entered `UNKNOWN` and was never automatically resent, preferring possible loss over duplicate user notification.

## Supersession and consequences

[ADR-0002](0002-match-notification-stage1-1-offline-firestore-boundary.md) replaced H2, registration-value identity, START/END scope and process loops with Firestore, anonymous Target authority, START-only intent and request-bound scheduling. The current normative contract is [Stage 1.1](../server-fcm-stage1.md).

This ADR remains only to explain the removed Stage 1 runtime and the retained `UNKNOWN` safety rationale. It does not claim power-loss, multi-process, production Firebase or device-delivery readiness.
