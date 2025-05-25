package com.sleke.library.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class EnterpriseDto(
    val title: String = "",
    val description: String = "",
) 