package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SlekeApkDto(
    val name: String = "",
    val link: String = "",
    val packageName: String = "",
)

fun SlekeApkDto.toApk(): Apk {
    return Apk(
        name = name,
        link = link,
        type = "apk",
        description = "",
        publisher = "",
        versionNameDisplay = "",
        packageName = "",
    )
}