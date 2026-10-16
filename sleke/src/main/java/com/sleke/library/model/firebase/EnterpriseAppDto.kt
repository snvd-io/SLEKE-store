package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class EnterpriseAppDto(
    val name: String = "",
    val description: String = "",
    val link: String = "",
    val packageName: String = "",
    val publisher: String = "",
    val type: String? = null
)
