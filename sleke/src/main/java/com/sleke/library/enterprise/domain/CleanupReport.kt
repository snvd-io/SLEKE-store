package com.sleke.library.enterprise.domain

enum class CleanupStage { ENTERPRISE, PLAY_CACHE, PLAY_ACCOUNTS }

data class CleanupFailure(val stage: CleanupStage, val cause: Throwable)

data class CleanupReport(val failures: List<CleanupFailure> = emptyList()) {
    val isComplete: Boolean get() = failures.isEmpty()
    operator fun plus(other: CleanupReport): CleanupReport =
        CleanupReport(failures + other.failures)
}
