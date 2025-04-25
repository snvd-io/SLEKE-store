package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Apk(
    val name: String = "",
    val link: String = "",
    val type: String = "",
    val description: String = "",
    val publisher: String = "",
    val versionNameDisplay: String = "",
    val packageName: String = "",
    val iconUrl: String = "",
    val rating: Float = 0f,
    val installs: String = "",
    val isGplayEnriched: Boolean = false,
    val country: String = ""
)