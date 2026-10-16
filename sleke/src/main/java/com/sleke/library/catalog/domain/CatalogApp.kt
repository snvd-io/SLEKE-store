package com.sleke.library.catalog.domain

enum class CatalogScope { Regular, Sleke, Enterprise }

data class CatalogIdentity(val collection: String, val documentId: String)

data class CatalogApp(
    val identity: CatalogIdentity,
    val packageName: String,
    val downloadUrl: String,
    val type: String? = null,
    val country: String = "",
    val title: String = "",
    val description: String = "",
    val publisher: String = "",
    val versionLabel: String = "",
    val iconUrl: String = "",
    val rating: Float = 0f,
    val installs: String = ""
) {
    fun withMetadata(metadata: CatalogMetadata?): CatalogApp = metadata?.let {
        copy(
            title = it.title?.takeIf(String::isNotBlank) ?: title,
            description = it.description?.takeIf(String::isNotBlank) ?: description,
            publisher = it.publisher?.takeIf(String::isNotBlank) ?: publisher,
            versionLabel = it.versionLabel?.takeIf(String::isNotBlank) ?: versionLabel,
            iconUrl = it.iconUrl?.takeIf(String::isNotBlank) ?: iconUrl,
            rating = it.rating ?: rating,
            installs = it.installs?.takeIf(String::isNotBlank) ?: installs
        )
    } ?: this
}
