package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties
import com.sleke.library.domain.AppDomain

@IgnoreExtraProperties
data class EnterpriseAppDto(
    val name: String = "",
    val description: String = "",
    val link: String = "",
    val packageName: String = "",
    val publisher: String = "",
    val type: String? = null,
)

fun EnterpriseAppDto.toApp(): AppDomain {
    return AppDomain(
        name = name,
        description = description,
        downloadUrl = link,
        packageName = packageName,
        publisher = publisher,
        type = type
    )
} 