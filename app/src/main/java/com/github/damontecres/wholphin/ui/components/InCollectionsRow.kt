package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CollectionTreeService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.theme.FluidLayout
import com.github.damontecres.wholphin.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * A collection that an item is in, as shown in [InCollectionsRow]
 */
data class InCollection(
    val item: BaseItem,
    val imageUrl: String?,
)

@HiltViewModel
class InCollectionsViewModel
    @Inject
    constructor(
        private val imageUrlService: ImageUrlService,
        private val collectionTreeService: CollectionTreeService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _collections = MutableStateFlow<List<InCollection>>(emptyList())
        val collections: StateFlow<List<InCollection>> = _collections

        /**
         * Find the collections that directly hold [itemId], from the saved collection tree
         */
        fun init(
            itemId: UUID,
            cardWidthPx: Int,
        ) {
            viewModelScope.launchIO {
                try {
                    collectionTreeService.get()
                    collectionTreeService.refreshIfOlderThan(CollectionTreeService.PAGE_REFRESH_AGE)
                    // Updated when the tree is rebuilt in the background
                    collectionTreeService.tree.filterNotNull().collect { tree ->
                        _collections.value =
                            tree.collections.values
                                .filter { entry -> entry.children.any { it.id == itemId } }
                                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.dto.name.orEmpty() })
                                .map { entry ->
                                    InCollection(
                                        item = BaseItem(entry.dto, false),
                                        imageUrl =
                                            entry.backdropItem?.let { backdrop ->
                                                imageUrlService.getItemImageUrl(
                                                    itemId = backdrop.id,
                                                    itemType = backdrop.type,
                                                    seriesId = null,
                                                    useSeriesForPrimary = true,
                                                    imageType = ImageType.BACKDROP,
                                                    imageTags = backdrop.imageTags.orEmpty(),
                                                    fillWidth = cardWidthPx,
                                                    backdropTags = backdrop.backdropImageTags.orEmpty(),
                                                )
                                            },
                                    )
                                }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error finding collections for %s", itemId)
                }
            }
        }
    }

/**
 * "In collection": the collections that a movie or series is in, as cards like on the Collections
 * page. Shows nothing if it isn't in any
 */
@Composable
fun InCollectionsRow(
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: InCollectionsViewModel = hiltViewModel(key = "in_collections_$itemId"),
) {
    // Width of a card, a wide card at the height of the page's other wide cards
    val cardWidth = 228.dp
    val cardWidthPx = with(LocalDensity.current) { cardWidth.roundToPx() }
    LaunchedEffect(itemId) { viewModel.init(itemId, cardWidthPx) }
    val collections by viewModel.collections.collectAsState()
    if (collections.isEmpty()) return

    val firstFocus = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        ItemRowTitle(stringResource(R.string.in_collection))
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.card),
            contentPadding =
                PaddingValues(
                    // The first card lines up with the headlines; the rest scroll full width
                    start = FluidLayout.rowStart(Spacing.card),
                    end = Spacing.card,
                    top = Spacing.sm,
                    bottom = Spacing.sm,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(collections, key = { _, it -> it.item.id }) { index, collection ->
                GenreCard(
                    genreId = collection.item.id,
                    name = collection.item.name,
                    imageUrl = collection.imageUrl,
                    onClick = { viewModel.navigationManager.navigateTo(collection.item.destination()) },
                    onLongClick = {},
                    aspectRatio = AspectRatios.WIDE,
                    modifier =
                        Modifier
                            .width(cardWidth)
                            .ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}
