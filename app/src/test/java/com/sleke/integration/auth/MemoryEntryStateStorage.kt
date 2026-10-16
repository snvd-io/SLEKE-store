package com.sleke.integration.auth

class MemoryEntryStateStorage : EntryStateStorage {
    override var intent = EntryIntent(null, null)
    override var pending: EntryNavigation? = null
    override var sequence = 0L
    override var providerPending = false
    override var providerEpoch: Long? = null
    override var completedEpoch: Long? = null
    override var pendingEpoch: Long? = null
}
