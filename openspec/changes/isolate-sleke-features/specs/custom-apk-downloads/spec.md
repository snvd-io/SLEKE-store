# Custom APK downloads

## Purpose

Let SLEKE users request, observe and cancel a validated APK operation with a controlled handoff to their preferred installer, independently of screen observation.

This is a draft contract. Requested-install continuation, consistent preferred-installer handling for updates/manual retries and cancellation of unsubmitted enterprise work on access loss were confirmed on 16 October 2026. The complete architecture remains a draft for review.

## ADDED Requirements

### Requirement: Start returns the actual operation
Repeated starts for one package SHALL return the retained active operation handle rather than a new handle that was never scheduled. Separate operations SHALL not overwrite each other's state.

#### Scenario: Duplicate starts retain existing work
- **WHEN** two callers request the same package while an operation is active
- **THEN** they observe the same actual active operation and only one transfer runs

### Requirement: Observation is free of installation effects
Recollecting operation state SHALL not start transfer, staging or installation. Terminal observation SHALL release its owned resources and support later reattachment without repeating effects.

#### Scenario: Screen is recreated
- **WHEN** a screen observes an already completed or installation-submitted operation again
- **THEN** its current state is restored without a second installer submission

### Requirement: Only a validated artifact reaches installation
The operation SHALL require a successful HTTP response, parseable APK metadata and equality between the requested and actual package name before publishing an installable artifact.

#### Scenario: Download returns a different package
- **WHEN** the requested package is `a.b` but the APK identifies itself as `x.y`
- **THEN** the operation fails, removes its incomplete output and never invokes an installer

#### Scenario: Response is invalid
- **WHEN** the response is unsuccessful, the archive is invalid or required metadata is missing
- **THEN** the operation fails without publishing an installable artifact

### Requirement: Cancellation remains cancellation
Cancelling a transfer SHALL propagate cancellation, clean up its incomplete files and prevent later success or install dispatch from the cancelled operation.

#### Scenario: User cancels during transfer
- **WHEN** cancellation arrives before artifact validation completes
- **THEN** the operation reaches cancelled state, its partial file is removed and no installer is called

### Requirement: Staging has explicit artifact ownership
Staging SHALL run away from the UI thread, submit only the validated operation's artifacts and retain files needed for pending confirmation. It SHALL not delete or include another operation's files.

#### Scenario: Another download owns the same package directory
- **WHEN** a custom APK is staged while other artifacts exist for that package/version
- **THEN** unrelated split files are neither submitted nor removed by the custom operation

### Requirement: Preferred-installer fallback is controlled
The operation SHALL use the selected available installer and retain the existing Session fallback for an unavailable selection. Synchronous submission failure SHALL permit one system-prompt fallback. Asynchronous failure or user rejection SHALL not automatically submit a second installer request.

#### Scenario: Selected installer is unavailable
- **WHEN** the preferred privileged installer cannot be used
- **THEN** the existing Session fallback handles the request and reports any required confirmation

#### Scenario: User rejects an accepted request
- **WHEN** the platform reports user rejection after accepting a request
- **THEN** the operation reports rejection without opening another installer automatically

### Requirement: Requested installation survives screen lifecycle
An explicitly requested custom installation SHALL continue after screen departure or process recreation. Recovery SHALL reconcile accepted external requests before resubmission and SHALL not initiate unrelated installs.

#### Scenario: Download finishes after navigation
- **WHEN** the user leaves the requesting screen before a requested download completes
- **THEN** the validated artifact reaches the preferred installer once through its operation owner, retaining platform confirmation when required

#### Scenario: Process stops near handoff
- **WHEN** the process restarts after an installer may already have accepted the artifact
- **THEN** recovery checks that handoff before deciding whether another submission is necessary

### Requirement: Installed versions and manual retries use one path
A requested newer APK SHALL be eligible when an older version is installed, and manual retries SHALL use the preferred-installer path. An equal or newer installed version SHALL not be reinstalled automatically.

#### Scenario: Older version is already installed
- **WHEN** a validated requested APK has a higher version than the installed package
- **THEN** the operation can submit the update through the preferred installer

#### Scenario: Manual installation is retried
- **WHEN** the user explicitly retries an installable operation
- **THEN** the same validation, ownership and preferred-installer rules apply

### Requirement: Enterprise operations follow confirmed access loss
Sign-out, identity change or confirmed revocation SHALL cancel unfinished enterprise operations and block unsubmitted installation. Already accepted requests SHALL be reconciled; installed apps SHALL not be uninstalled as a sign-out side effect.

#### Scenario: Enterprise identity changes before handoff
- **WHEN** an operation's enterprise identity is no longer current before installation submission
- **THEN** its unfinished work is cancelled and unsubmitted artifacts for that identity are purged

#### Scenario: Android already accepted installation
- **WHEN** access ends after an owned installer request was accepted
- **THEN** supported cancellation is attempted for that owned pending request and the actual platform outcome is reported without claiming an automatic undo

### Requirement: Temporary access outage blocks new private handoffs
A temporary membership outage SHALL block new enterprise download requests and new enterprise install handoffs without being classified as confirmed revocation. A private validated artifact SHALL remain scoped to its owner while an explicit access refresh is pending.

#### Scenario: Membership refresh is temporarily unavailable
- **WHEN** an enterprise artifact is validated but membership cannot currently be confirmed
- **THEN** no new installer handoff occurs until the same identity's access is refreshed successfully
