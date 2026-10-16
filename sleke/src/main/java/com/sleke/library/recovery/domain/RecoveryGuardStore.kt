package com.sleke.library.recovery.domain

interface RecoveryGuardStore {
    fun read(): Long
    fun record(timestampMs: Long)
    fun clear()
}
