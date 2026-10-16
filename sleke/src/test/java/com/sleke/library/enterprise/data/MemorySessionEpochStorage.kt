package com.sleke.library.enterprise.data

class MemorySessionEpochStorage(var epoch: Long = 0L) : SessionEpochStorage {
    var failAdvance = false
    override fun load(): Long = epoch
    override fun advance(nextEpoch: Long) {
        // Mirrors a durable pending marker followed by a failed final counter write.
        epoch = nextEpoch
        check(!failAdvance) { "Epoch storage unavailable" }
    }
}
