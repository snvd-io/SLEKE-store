# App details recovery

## Purpose

Preserve the existing guarded recovery sequence for app-details load failures while separating the recovery decision and stored guard from upstream view-model implementation.

## ADDED Requirements

### Requirement: First ordinary failure retries in place
An ordinary app-details load failure SHALL receive one silent in-place retry, including when a recent restart guard exists. The existing one-second retry delay SHALL be retained.

#### Scenario: First load attempt fails
- **WHEN** no in-place retry has been attempted for this load
- **THEN** the app retries the same package after the existing delay without restarting

### Requirement: Repeated failure uses the restart guard
After the retry fails, the app SHALL restart when no recent restart exists. A repeated failure less than 60 seconds after a restart SHALL show the existing recovery message. A restart exactly 60 seconds old SHALL be treated as stale.

#### Scenario: Retry fails inside the guard window
- **WHEN** a retry fails and the persisted restart is less than 60 seconds old
- **THEN** the app shows the recovery message instead of restarting again

#### Scenario: Guard reaches its boundary
- **WHEN** a retry fails and the persisted restart is exactly 60 seconds old
- **THEN** another restart is permitted

### Requirement: Successful load clears the guard
A successful app-details load SHALL clear the persisted restart guard and retain the existing preference key for upgrade compatibility.

#### Scenario: Load succeeds after recovery
- **WHEN** app details are successfully loaded
- **THEN** an old restart timestamp cannot affect a later unrelated load

### Requirement: Authentication failure follows upstream reauthentication
An expired Play session SHALL retain the upstream reauthentication route and requested-package return intent rather than entering the ordinary restart recovery sequence.

#### Scenario: Play rejects the session
- **WHEN** app details return an authentication failure
- **THEN** the host revalidates the Play session and retains the package to reopen afterward
