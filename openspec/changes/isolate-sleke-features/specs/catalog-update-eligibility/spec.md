# Catalog and update eligibility

## Purpose

Keep installed-app visibility and automated update eligibility predictable while sharing catalog access and preserving their intentionally different filtering scopes.

## ADDED Requirements

### Requirement: Installed-app visibility preserves catalog scope
Installed-app presentation SHALL retain the current catalog intersection and applicable blacklist behavior independently of the update-source preference.

#### Scenario: Installed package is absent from the catalog
- **WHEN** installed apps are loaded with a successful catalog snapshot
- **THEN** a package outside that catalog is excluded from the SLEKE installed-app list

### Requirement: Update modes retain their existing meaning
With the existing catalog filter enabled, update candidates SHALL intersect the catalog and retain the current branch behavior. With it disabled, candidates SHALL retain the existing non-catalog installer-source and F-Droid filtering rules. The persisted key and default SHALL remain compatible.

#### Scenario: Catalog filtering is enabled
- **WHEN** the existing catalog-filter preference is true
- **THEN** the update candidate set is restricted to catalog membership without newly applying the exclusions currently bypassed in that branch

#### Scenario: Catalog filtering is disabled
- **WHEN** the preference is false, including its current default
- **THEN** the existing non-catalog filtering path is used

### Requirement: Catalog failure cannot broaden eligibility
A failed catalog fetch SHALL remain distinct from a successful empty catalog and SHALL not be treated as permission to use every installed package. A later retry SHALL perform fresh work.

#### Scenario: Catalog lookup fails with filtering enabled
- **WHEN** catalog membership cannot be fetched
- **THEN** no additional package becomes eligible and no update is enqueued from an unrestricted fallback

#### Scenario: Successful empty catalog
- **WHEN** the catalog returns a successful empty package set
- **THEN** the catalog-filtered candidate set is empty rather than an error or unrestricted result

### Requirement: GMS exclusion is limited to the update pipeline
The package `com.google.android.gms` SHALL be excluded from update lookup, update presentation, update notifications and automatic update downloads in every update preference branch. Manual microG discovery and onboarding SHALL remain available.

#### Scenario: GMS appears in an allowed catalog
- **WHEN** an update scan encounters GMS with any catalog preference value
- **THEN** it cannot become an update candidate, notification or automatic download

#### Scenario: Manual microG discovery
- **WHEN** a user explicitly opens the supported microG discovery or onboarding flow
- **THEN** the automated-update exclusion does not block that flow

### Requirement: Catalog reuse is identity scoped
Concurrent consumers SHALL be able to share a successful in-flight catalog fetch for the same identity. Failures SHALL be retryable and identity changes SHALL invalidate access-dependent results.

#### Scenario: Failed request is retried
- **WHEN** a catalog request has failed and a consumer requests a refresh
- **THEN** the consumer is not permanently attached to the previous failed operation
