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
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CollectionTreeService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = CollectionGridViewModel.Factory::class)
class CollectionGridViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val collectionTreeService: CollectionTreeService,
        val navigationManager: NavigationManager,
        @Assisted private val itemId: UUID,
        @Assisted("recursive") private val recursive: Boolean,
        @Assisted("emojiFirst") private val emojiFirst: Boolean,
        @Assisted("groupNested") private val groupNested: Boolean,
        @Assisted("contentsMode") private val contentsMode: Boolean,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: UUID,
                @Assisted("recursive") recursive: Boolean,
                @Assisted("emojiFirst") emojiFirst: Boolean,
                @Assisted("groupNested") groupNested: Boolean,
                @Assisted("contentsMode") contentsMode: Boolean,
            ): CollectionGridViewModel
        }

        private val _state = MutableStateFlow(CollectionGridState())
        val state: StateFlow<CollectionGridState> = _state

        private var cardWidthPx = 0

        fun init(cardWidthPx: Int) {
            this.cardWidthPx = cardWidthPx
            _state.update { it.copy(item = DataLoadingState.Loading) }
            viewModelScope.launchIO {
                try {
                    // A collection's own details are in the saved tree, a library's need one call
                    val item =
                        collectionTreeService.get().entry(itemId)?.dto?.let { BaseItem(it, false) }
                            ?: api.userLibraryApi.getItem(itemId = itemId).content.let { BaseItem(it, false) }
                    show(item)
                    collectionTreeService.refreshIfOlderThan(CollectionTreeService.PAGE_REFRESH_AGE)
                    // Show changes when the tree is rebuilt in the background
                    collectionTreeService.tree
                        .drop(1)
                        .filterNotNull()
                        .collect { show(item) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching collections")
                    _state.update { it.copy(item = DataLoadingState.Error(ex)) }
                }
            }
        }

        /**
         * Build the grid from the saved collection tree
         */
        private suspend fun show(item: BaseItem) {
            val all =
                if (contentsMode) {
                    contentsItems()
                } else {
                    collectionTreeService.list(itemId, recursive).map { it.dto }
                }
            val boxSets = all.filter { it.type == BaseItemKind.BOX_SET }
            val ids = boxSets.map { it.id }.toSet()
            // With both settings on, non-emoji collections that only hold other collections are represented
            // by their group heading, so their own card is hidden
            val hideContainers = emojiFirst && groupNested
            val parents = mutableMapOf<UUID, UUID>()
            val containers = mutableSetOf<UUID>()
            if (groupNested) {
                boxSets.forEach { collection ->
                    val entry = collectionTreeService.entry(collection.id) ?: return@forEach
                    entry.childCollections
                        .filter { it.id in ids && it.id != collection.id }
                        .forEach { parents.putIfAbsent(it.id, collection.id) }
                    if (hideContainers && entry.onlyCollections) containers.add(collection.id)
                }
            }
            // Emoji collections first, then the rest. Both A-Z, ignoring the leading emoji
            val byName =
                compareByDescending<CollectionCardItem> { it.isEmoji }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortName.withoutLeadingEmoji() }
            val allSorted =
                all
                    .map {
                        CollectionCardItem(
                            item = BaseItem(it, false),
                            imageUrl = imageFor(it),
                            parentId = parents[it.id],
                            isEmoji =
                                if (contentsMode) {
                                    // Inside a nested collection its own movies etc. are the big cards
                                    it.type != BaseItemKind.BOX_SET
                                } else {
                                    emojiFirst && it.name.startsWithEmoji()
                                },
                        )
                    }.sortedWith(byName)
            val collections = layoutGroups(allSorted).filter { it.isEmoji || it.id !in containers }
            _state.update {
                it.copy(
                    item = DataLoadingState.Success(item),
                    collections = collections,
                )
            }
        }

        /**
         * Everything directly in this collection, plus the collections inside its child collections so those
         * can be grouped under their parent like on the Collections page
         */
        private suspend fun contentsItems(): List<BaseItemDto> {
            val direct = collectionTreeService.entry(itemId)?.children.orEmpty()
            val grandchildren =
                direct
                    .filter { it.type == BaseItemKind.BOX_SET }
                    .flatMap { collectionTreeService.entry(it.id)?.childCollections.orEmpty() }
            return (direct + grandchildren).distinctBy { it.id }
        }

        /**
         * A movie etc. uses its own backdrop, a collection the one picked for it in the tree
         */
        private suspend fun imageFor(dto: BaseItemDto): String? {
            val source =
                if (dto.type != BaseItemKind.BOX_SET) {
                    dto.takeIf { !it.backdropImageTags.isNullOrEmpty() }
                } else {
                    collectionTreeService.entry(dto.id)?.backdropItem
                }
            return source?.let { backdropUrl(it, cardWidthPx) }
        }

        /**
         * Order the collections and number their groups: emoji collections first (group -1), then the rest in
         * groups. Collections inside an emoji collection come first, in the order of those parents. Then the
         * collections that aren't inside another one. Each group is listed without breaks, followed by the
         * groups of its members' child collections.
         */
        private fun layoutGroups(sorted: List<CollectionCardItem>): List<CollectionCardItem> {
            val emoji = sorted.filter { it.isEmoji }
            val rest = sorted.filterNot { it.isEmoji }
            val childrenOf = rest.groupBy { it.parentId }
            val result = emoji.map { it.copy(groupIndex = -1) }.toMutableList()
            val placed = mutableSetOf<UUID>()
            var groupIndex = 0

            // Only groups of children of a first level collection get a heading (the parent's name)
            fun titleFor(parent: CollectionCardItem) = parent.item.name.takeIf { parent.parentId == null }

            // A group is listed in full first, then the child groups of its members follow in the same order
            fun emit(
                run: List<CollectionCardItem>,
                title: String?,
            ) {
                val group = run.filter { placed.add(it.id) }
                if (group.isEmpty()) return
                result.addAll(group.map { it.copy(groupIndex = groupIndex, groupTitle = title) })
                groupIndex++
                group.forEach { c ->
                    val children = childrenOf[c.id].orEmpty().filter { it.id !in placed }
                    if (children.isNotEmpty()) emit(children, titleFor(c))
                }
            }
            emoji.forEach { parent -> childrenOf[parent.id]?.let { emit(it, titleFor(parent)) } }
            // Not inside another collection
            emit(rest.filter { it.parentId == null }, null)
            // Anything left over (e.g. collections inside each other)
            emit(rest.filter { it.id !in placed }, null)
            return result
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
    /** Heading shown above this card's group, if any */
    val groupTitle: String? = null,
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
    contentsMode: Boolean = false,
    showTitle: Boolean = false,
    viewModel: CollectionGridViewModel =
        hiltViewModel<CollectionGridViewModel, CollectionGridViewModel.Factory>(
            key = "${itemId}_collection_cards_${emojiFirst}_${groupNested}_$contentsMode",
            creationCallback = { it.create(itemId, recursive, emojiFirst, groupNested, contentsMode) },
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
                                // Gap between groups
                                item(key = "group_gap_$groupIndex", span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(Modifier.height(groupGap))
                                }
                            }
                            subgroup.first().groupTitle?.let { title ->
                                item(key = "group_title_$groupIndex", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
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
