package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.ifElse
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
        @Assisted("recursive") private val recursive: Boolean,
        @Assisted("emojiFirst") private val emojiFirst: Boolean,
        @Assisted("groupNested") private val groupNested: Boolean,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: UUID,
                @Assisted("recursive") recursive: Boolean,
                @Assisted("emojiFirst") emojiFirst: Boolean,
                @Assisted("groupNested") groupNested: Boolean,
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
                    val all =
                        GetItemsRequestHandler
                            .execute(api, collectionsRequest())
                            .content.items
                    val parents = if (groupNested) parentCollections(all) else emptyMap()
                    // Emoji collections first, then the rest. Both A-Z, ignoring the leading emoji
                    val byName =
                        compareByDescending<CollectionCardItem> { it.isEmoji }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortName.withoutLeadingEmoji() }
                    val allSorted =
                        all
                            .map {
                                CollectionCardItem(
                                    item = BaseItem(it, false),
                                    imageUrl = null,
                                    parentId = parents[it.id],
                                    isEmoji = emojiFirst && it.name.startsWithEmoji(),
                                )
                            }
                            .sortedWith(byName)
                    val collections = layoutGroups(allSorted)
                    val key = CollectionCacheKey(serverRepository.currentUser?.id, itemId, emojiFirst, groupNested)
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
         * Order the collections and number their groups: emoji collections first (group -1), then the rest in
         * groups. Collections inside an emoji collection come first, in the order of those parents. Then the
         * collections that aren't inside another one, where each group of child collections is listed right
         * after its parent.
         */
        private fun layoutGroups(sorted: List<CollectionCardItem>): List<CollectionCardItem> {
            val emoji = sorted.filter { it.isEmoji }
            val rest = sorted.filterNot { it.isEmoji }
            val childrenOf = rest.groupBy { it.parentId }
            val result = emoji.map { it.copy(groupIndex = -1) }.toMutableList()
            val placed = mutableSetOf<UUID>()
            var groupIndex = 0

            fun emit(run: List<CollectionCardItem>) {
                var current = mutableListOf<CollectionCardItem>()

                fun close() {
                    if (current.isNotEmpty()) {
                        result.addAll(current.map { it.copy(groupIndex = groupIndex) })
                        groupIndex++
                        current = mutableListOf()
                    }
                }
                run.filter { placed.add(it.id) }.forEach { c ->
                    current.add(c)
                    val children = childrenOf[c.id].orEmpty().filter { it.id !in placed }
                    if (children.isNotEmpty()) {
                        close()
                        emit(children)
                    }
                }
                close()
            }
            emoji.forEach { parent -> childrenOf[parent.id]?.let { emit(it) } }
            // Not inside another collection
            emit(rest.filter { it.parentId == null })
            // Anything left over (e.g. collections inside each other)
            emit(rest.filter { it.id !in placed })
            return result
        }

        /**
         * Map each of the given collections that is inside another of the given collections to that parent.
         * If it is in several, the first parent in the list wins.
         */
        private suspend fun parentCollections(collections: List<BaseItemDto>): Map<UUID, UUID> {
            val userId = serverRepository.currentUser?.id
            val ids = collections.map { it.id }.toSet()
            val semaphore = Semaphore(IMAGE_CONCURRENCY)
            val children =
                collections
                    .map { collection ->
                        viewModelScope.async(WholphinDispatchers.IO) {
                            semaphore.withPermit {
                                try {
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = collection.id,
                                                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                                enableTotalRecordCount = false,
                                            ),
                                        ).content.items
                                        .map { it.id }
                                        .filter { it in ids && it != collection.id }
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Error fetching child collections of %s", collection.id)
                                    emptyList()
                                }
                            }
                        }
                    }.awaitAll()
            val parents = mutableMapOf<UUID, UUID>()
            collections.zip(children).forEach { (parent, childIds) ->
                childIds.forEach { parents.putIfAbsent(it, parent.id) }
            }
            return parents
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
    }

/**
 * Whether the name starts with an emoji, e.g. "🎬 Marvel"
 */
fun String?.startsWithEmoji(): Boolean {
    val trimmed = this?.trimStart() ?: return false
    if (trimmed.isEmpty()) return false
    val codePoint = trimmed.codePointAt(0)
    return Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt() ||
        codePoint in 0x1F000..0x1FAFF ||
        codePoint in 0x2600..0x27BF
}

/**
 * The name without any leading emoji, variation selectors or spaces, for sorting
 */
private fun String.withoutLeadingEmoji(): String {
    var rest = this.trimStart()
    while (rest.startsWithEmoji() || rest.firstOrNull()?.let { it == '\uFE0F' || it == '\u200D' } == true) {
        rest = rest.substring(Character.charCount(rest.codePointAt(0))).trimStart()
    }
    return rest
}

data class CollectionGridState(
    val item: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val collections: List<CollectionCardItem> = emptyList(),
)

