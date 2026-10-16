package com.sleke.library.enterprise.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sleke.library.data.Firestore
import com.sleke.library.enterprise.domain.EnterpriseIdentity
import com.sleke.library.model.firebase.EnterpriseDto
import com.sleke.library.model.firebase.UserDto
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface FirebaseSessionSource {
    val currentIdentity: EnterpriseIdentity?
    val identities: Flow<EnterpriseIdentity?>
    suspend fun membership(): MembershipResult
    suspend fun signOut()
}

sealed interface MembershipResult {
    val identity: EnterpriseIdentity?
    data class Allowed(
        override val identity: EnterpriseIdentity,
        val enterpriseId: String,
        val enterpriseName: String
    ) : MembershipResult
    data class NoMembership(override val identity: EnterpriseIdentity) : MembershipResult
    data class Unavailable(
        override val identity: EnterpriseIdentity?,
        val cause: Throwable
    ) : MembershipResult
}

class DefaultFirebaseSessionSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : FirebaseSessionSource {
    override val currentIdentity: EnterpriseIdentity? get() = auth.currentUser?.let {
        EnterpriseIdentity(it.uid)
    }
    override val identities: Flow<EnterpriseIdentity?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            trySend(firebase.currentUser?.let { EnterpriseIdentity(it.uid) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun membership(): MembershipResult {
        val identity = auth.currentUser?.let { EnterpriseIdentity(it.uid) }
            ?: return MembershipResult.Unavailable(
                null,
                IllegalStateException("No current enterprise identity")
            )
        return try {
            val user = firestore.collection(Firestore.Collection.USERS).document(identity.uid)
                .get().await().toObject(UserDto::class.java)
            val enterpriseId = user?.enterprise?.takeIf { it.isNotBlank() }
                ?: return MembershipResult.NoMembership(identity)
            val enterprise = firestore.collection(
                Firestore.Collection.ENTERPRISE
            ).document(enterpriseId)
                .get().await().toObject(EnterpriseDto::class.java)
            if (enterprise == null) {
                MembershipResult.NoMembership(identity)
            } else {
                MembershipResult.Allowed(identity, enterpriseId, enterprise.title)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            MembershipResult.Unavailable(identity, failure)
        }
    }

    override suspend fun signOut() = auth.signOut()
}
