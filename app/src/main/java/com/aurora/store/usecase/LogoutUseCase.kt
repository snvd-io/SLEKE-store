package com.aurora.store.usecase

import com.sleke.library.enterprise.data.SignOutCoordinator
import com.sleke.library.enterprise.data.SignOutScope
import com.sleke.library.enterprise.domain.CleanupReport
import javax.inject.Inject

class LogoutUseCase @Inject constructor(private val coordinator: SignOutCoordinator) {
    suspend operator fun invoke(scope: SignOutScope): CleanupReport = coordinator.signOut(scope)
}
