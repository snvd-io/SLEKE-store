package com.sleke.presentation.screens

import kotlinx.serialization.Serializable

internal interface SlekeNavigation {
    @Serializable
    data object SlekeApps : SlekeNavigation

    @Serializable
    data object SlekeEnterprise : SlekeNavigation
}