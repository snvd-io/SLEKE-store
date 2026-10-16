package com.aurora.store.usecase

import com.sleke.integration.auth.SignOutCoordinator
import com.sleke.integration.auth.SignOutScope
import com.sleke.library.enterprise.domain.CleanupReport
import javax.inject.Inject

class LogoutUseCase @Inject constructor(private val coordinator: SignOutCoordinator) {
    suspend operator fun invoke(scope: SignOutScope): CleanupReport = coordinator.signOut(scope)
}
