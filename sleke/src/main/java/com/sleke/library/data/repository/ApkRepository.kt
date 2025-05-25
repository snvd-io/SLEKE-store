package com.sleke.library.data.repository

import com.sleke.library.domain.AppDomain
import com.sleke.library.model.firebase.AppDto
import com.sleke.library.model.firebase.EnterpriseDto
import com.sleke.library.model.firebase.SlekeApkDto

interface ApkRepository {
    suspend fun slekeApks(): List<SlekeApkDto>
    suspend fun getAllApks(): List<AppDto>

    suspend fun hasEnterpriseAccess(userId: String): Boolean
    suspend fun getEnterpriseApps(userId: String): List<AppDomain>
    suspend fun getEnterpriseDetails(userId: String): EnterpriseDto?
}