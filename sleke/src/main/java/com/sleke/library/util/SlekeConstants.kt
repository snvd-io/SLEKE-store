package com.sleke.library.util

import android.content.Context

object SlekeConstants {
    const val AURORA_USER_AGENT = "com.aurora.store-4.8.1-73"
    const val EXTRA_IS_CUSTOM_STORE = "EXTRA_IS_CUSTOM_STORE"
    
    fun getProviderAuthority(context: Context): String {
        return "${context.packageName}.fileProvider"
    }
}