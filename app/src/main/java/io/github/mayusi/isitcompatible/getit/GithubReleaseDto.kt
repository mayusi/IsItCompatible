package io.github.mayusi.isitcompatible.getit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shared DTOs for the GitHub Releases API response. Used by both
 * [AppUpdateChecker] (reads name/body for release notes + SHA256) and
 * [io.github.mayusi.isitcompatible.getit.source.GitHubReleasesSource]
 * (reads assets to pick the right APK). All fields are nullable/optional so
 * both use sites work with a single superset DTO.
 */
@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhAsset(
    val name: String,
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
