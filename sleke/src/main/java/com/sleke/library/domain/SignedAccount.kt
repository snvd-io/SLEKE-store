package com.sleke.library.domain

data class SignedAccount(
    val signedUid: String = "",
    val signedEmail: String = "",
    val isEnterprise: Boolean = false
)
