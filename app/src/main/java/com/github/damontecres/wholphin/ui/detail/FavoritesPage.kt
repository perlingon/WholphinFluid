package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.rememberContextMenu
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState

@Composable
fun FavoritesPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect { viewModel.init() }
    val state by viewModel.state.collectAsState()

    when (val s = state.loadingState) {
        is FavoritesLoadingState.Error -> {
            ErrorMessage(s.message, s.exception, modifier)
        }

        FavoritesLoadingState.Loading,
        FavoritesLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        FavoritesLoadingState.NoFavorites -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    modifier
                        .focusRequester(focusRequester)
                        .focusable(),
            ) {
                Text(
                    text = stringResource(R.string.no_favorites_found),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier,
                )
            }
        }

        FavoritesLoadingState.Success -> {
            FavoritesGroupedGrid(
                preferences = preferences,
                state = state,
                viewModel = viewModel,
                modifier = modifier,
            )
        }
    }
}


/**
 * All favourites on one page, grouped under a heading per type (Movies, Shows, Episodes...), the
 * way grouped collections are shown
 */
@Composable
private fun FavoritesGroupedGrid(
    preferences: UserPreferences,
    state: FavoritesPageState,
    viewModel: FavoritesViewModel,
    modifier: Modifier = Modifier,
) {
    // Gap between one type's cards and the next type's heading
    val groupGap = Spacing.lg
    val spacing = Spacing.md

    // The types that have favourites, in display order, with what's loaded for each
    val groups =
        state.tabs.keys.mapNotNull { type ->
            val folder = state.favorites[type] ?: return@mapNotNull null
            val items = (folder.items as? DataLoadingState.Success<List<BaseItem?>>)?.data
            if (items.isNullOrEmpty()) null else Triple(type, folder.viewOptions, items)
        }
    // Context menu actions depend on the type
    val types = groups.map { it.first }
    val providers = remember(types) { types.associateWith { viewModel.createTypedProvider(it) } }
    val contextMenus =
        providers.mapValues { (type, provider) ->
            key(type) { rememberContextMenu(preferences, provider) }
        }
    // Each type keeps its own number of columns, so the grid is split into enough slots for all of them
    val gridSlots =
        remember(groups.map { it.second.columns }) {
            groups.fold(1) { slots, group -> lcm(slots, group.second.columns.coerceAtLeast(1)) }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridSlots),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
        modifier =
            modifier
                .fillMaxSize()
                .focusGroup(),
    ) {
        groups.forEachIndexed { groupIndex, (type, viewOptions, groupItems) ->
            if (groupIndex > 0) {
                item(key = "group_gap_$type", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(groupGap))
                }
            }
            item(key = "group_title_$type", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(formatTypeName(type)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            val span = gridSlots / viewOptions.columns.coerceAtLeast(1)
            items(
                groupItems.size,
                key = { "${type}_$it" },
                span = { GridItemSpan(span) },
            ) { index ->
                val item = groupItems[index]
                GridCard(
                    item = item,
                    onClick = { item?.let { viewModel.navigationManager.navigateTo(it.destination()) } },
                    onLongClick = { item?.let { contextMenus[type]?.showContextMenu(index, it) } },
                    imageContentScale = viewOptions.contentScale.scale,
                    imageAspectRatio = viewOptions.aspectRatio.ratio,
                    imageType = viewOptions.imageType,
                    showTitle = viewOptions.showTitles,
                    modifier =
                        Modifier
                            .ifElse(groupIndex == 0 && index == 0, Modifier.focusRequester(focusRequester))
                            .onFocusChanged {
                                if (it.isFocused && item != null && viewOptions.showBackdrop) {
                                    providers[type]?.updateBackdrop(item)
                                }
                            },
                )
            }
        }
    }
    contextMenus.values.forEach { it.Compose() }
}

private tailrec fun gcd(
    a: Int,
    b: Int,
): Int = if (b == 0) a else gcd(b, a % b)

private fun lcm(
    a: Int,
    b: Int,
): Int = a / gcd(a, b) * b
