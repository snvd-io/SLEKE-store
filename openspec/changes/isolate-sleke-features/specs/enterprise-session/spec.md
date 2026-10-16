# Enterprise session

## Purpose

Provide one consistent enterprise session without conflating Firebase enterprise identity, persisted presentation hints and the independent Google Play session.

## ADDED Requirements

### Requirement: Membership belongs to the current identity
Enterprise access SHALL be resolved for the currently authenticated enterprise identity. A persisted membership flag SHALL be a presentation hint rather than proof of current access.

#### Scenario: Stored identity differs from current identity
- **WHEN** restored preferences describe a different user from the authenticated enterprise user
- **THEN** old enterprise content and membership are cleared before routing or presenting data for the current user

#### Scenario: Membership is revoked
- **WHEN** a refreshed membership result no longer permits enterprise access
- **THEN** the previous enterprise content becomes unavailable and its presentation state is cleared

### Requirement: Access outcomes remain distinct
The session SHALL distinguish restoring, signed out, checking access, permitted access, no enterprise membership and temporarily unavailable access.

#### Scenario: Membership lookup fails
- **WHEN** the membership service is temporarily unavailable
- **THEN** the user remains authenticated, the failure remains retryable and it is not persisted as a successful denial

#### Scenario: Public entry during a membership outage
- **WHEN** membership is temporarily unavailable and the required Play session is ready
- **THEN** the regular catalog is available with enterprise content blocked and an explicit membership retry

#### Scenario: Authenticated user has no membership
- **WHEN** membership lookup succeeds without an enterprise association
- **THEN** the regular catalog remains available without granting enterprise access

### Requirement: Email entry awaits required session preparation
Successful email entry SHALL resolve current-user membership and await the required anonymous Play session preparation before emitting its final navigation outcome.

#### Scenario: Email entry succeeds
- **WHEN** enterprise email authentication succeeds and the required Play session becomes ready
- **THEN** navigation occurs once using the current identity, membership and original deep-link intent

### Requirement: Play identity remains independent
Google Play login, account switching, individual Play-account removal, token refresh and device-spoof resets SHALL preserve enterprise identity unless full application sign-out is explicitly requested.

#### Scenario: Google Play account changes
- **WHEN** the active Play account is switched or refreshed
- **THEN** no enterprise identity is fabricated or silently signed out

### Requirement: Sign-out scopes are explicit
Enterprise-only sign-out SHALL clear enterprise identity and cached enterprise state. Full application sign-out SHALL attempt enterprise cleanup, Play cached-auth cleanup and Play account-record cleanup, be idempotent and invalidate outstanding sign-in work.

#### Scenario: Full sign-out races with sign-in
- **WHEN** full sign-out occurs while membership or Play preparation is in flight
- **THEN** a later completion cannot restore the old account or navigate into its catalog

#### Scenario: One cleanup fails
- **WHEN** one full-sign-out cleanup operation fails
- **THEN** the other cleanup operations are still attempted and the partial failure is reported for retry

### Requirement: Cancelled entry preserves the previous session
Cancelling email entry SHALL reset its busy state without deleting an existing session. Lifecycle recreation SHALL not repeat account creation or navigation effects.

#### Scenario: User cancels email entry
- **WHEN** the provider returns cancellation
- **THEN** the previous session remains intact and email entry can be started again

### Requirement: Observation and diagnostics respect lifecycle and privacy
Session observation SHALL have one owned lifecycle with deterministic listener cleanup. Public feature state SHALL expose domain identity data, and diagnostics SHALL exclude credentials and personal identity fields.

#### Scenario: Session owner is disposed
- **WHEN** its owning lifecycle ends
- **THEN** listeners are detached and no further session updates or navigation effects are delivered
