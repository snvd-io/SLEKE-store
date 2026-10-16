# SLEKE catalog

## Purpose

Keep SLEKE catalog entries usable and identifiable while combining authoritative catalog data with optional Play display metadata and reusable feature presentation.

## ADDED Requirements

### Requirement: Catalog source fields remain authoritative
The catalog SHALL preserve each source entry's package name, download URL, type, country and source identity when optional display metadata is applied.

#### Scenario: Play metadata is available
- **WHEN** a catalog entry receives Play title, artwork, rating or description
- **THEN** its package, download URL, type, country and source identity remain unchanged

### Requirement: Enrichment failure retains the entry
The catalog SHALL retain its source entry when authentication, a Play result or enrichment is unavailable. Cancellation SHALL propagate instead of becoming a successful fallback result.

#### Scenario: Metadata cannot be loaded
- **WHEN** Play authentication is absent or a metadata request fails
- **THEN** the entry remains visible with its source fields and a usable source download URL

#### Scenario: Search is cancelled
- **WHEN** an obsolete search or session is cancelled
- **THEN** its result cannot replace the current catalog state

### Requirement: Pagination has stable item boundaries
The catalog SHALL include each matching entry once and in source order across initial and subsequent page loads, including when requested page sizes differ.

#### Scenario: Initial and append sizes differ
- **WHEN** the initial request loads more entries than a later append request
- **THEN** concatenated pages contain no duplicated or skipped source entries

### Requirement: Feature presentation preserves navigation
SLEKE presentation SHALL retain the regular catalog, Sleke Apps tab, enterprise drill-down, back behavior, search and existing loading, empty, error and download states.

#### Scenario: Enterprise access is allowed
- **WHEN** the current enterprise session permits access
- **THEN** the enterprise catalog is available through the existing entry points and back navigation returns to the previous feature view

#### Scenario: Regular catalog entry is opened
- **WHEN** a user opens a catalog app
- **THEN** the host opens its details using that entry's package name

### Requirement: Metadata work is bounded and session scoped
Catalog enrichment SHALL limit concurrent work, share duplicate in-flight requests within the same session and discard obsolete-session results without changing source order.

#### Scenario: Duplicate metadata request
- **WHEN** the same package is requested concurrently in one active session
- **THEN** callers share the in-flight result and a subsequent identity change prevents that result from entering the new session

### Requirement: Empty catalog and failed catalog are distinct
The catalog SHALL distinguish a successful empty result from a failed fetch and provide a retry that performs fresh work.

#### Scenario: Catalog recovers after failure
- **WHEN** a source fetch fails and the user retries after connectivity returns
- **THEN** a new fetch can populate the catalog without recreating the screen or application
