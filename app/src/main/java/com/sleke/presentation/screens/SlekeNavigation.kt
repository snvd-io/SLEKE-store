package com.sleke.presentation.screens

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

internal interface SlekeNavigation : NavKey {
    @Serializable
    data object SlekeApps : SlekeNavigation

    @Serializable
    data object SlekeEnterprise : SlekeNavigation
}