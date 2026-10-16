# Runtime and branding

## Purpose

Keep SLEKE identity, diagnostics and reusable presentation consistent while removing product-specific initialization and assets from frequently changing upstream implementation files.

## ADDED Requirements

### Requirement: Product identity remains compatible
The refactor SHALL retain application ID `com.sleke.store`, versionCode 76, versionName `4.8.4`, existing branding, signing configuration, database version 13 and existing preference keys.

#### Scenario: Existing installation is upgraded
- **WHEN** the refactored app replaces the current build
- **THEN** application identity and existing settings/data remain compatible without a reset or unrelated database migration

### Requirement: Runtime initialization is idempotent
Product runtime initialization SHALL use application-provided configuration, register diagnostics once and preserve other components' logging registrations.

#### Scenario: Initialization is invoked twice
- **WHEN** product initialization is repeated in one process
- **THEN** it does not create duplicate diagnostics or remove unrelated logging sinks

### Requirement: Diagnostics retain meaningful severity
Warnings SHALL be recorded as breadcrumbs. An error with a throwable SHALL retain the original exception and be recorded once. Logging SHALL not include credentials, complete account documents or personal identity fields.

#### Scenario: Error includes an exception
- **WHEN** a release diagnostic error contains a throwable
- **THEN** the original exception reaches the configured diagnostic sink once with its correct severity

### Requirement: SLEKE resources have stable ownership
SLEKE-specific presentation resources SHALL have feature-owned names and references so upstream removal of an unrelated resource cannot remove product behavior. Existing copyright and license notices SHALL be retained.

#### Scenario: Upstream cleans unused resources
- **WHEN** an upstream update deletes its own obsolete drawable
- **THEN** SLEKE email entry, catalog presentation and branding continue to resolve their owned resources

### Requirement: Presentation can be reused without host internals
SLEKE feature UI SHALL consume immutable presentation state and callbacks for host actions. It SHALL render without requiring Aurora navigation, Play models, account SDK objects or installer objects in its public interface.

#### Scenario: Feature content is previewed in isolation
- **WHEN** representative state and no-op callbacks are supplied
- **THEN** loading, populated, empty and error presentation can render without authenticating or starting background work

### Requirement: Structural changes retain an auditable contract
The implementation SHALL preserve the one-way application-to-feature dependency, keep domain rules independent of Android/network types, and make the existing style checks pass without suppressing their rules.

#### Scenario: A host implementation type enters feature code
- **WHEN** reusable feature code starts depending on an Aurora implementation class
- **THEN** an architecture check fails before integration
