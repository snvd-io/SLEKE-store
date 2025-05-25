package com.sleke.library.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObjects
import com.sleke.library.data.Firestore
import com.sleke.library.domain.AppDomain
import com.sleke.library.model.firebase.AppDto
import com.sleke.library.model.firebase.EnterpriseAppDto
import com.sleke.library.model.firebase.SlekeApkDto
import com.sleke.library.model.firebase.UserDto
import com.sleke.library.model.firebase.toApp
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreApkRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) : ApkRepository {

    override suspend fun slekeApks(): List<SlekeApkDto> {
        val snapshot = firestore.collection(Firestore.Collection.SLEKE_APPS).get().await()
        return snapshot.toObjects(SlekeApkDto::class.java)
    }

    override suspend fun getAllApks(): List<AppDto> {
        val snapshot = firestore.collection("apks").orderBy("name").get().await()
        return snapshot.toObjects(AppDto::class.java)
    }

    override suspend fun hasEnterpriseAccess(userId: String): Boolean {
        return try {
            val user = firestore.collection(Firestore.Collection.USERS)
                .document(userId)
                .get()
                .await()
                .toObject(UserDto::class.java)

            Timber.tag("FBRepository")
                .d("User: $user, Enterprise: ${user?.enterprise}")

            user?.enterprise?.isNotBlank() ?: false
        } catch (e: Exception) {
            Timber.tag("FBRepository").w(e, "Error checking enterprise access for user: $userId")
            false
        }
    }

    override suspend fun getEnterpriseApps(userId: String): List<AppDomain> {
        try {
            val user = firestore.collection(Firestore.Collection.USERS)
                .document(userId)
                .get()
                .await()
                .toObject(UserDto::class.java)

            if (user == null || user.enterprise.isNullOrBlank()) {
                Timber.tag("FBRepository")
                    .d("User not found or no enterprise access for user: $userId")
                return emptyList()
            }

            val enterpriseApps = firestore
                .collection(Firestore.Collection.ENTERPRISE)
                .document(user.enterprise)
                .collection(Firestore.Collection.ENTERPRISE_APPS)
                .get().await().toObjects<EnterpriseAppDto>()

            if (enterpriseApps.isEmpty()) {
                Timber.tag("FBRepository")
                    .d("No enterprise apps found for user: $userId in enterprise: ${user.enterprise}")
                return emptyList()
            }

            return enterpriseApps.map { appDto ->
                appDto.toApp()
            }

        } catch (e: Exception) {
            Timber.tag("FBRepository").w(e, "Error fetching enterprise apps for user: $userId")
            return emptyList()
        }
    }
} 