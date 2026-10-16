package com.sleke.library.catalog.data

import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.model.firebase.AppDto
import com.sleke.library.model.firebase.EnterpriseAppDto
import com.sleke.library.model.firebase.SlekeApkDto

object CatalogDtoMapper {
    fun map(id: String, dto: AppDto) = CatalogApp(
        CatalogIdentity("apks", id),
        dto.packageName,
        dto.link,
        dto.type,
        dto.country,
        dto.name,
        dto.description,
        dto.publisher,
        dto.versionNameDisplay,
        dto.iconUrl,
        dto.rating,
        dto.installs
    )
    fun map(id: String, dto: SlekeApkDto) = CatalogApp(
        CatalogIdentity("sleke_apps", id),
        dto.packageName,
        dto.link,
        dto.type,
        title = dto.name,
        description = dto.description,
        publisher = dto.publisher,
        versionLabel = dto.versionNameDisplay.ifBlank { dto.versionName }
    )
    fun map(collection: String, id: String, dto: EnterpriseAppDto) = CatalogApp(
        CatalogIdentity(collection, id),
        dto.packageName,
        dto.link,
        dto.type,
        title = dto.name,
        description = dto.description,
        publisher = dto.publisher
    )
}
