package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SlekeApkDto(
    val name: String = "",
    val link: String = "",
    val packageName: String = "",
    val description: String = "",
    val publisher: String = "",
    val type: String = "",
    val versionName: String = "",
    val versionNameDisplay: String = "",
)

fun SlekeApkDto.toApk(): Apk {
    return Apk(
        name = name,
        link = this@toApk.link,
        type = type.ifEmpty { "apk" },
        description = description,
        publisher = publisher,
        versionNameDisplay = versionNameDisplay.ifEmpty { versionName },
        packageName = packageName,
    )
}