package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties
import com.sleke.library.domain.AppDomain

@IgnoreExtraProperties
data class SlekeApkDto(
    val name: String = "",
    val link: String = "",
    val packageName: String = "",
    val description: String = "",
    val publisher: String = "",
    val type: String? = null,
    val versionName: String = "",
    val versionNameDisplay: String = "",
)

fun SlekeApkDto.toApp(): AppDomain {
    return AppDomain(
        name = name,
        downloadUrl = this@toApp.link,
        type = type,
        description = description,
        publisher = publisher,
        version = versionNameDisplay.ifEmpty { versionName },
        packageName = packageName,
    )
}