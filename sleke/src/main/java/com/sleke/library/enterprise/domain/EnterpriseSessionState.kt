package com.sleke.library.enterprise.domain

data class EnterpriseIdentity(val uid: String)

sealed interface EnterpriseSessionState {
    data object Restoring : EnterpriseSessionState
    data object SignedOut : EnterpriseSessionState
    data class Checking(val identity: EnterpriseIdentity) : EnterpriseSessionState
    data class Allowed(
        val identity: EnterpriseIdentity,
        val enterpriseId: String,
        val enterpriseName: String
    ) : EnterpriseSessionState
    data class NoMembership(val identity: EnterpriseIdentity) : EnterpriseSessionState
    data class Unavailable(val identity: EnterpriseIdentity) : EnterpriseSessionState
}
