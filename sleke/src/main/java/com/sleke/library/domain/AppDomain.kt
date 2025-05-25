package com.sleke.library.domain

data class AppDomain(
    val name: String,
    val description: String,
    val downloadUrl: String,
    val packageName: String,
    val publisher: String,
    val type: String? = null,
    val version: String? = null,
)
