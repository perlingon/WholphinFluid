package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.mayakapps.kache.InMemoryKache
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/**
 * How many levels of nested collections to look through when a collection has no movie with a backdrop
 */
private const val MAX_NESTED_DEPTH = 3

/**
 * How many child collections to try at each level before giving up
 */
private const val MAX_CHILD_COLLECTIONS = 3

/**
 * How many backdrop lookups run at the same time
 */
private const val IMAGE_CONCURRENCY = 4

@HiltViewModel(assistedFactory = CollectionGridViewModel.Factory::class)
class CollectionGridViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        @Assisted private val itemId: UUID,
        @Assisted private val recursive: Boolean,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: UUID,
                recursive: Boolean,
            ): CollectionGridViewModel
        }

        private val _state = MutableStateFlow(CollectionGridState())
        val state: StateFlow<CollectionGridState> = _state

        private fun collectionsRequest() =
            GetItemsRequest(
                userId = serverRepository.currentUser?.id,
                parentId = itemId,
                recursive = recursive,
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = SlimItemFields,
            )

        fun init(cardWidthPx: Int) {
            _state.update { it.copy(item = DataLoadingState.Loading) }
            viewModelScope.launchIO {
                try {
                    val item =
                        api.userLibraryApi.getItem(itemId = itemId).content.let {
                            BaseItem(it, false)
                        }
                    val collections =
                        GetItemsRequestHandler
                            .execute(api, collectionsRequest())
                            .content.items
                            .map { CollectionCardItem(BaseItem(it, false), null) }
                    val key = CollectionCacheKey(serverRepository.currentUser?.id, itemId)
                    val cached = collectionImageCache.getIfAvailable(key)
                    _state.update {
                        it.copy(
                            item = DataLoadingState.Success(item),
                            collections =
                                if (cached != null) {
                                    collections.map { c -> c.copy(imageUrl = cached[c.id]) }
                                } else {
                                    collections
                                },
                        )
                    }
                    if (cached == null) {
                        loadImages(key, collections.map { it.item.data }, cardWidthPx)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching collections")
                    _state.update { it.copy(item = DataLoadingState.Error(ex)) }
                }
            }
        }

        /**
         * Find a backdrop for every collection, updating the grid as each one arrives
         */
        private suspend fun loadImages(
            key: CollectionCacheKey,
            collections: List<BaseItemDto>,
            cardWidthPx: Int,
        ) {
            val userId = serverRepository.currentUser?.id
            val semaphore = Semaphore(IMAGE_CONCURRENCY)
            val results =
                collections
                    .map { collection ->
                        viewModelScope.async(WholphinDispatchers.IO) {
                            val url =
                                semaphore.withPermit {
                                    try {
                                        val source =
                                            findBackdropItem(userId, collection.id, 0)
                                                ?: collection.takeIf { !it.backdropImageTags.isNullOrEmpty() }
                                        source?.let { backdropUrl(it, cardWidthPx) }
                                    } catch (ex: Exception) {
                                        Timber.w(ex, "Error finding backdrop for collection %s", collection.id)
                                        null
                                    }
                                }
                            if (url != null) {
                                _state.update { st ->
                                    st.copy(
                                        collections =
                                            st.collections.map {
                                                if (it.id == collection.id) it.copy(imageUrl = url) else it
                                            },
                                    )
                                }
                            }
                            collection.id to url
                        }
                    }.awaitAll()
            collectionImageCache.put(key, results.toMap())
        }

        /**
         * Pick a random movie (or other non-collection item) with a backdrop from the collection.
         * If there is none, look inside the collection's child collections instead.
         */
        private suspend fun findBackdropItem(
            userId: UUID?,
            collectionId: UUID,
            depth: Int,
        ): BaseItemDto? {
            val direct =
                GetItemsRequestHandler
                    .execute(
                        api,
                        GetItemsRequest(
                            userId = userId,
                            parentId = collectionId,
                            limit = 1,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            excludeItemTypes = listOf(BaseItemKind.BOX_SET),
                            imageTypes = listOf(ImageType.BACKDROP),
                            imageTypeLimit = 1,
                            enableTotalRecordCount = false,
                        ),
                    ).content.items
                    .firstOrNull()
            if (direct != null || depth >= MAX_NESTED_DEPTH) return direct

            val children =
                GetItemsRequestHandler
                    .execute(
                        api,
                        GetItemsRequest(
                            userId = userId,
                            parentId = collectionId,
                            limit = MAX_CHILD_COLLECTIONS,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            includeItemTypes = listOf(BaseItemKind.BOX_SET),
                            imageTypeLimit = 1,
                            enableTotalRecordCount = false,
                        ),
                    ).content.items
            for (child in children) {
                findBackdropItem(userId, child.id, depth + 1)?.let { return it }
            }
            // Nothing deeper down, so fall back to a child collection's own backdrop
            return children.firstOrNull { !it.backdropImageTags.isNullOrEmpty() }
        }

        private fun backdropUrl(
            item: BaseItemDto,
            cardWidthPx: Int,
        ): String? =
            imageUrlService.getItemImageUrl(
                itemId = item.id,
                itemType = item.type,
                seriesId = null,
                useSeriesForPrimary = true,
                imageType = ImageType.BACKDROP,
                imageTags = item.imageTags.orEmpty(),
                fillWidth = cardWidthPx,
                backdropTags = item.backdropImageTags.orEmpty(),
            )

        suspend fun positionOfLetter(letter: Char): Int =
            withContext(WholphinDispatchers.IO) {
                GetItemsRequestHandler.countMatching(
                    api = api,
                    request =
                        collectionsRequest().copy(
                            nameLessThan = letter.toString(),
                            limit = 0,
                            enableTotalRecordCount = true,
                        ),
                )
            }
    }

