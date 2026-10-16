package com.sleke.library.enterprise.domain

import kotlinx.coroutines.flow.StateFlow

interface EnterpriseSessionRepository {
    val state: StateFlow<EnterpriseSessionState>
    suspend fun revokeAccess()
    suspend fun refresh(): EnterpriseSessionState
    suspend fun signOut(): CleanupReport
}
