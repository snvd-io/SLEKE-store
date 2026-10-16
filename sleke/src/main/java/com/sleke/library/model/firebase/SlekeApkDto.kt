package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SlekeApkDto(
    val name: String = "",
    val link: String = "",
    val packageName: String = "",
    val description: String = "",
    val publisher: String = "",
    val type: String? = null,
    val versionName: String = "",
    val versionNameDisplay: String = ""
)
