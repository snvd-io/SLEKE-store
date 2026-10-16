package com.sleke.integration.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.store.data.AccountRepository
import com.aurora.store.data.model.AccountType
import com.aurora.store.data.room.AuroraDatabase
import com.aurora.store.data.room.account.Account
import com.aurora.store.data.room.account.AppAccountBinding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Room deletion/default promotion without touching the application's database. */
@RunWith(AndroidJUnit4::class)
class ScopedSignOutTest {
    @Test fun removingOneAccountClearsItsBindingsAndPromotesAnonymous() = runBlocking {
        withAccounts { repository, database ->
            repository.removeAccount("person")
            assertNull(database.appAccountBindingDao().getForPackage("com.example.private"))
            assertEquals(Account.ANONYMOUS_ID, repository.getDefault()?.id)
            assertEquals(1, repository.getAll().size)
        }
    }

    @Test fun removingAllAccountsLeavesNoBindingsOrDefault() = runBlocking {
        withAccounts { repository, database ->
            repository.getAll().forEach { repository.removeAccount(it.id) }
            assertEquals(emptyList<Account>(), repository.getAll())
            assertNull(repository.getDefault())
            assertNull(database.appAccountBindingDao().getForPackage("com.example.private"))
            assertNull(database.appAccountBindingDao().getForPackage("com.example.public"))
        }
    }

    private suspend fun withAccounts(block: suspend (AccountRepository, AuroraDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AuroraDatabase::class.java).build()
        try {
            val repository =
                AccountRepository(database.accountDao(), database.appAccountBindingDao())
            repository.upsertAccount(
                Account("person", AccountType.GOOGLE, "person@example.test", isDefault = true)
            )
            repository.upsertAccount(
                Account(Account.ANONYMOUS_ID, AccountType.ANONYMOUS, "anonymous@example.test")
            )
            database.appAccountBindingDao().upsert(
                AppAccountBinding("com.example.private", "person")
            )
            database.appAccountBindingDao().upsert(
                AppAccountBinding("com.example.public", Account.ANONYMOUS_ID)
            )
            block(repository, database)
        } finally {
            database.close()
        }
    }
}
