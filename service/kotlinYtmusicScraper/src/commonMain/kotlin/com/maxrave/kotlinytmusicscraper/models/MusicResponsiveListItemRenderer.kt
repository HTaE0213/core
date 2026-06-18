@file:OptIn(ExperimentalSerializationApi::class)

package com.maxrave.kotlinytmusicscraper.models

import com.maxrave.kotlinytmusicscraper.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.maxrave.kotlinytmusicscraper.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.maxrave.kotlinytmusicscraper.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.maxrave.kotlinytmusicscraper.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Typical list item
 * Used in [MusicCarouselShelfRenderer], [MusicShelfRenderer]
 * Appears in quick picks, search results, table items, etc.
 */
@Serializable
data class MusicResponsiveListItemRenderer(
    val badges: List<Badges>?,
    val fixedColumns: List<FlexColumn>?,
    val flexColumns: List<FlexColumn>,
    val thumbnail: ThumbnailRenderer?,
    val menu: Menu?,
    val playlistItemData: PlaylistItemData?,
    val overlay: Overlay?,
    val navigationEndpoint: NavigationEndpoint?,
    val contributorsAvatars: ContributorsAvatars? = null,
) {
    val isSong: Boolean
        get() = navigationEndpoint == null || navigationEndpoint.watchEndpoint != null || navigationEndpoint.watchPlaylistEndpoint != null
    val isVideo: Boolean
        get() =
            navigationEndpoint
                ?.watchEndpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType != null
    val isPlaylist: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType == MUSIC_PAGE_TYPE_PLAYLIST
    val isAlbum: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType == MUSIC_PAGE_TYPE_ALBUM ||
                navigationEndpoint
                    ?.browseEndpoint
                    ?.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig
                    ?.pageType == MUSIC_PAGE_TYPE_AUDIOBOOK
    val isArtist: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType == MUSIC_PAGE_TYPE_ARTIST

    /**
     * The song/video id. YouTube (web, 2026) dropped [playlistItemData] from search items, so we
     * fall back to the overlay play button and then the first flex column's watch endpoint.
     */
    val videoId: String?
        get() =
            playlistItemData?.videoId
                ?: overlay
                    ?.musicItemThumbnailOverlayRenderer
                    ?.content
                    ?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint
                    ?.watchEndpoint
                    ?.videoId
                ?: flexColumns
                    .firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.navigationEndpoint
                    ?.watchEndpoint
                    ?.videoId

    /**
     * The album/playlist id from the play button. The play endpoint switched from
     * watchPlaylistEndpoint to watchEndpoint in the 2026 web response, so check both.
     */
    val playlistId: String?
        get() =
            overlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.let { it.watchPlaylistEndpoint?.playlistId ?: it.watchEndpoint?.playlistId }

    @Serializable
    data class FlexColumn(
        @JsonNames("musicResponsiveListItemFixedColumnRenderer")
        val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer,
    ) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(
            val text: Runs?,
        ) {
            fun toAlbum(): Album? {
                val run = text?.runs?.firstOrNull()
                if (run != null && isAlbum()) {
                    return Album(
                        name = run.text,
                        id = run.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    )
                }
                return null
            }

            fun toArtist(): Artist? {
                val run = text?.runs?.firstOrNull()
                if (run != null && isArtist()) {
                    return Artist(
                        name = run.text,
                        id = run.navigationEndpoint?.browseEndpoint?.browseId ?: "",
                    )
                }
                return null
            }

            fun isAlbum(): Boolean =
                text
                    ?.runs
                    ?.firstOrNull()
                    ?.navigationEndpoint
                    ?.browseEndpoint
                    ?.isAlbumEndpoint == true

            fun isArtist(): Boolean =
                text
                    ?.runs
                    ?.firstOrNull()
                    ?.navigationEndpoint
                    ?.browseEndpoint
                    ?.isArtistEndpoint == true ||
                    (
                        text
                            ?.runs
                            ?.firstOrNull()
                            ?.navigationEndpoint
                            ?.watchEndpoint == null &&
                            text
                                ?.runs
                                ?.firstOrNull()
                                ?.navigationEndpoint
                                ?.browseEndpoint == null
                    )
        }
    }

    @Serializable
    data class PlaylistItemData(
        val playlistSetVideoId: String?,
        val videoId: String,
    )

    @Serializable
    data class ContributorsAvatars(
        val avatarStackViewModel: AvatarStackViewModel? = null,
    ) {
        @Serializable
        data class AvatarStackViewModel(
            val avatars: List<Avatar>? = null,
            val rendererContext: RendererContext? = null,
        ) {
            @Serializable
            data class Avatar(
                val avatarViewModel: AvatarViewModel? = null,
            ) {
                @Serializable
                data class AvatarViewModel(
                    val image: AvatarImage? = null,
                    val accessibilityText: String? = null,
                ) {
                    @Serializable
                    data class AvatarImage(
                        val sources: List<Source>? = null,
                    ) {
                        @Serializable
                        data class Source(
                            val url: String? = null,
                        )
                    }
                }
            }

            @Serializable
            data class RendererContext(
                val commandContext: CommandContext? = null,
            ) {
                @Serializable
                data class CommandContext(
                    val onTap: OnTap? = null,
                ) {
                    @Serializable
                    data class OnTap(
                        val innertubeCommand: InnertubeCommand? = null,
                    ) {
                        @Serializable
                        data class InnertubeCommand(
                            val browseEndpoint: BrowseEndpoint? = null,
                        )
                    }
                }
            }
        }
    }

    @Serializable
    data class Overlay(
        val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer,
    ) {
        @Serializable
        data class MusicItemThumbnailOverlayRenderer(
            val content: Content,
        ) {
            @Serializable
            data class Content(
                val musicPlayButtonRenderer: MusicPlayButtonRenderer,
            ) {
                @Serializable
                data class MusicPlayButtonRenderer(
                    val playNavigationEndpoint: NavigationEndpoint?,
                )
            }
        }
    }
}