data class CollectionGridState(
    val item: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val collections: List<CollectionCardItem> = emptyList(),
)

data class CollectionCacheKey(
    val userId: UUID?,
    val parentId: UUID,
)

private val collectionImageCache by lazy {
    InMemoryKache<CollectionCacheKey, Map<UUID, String?>>(8) {
        expireAfterWriteDuration = 2.hours
    }
}

@Stable
data class CollectionCardItem(
    val item: BaseItem,
    val imageUrl: String?,
) : CardGridItem {
    val id: UUID get() = item.id
    override val gridId: String get() = item.gridId
    override val playable: Boolean = false
    override val sortName: String get() = item.sortName
}

/**
 * Show a grid of collections styled like [GenreCardGrid], using a backdrop from a movie in each collection
 */
@Composable
fun CollectionCardGrid(
    itemId: UUID,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    initialPosition: Int = 0,
    viewModel: CollectionGridViewModel =
        hiltViewModel<CollectionGridViewModel, CollectionGridViewModel.Factory>(
            key = "${itemId}_collection_cards",
            creationCallback = { it.create(itemId, recursive) },
        ),
) {
    val columns = 4
    val spacing = 16.dp
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val cardWidthPx =
        remember(density) {
            with(density) {
                // Same approximation as GenreCardGrid
                (configuration.screenWidthDp.dp - (2 * 16.dp + 3 * spacing))
                    .div(columns)
                    .roundToPx()
            }
        }
    OneTimeLaunchedEffect {
        viewModel.init(cardWidthPx)
    }
    val state by viewModel.state.collectAsState()

    val gridFocusRequester = remember { FocusRequester() }
    when (val st = state.item) {
        DataLoadingState.Pending,
        DataLoadingState.Loading,
        -> {
            LoadingPage(modifier.focusable())
        }

        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier.focusable())
        }

        is DataLoadingState.Success<BaseItem> -> {
            Column(modifier = modifier) {
                if (showTitle) {
                    Text(
                        text = st.data.title ?: "",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.section, vertical = Spacing.md),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                    CardGrid(
                        pager = state.collections,
                        onClickItem = { index, collection ->
                            viewModel.navigationManager.navigateTo(collection.item.destination(index))
                        },
                        onLongClickItem = { _, _ -> },
                        onClickPlay = { _, _ -> },
                        letterPosition = { viewModel.positionOfLetter(it) },
                        gridFocusRequester = gridFocusRequester,
                        showJumpButtons = false,
                        showLetterButtons = true,
                        modifier = Modifier.fillMaxSize(),
                        initialPosition = initialPosition,
                        positionCallback = { columns, position ->
                        },
                        columns = columns,
                        spacing = spacing,
                        cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                            GenreCard(
                                genreId = item?.id,
                                name = item?.item?.name,
                                imageUrl = item?.imageUrl,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                modifier = mod,
                            )
                        },
                    )
                }
            }
        }
    }
}
