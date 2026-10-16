package com.sleke.integration.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sleke.library.data.datastore.SlekePreferencesDataStoreImpl
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Local-only compatibility check; never contacts Firebase. */
@RunWith(AndroidJUnit4::class)
class EnterpriseSessionStorageTest {
    @Test fun readsExistingPreferenceKeysAndClearsOnlyEnterpriseFields() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "enterprise-storage-${System.nanoTime()}").apply {
            mkdirs()
        }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = directory
        }
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val seed = PreferenceDataStoreFactory.create(scope = seedScope) {
            context.preferencesDataStoreFile("sleke_prefs")
        }
        seed.edit {
            it[stringPreferencesKey("signed_uid")] = "legacy-current"
            it[stringPreferencesKey("signed_email")] = "person@example.test"
            it[booleanPreferencesKey("is_enterprise")] = true
            it[stringPreferencesKey("unrelated")] = "preserved"
        }
        seedScope.cancel()
        seedScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = SlekePreferencesDataStoreImpl(context, owner)
            val account = preferences.signedAccount.first()!!
            assertEquals("legacy-current", account.signedUid)
            assertEquals("person@example.test", account.signedEmail)
            assertEquals(true, account.isEnterprise)
            preferences.clean()
            assertNull(preferences.signedAccount.first())
            owner.cancel()
            owner.coroutineContext[kotlinx.coroutines.Job]!!.join()
            val verifyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val raw = PreferenceDataStoreFactory.create(scope = verifyScope) {
                    context.preferencesDataStoreFile("sleke_prefs")
                }
                assertEquals("preserved", raw.data.first()[stringPreferencesKey("unrelated")])
            } finally {
                verifyScope.cancel()
                verifyScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            }
        } finally {
            owner.cancel()
            owner.coroutineContext[kotlinx.coroutines.Job]!!.join()
            directory.deleteRecursively()
        }
    }
}
