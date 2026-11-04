package com.github.damontecres.wholphin.ui.util

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R

/**
 * A kind of library that has a common default name ("Movies", "Shows"…), which is shown in the
 * app's language in place of the name the server has for it
 */
enum class LibraryKind(
    @param:StringRes val nameRes: Int,
) {
    MOVIES(R.string.library_movies),
    SHOWS(R.string.library_shows),
    MUSIC(R.string.library_music),
}

private val libraryKindsByName =
    mapOf(
        "movies" to LibraryKind.MOVIES,
        "films" to LibraryKind.MOVIES,
        "film" to LibraryKind.MOVIES,
        "filmer" to LibraryKind.MOVIES,
        "shows" to LibraryKind.SHOWS,
        "tv shows" to LibraryKind.SHOWS,
        "tv-shows" to LibraryKind.SHOWS,
        "tv" to LibraryKind.SHOWS,
        "series" to LibraryKind.SHOWS,
        "serier" to LibraryKind.SHOWS,
        "music" to LibraryKind.MUSIC,
        "musik" to LibraryKind.MUSIC,
    )

/**
 * The [LibraryKind] of a library with this name, or null if it's a name of its own
 */
fun libraryKindOf(name: String?): LibraryKind? = name?.let { libraryKindsByName[it.trim().lowercase()] }

// Titles that read better as a phrase of their own than as e.g. "Recently added in Movies"
private val libraryRowTitles =
    mapOf(
        (R.string.recently_added_in to LibraryKind.MOVIES) to R.string.recently_added_movies,
        (R.string.recently_added_in to LibraryKind.SHOWS) to R.string.recently_added_shows,
        (R.string.recently_added_in to LibraryKind.MUSIC) to R.string.recently_added_music,
        (R.string.recently_released_in to LibraryKind.MOVIES) to R.string.recently_released_movies,
        (R.string.recently_released_in to LibraryKind.SHOWS) to R.string.recently_released_shows,
        (R.string.recently_released_in to LibraryKind.MUSIC) to R.string.recently_released_music,
        (R.string.genres_in to LibraryKind.MOVIES) to R.string.genres_movies,
        (R.string.genres_in to LibraryKind.SHOWS) to R.string.genres_shows,
        (R.string.genres_in to LibraryKind.MUSIC) to R.string.genres_music,
        (R.string.studios_in to LibraryKind.MOVIES) to R.string.studios_movies,
        (R.string.studios_in to LibraryKind.SHOWS) to R.string.studios_shows,
        (R.string.studios_in to LibraryKind.MUSIC) to R.string.studios_music,
    )

/**
 * The title of a row for a library, from a string like "Recently added in %1$s": for a library
 * with a default name it's a phrase of its own ("Recently added movies") or uses the name in the
 * app's language, otherwise it uses the library's name
 */
fun libraryRowTitle(
    @StringRes stringRes: Int,
    libraryName: String,
): StringProvider {
    val kind = libraryKindOf(libraryName) ?: return ResArgStringProvider(stringRes, libraryName)
    libraryRowTitles[stringRes to kind]?.let { return ResStringProvider(it) }
    return ResProviderStringProvider(stringRes, ResStringProvider(kind.nameRes))
}
