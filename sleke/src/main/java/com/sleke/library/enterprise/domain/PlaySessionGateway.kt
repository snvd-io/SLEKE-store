package com.sleke.library.enterprise.domain

interface PlaySessionGateway {
    suspend fun prepareAnonymous(): PlayPreparation
    suspend fun resetCachedAuth(): CleanupReport
    suspend fun removeAllAccounts(): CleanupReport
}

sealed interface PlayPreparation {
    data object Ready : PlayPreparation
    data class Failed(val cause: Throwable) : PlayPreparation
}
