package com.sleke.integration.recovery

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aurora.store.util.Preferences
import com.sleke.library.recovery.domain.RecoveryGuardStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class PreferenceRecoveryGuardStore(
    private val preferences: SharedPreferences
) : RecoveryGuardStore {
    @Inject
    constructor(@ApplicationContext context: Context) : this(Preferences.getPrefs(context))

    override fun read(): Long = preferences.getLong(Preferences.PREFERENCE_ERROR_RESTART_TS, 0L)

    override fun record(timestampMs: Long) {
        preferences.edit { putLong(Preferences.PREFERENCE_ERROR_RESTART_TS, timestampMs) }
    }

    override fun clear() = record(0L)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RecoveryModule {
    @Binds
    abstract fun recoveryGuardStore(store: PreferenceRecoveryGuardStore): RecoveryGuardStore
}
