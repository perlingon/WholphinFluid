package com.github.damontecres.wholphin.ui.nav

import com.github.damontecres.wholphin.R
import org.jellyfin.sdk.model.api.CollectionType

// The tabs of the pages that the nav drawer opens. The drawer shows them as a submenu of a page's
// item before the page is opened, so the pages use these same lists

val MovieLibraryTabs = listOf(R.string.recommended, R.string.library, R.string.collections, R.string.genres)
val TvLibraryTabs = listOf(R.string.recommended, R.string.library, R.string.genres, R.string.studios)
val MusicLibraryTabs = listOf(R.string.recommended, R.string.albums, R.string.artists, R.string.genres, R.string.songs)

// Followed by a tab for each recordings folder, which isn't known until the page loads
val LiveTvTabs = listOf(R.string.tv_guide, R.string.tv_dvr_schedule)

val DiscoverTabs =
    listOf(R.string.discover, R.string.request, R.string.search, R.string.movies_title, R.string.tv_shows_title)

/**
 * The tabs of this drawer item's page that are known before the page is opened, or null when they
 * depend on the page's content (Favorites) or the page has none
 */
val NavDrawerItem.knownTabs: List<Int>?
    get() =
        when (this) {
            NavDrawerItem.Discover -> DiscoverTabs

            is ServerNavDrawerItem ->
                when (type) {
                    CollectionType.MOVIES -> MovieLibraryTabs
                    CollectionType.TVSHOWS -> TvLibraryTabs
                    CollectionType.MUSIC -> MusicLibraryTabs
                    CollectionType.LIVETV -> LiveTvTabs
                    else -> null
                }

            else -> null
        }

/**
 * The page a drawer item opens
 */
val NavDrawerItem.itemDestination: Destination?
    get() =
        when (this) {
            NavDrawerItem.Favorites -> Destination.Favorites
            NavDrawerItem.Discover -> Destination.Discover
            is ServerNavDrawerItem -> destination
            else -> null
        }
