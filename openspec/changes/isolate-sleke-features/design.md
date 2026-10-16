# SLEKE module design

## Context

The reviewed repository baseline is the merge of PR 5 at `c58f58eb0`. See [proposal.md](proposal.md) for motivation and the capability files for behavior.

The complete human-readable architecture design is [SLEKE module architecture](../../../docs/superpowers/specs/2026-10-16-sleke-module-refactor-design.md). That document is the single source for ownership, contracts, sequencing, alternatives, risk controls and acceptance; the web reader is generated from it.

## Goals / Non-Goals

**Goals:** Keep the existing two modules, use feature-first layering, reduce changes inside upstream orchestration, and correct the identified reliability defects with regression coverage.

**Non-Goals:** New application framework, extra Gradle modules, product redesign, backend deployments, release publication or database/preference resets.

## Decisions

- Use small domain contracts with application-owned adapters rather than allowing `:sleke` to depend on Aurora implementation classes.
- Retain upstream screen/worker algorithms and call owned feature routes/policies rather than duplicating the entire upstream shell.
- Distinguish enterprise and Play identities, expose explicit sign-out scopes and invalidate stale asynchronous results.
- Separate download state observation from transfer and installation ownership; validate artifacts before handoff.
- Preserve the current installed-list/update-mode differences rather than changing filtering semantics during extraction.
- Preserve the existing recovery constants, stored keys, package identity, version and signing setup.

## Alternatives considered

Moving files without changing dependencies leaves reverse coupling and duplicate behavior. A new collection of Gradle modules adds build complexity before boundaries are proven. A separate full SLEKE application shell duplicates upstream update/storage/notification algorithms. These alternatives are not selected.

## Risks / Trade-offs

- Interface proliferation → Keep contracts aligned with actual host services and avoid empty layers.
- Behavioral drift → Add characterization and defect regression scenarios before each move.
- Stale identity or operation completions → Cancel/invalidate work on generation changes and test races.
- Duplicate installation after process recovery → Reconcile external handoff state; never promise unconditional exactly-once external effects.
- Existing source-filter inconsistency → Preserve the explicit truth table and make any later product change a separate decision.
- Runtime validation unavailable locally → Preserve the pending acceptance status until a device/emulator verifies those scenarios.

## Migration Plan

Follow the eight independently verifiable stages in the human-readable design. No product code is changed in this specification phase. Revert an individual failed implementation stage rather than resetting application data.
