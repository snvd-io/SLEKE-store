package com.sleke.store.data.model

data class SessionInfo(
    val sessionId: Int,
    val packageName: String,
    val versionCode: Int,
    val displayName: String = String()
)
