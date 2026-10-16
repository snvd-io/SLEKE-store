package com.sleke.library.enterprise.domain

enum class EntryDestination { Wait, RegularCatalog, EnterpriseCatalog }

object EnterpriseEntryPolicy {
    fun destination(session: EnterpriseSessionState, playReady: Boolean): EntryDestination = when {
        !playReady -> EntryDestination.Wait
        session is EnterpriseSessionState.Allowed -> EntryDestination.EnterpriseCatalog
        session is EnterpriseSessionState.NoMembership ||
            session is EnterpriseSessionState.Unavailable ->
            EntryDestination.RegularCatalog
        else -> EntryDestination.Wait
    }
}