data class CollectionCacheKey(
    val userId: UUID?,
    val parentId: UUID,
    val emojiFirst: Boolean,
    val groupNested: Boolean,
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
    /** The collection this one is inside, when grouping collections */
    val parentId: UUID? = null,
    /** Shown first and as a bigger card (emoji collections, when that setting is on) */
    val isEmoji: Boolean = false,
    /** Which group of cards this belongs to (-1 for the emoji collections), groups are separated by a gap */
    val groupIndex: Int = 0,
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
    emojiFirst: Boolean = false,
    groupNested: Boolean = false,
    showTitle: Boolean = false,
    viewModel: CollectionGridViewModel =
        hiltViewModel<CollectionGridViewModel, CollectionGridViewModel.Factory>(
            key = "${itemId}_collection_cards_${emojiFirst}_$groupNested",
            creationCallback = { it.create(itemId, recursive, emojiFirst, groupNested) },
        ),
) {
    val columns = 4
    // Emoji collections get bigger cards, this many per row
    val emojiColumns = 3
    // The grid is split into lcm(columns, emojiColumns) slots so both groups fit
    val gridSlots = columns * emojiColumns
    val spacing = 16.dp
    // Extra space between the emoji collections and the rest (on top of the normal row spacing)
    val groupGap = Spacing.md
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val cardWidthPx =
        remember(density) {
            with(density) {
                // Same approximation as GenreCardGrid, sized for the bigger emoji cards
                (configuration.screenWidthDp.dp - (2 * 16.dp + (emojiColumns - 1) * spacing))
                    .div(emojiColumns)
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
                val emojiGroup = remember(state.collections) { state.collections.filter { it.isEmoji } }
                // Non-emoji collections, split into their groups
                val restSubgroups =
                    remember(state.collections) {
                        state.collections
                            .filterNot { it.isEmoji }
                            .fold(mutableListOf<MutableList<CollectionCardItem>>()) { groups, c ->
                                if (groups.lastOrNull()?.first()?.groupIndex == c.groupIndex) {
                                    groups.last().add(c)
                                } else {
                                    groups.add(mutableListOf(c))
                                }
                                groups
                            }
                    }
                // Index (across both groups) of the card to focus when the page opens or is returned to
                var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }

                // Measured grid width, used to give the wider emoji cards the same height as the others
                var gridWidthPx by remember { mutableIntStateOf(0) }
                val emojiAspectRatio =
                    remember(gridWidthPx) {
                        val gapPx = with(density) { spacing.toPx() }
                        val paddingPx = with(density) { (2 * Spacing.md).toPx() }
                        val slotPx = (gridWidthPx - paddingPx - (gridSlots - 1) * gapPx) / gridSlots
                        val slotsPerCard = gridSlots / columns
                        val slotsPerEmojiCard = gridSlots / emojiColumns
                        val cardWidth = slotsPerCard * slotPx + (slotsPerCard - 1) * gapPx
                        val emojiCardWidth = slotsPerEmojiCard * slotPx + (slotsPerEmojiCard - 1) * gapPx
                        if (gridWidthPx > 0 && cardWidth > 0f) {
                            AspectRatios.WIDE * emojiCardWidth / cardWidth
                        } else {
                            AspectRatios.WIDE * columns / emojiColumns
                        }
                    }
                val collectionCard: @Composable (CollectionCardItem, Int) -> Unit = { collection, index ->
                    GenreCard(
                        genreId = collection.id,
                        name = collection.item.name,
                        imageUrl = collection.imageUrl,
                        onClick = {
                            focusedIndex = index
                            viewModel.navigationManager.navigateTo(collection.item.destination(index))
                        },
                        onLongClick = {},
                        aspectRatio = if (collection.isEmoji) emojiAspectRatio else AspectRatios.WIDE,
                        modifier =
                            Modifier
                                .ifElse(index == focusedIndex, Modifier.focusRequester(gridFocusRequester))
                                .onFocusChanged { if (it.isFocused) focusedIndex = index },
                    )
                }

                if (state.collections.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.no_results),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridSlots),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onSizeChanged { gridWidthPx = it.width }
                                .focusGroup(),
                    ) {
                        items(
                            emojiGroup.size,
                            key = { emojiGroup[it].gridId },
                            span = { GridItemSpan(gridSlots / emojiColumns) },
                        ) { index ->
                            collectionCard(emojiGroup[index], index)
                        }
                        var offset = emojiGroup.size
                        restSubgroups.forEachIndexed { groupIndex, subgroup ->
                            if (emojiGroup.isNotEmpty() || groupIndex > 0) {
                                // Gap between groups, no heading
                                item(key = "group_gap_$groupIndex", span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(Modifier.height(groupGap))
                                }
                            }
                            val start = offset
                            items(
                                subgroup.size,
                                key = { subgroup[it].gridId },
                                span = { GridItemSpan(gridSlots / columns) },
                            ) { index ->
                                collectionCard(subgroup[index], start + index)
                            }
                            offset += subgroup.size
                        }
                    }
                }
            }
        }
    }
}
