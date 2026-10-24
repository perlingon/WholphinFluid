package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How many levels of nested collections to look through for a backdrop
 */
private const val MAX_NESTED_DEPTH = 3

/**
 * How many requests run at the same time while building the tree
 */
private const val CONCURRENCY = 4

/**
 * A collection, everything directly in it, and the item whose backdrop represents it
 */
@Serializable
data class CollectionEntry(
    val dto: BaseItemDto,
    val children: List<BaseItemDto>,
    val backdropItem: BaseItemDto? = null,
) {
    val childCollections: List<BaseItemDto> get() = children.filter { it.type == BaseItemKind.BOX_SET }

    /** Holds child collections and nothing else */
    val onlyCollections: Boolean get() = children.isNotEmpty() && children.all { it.type == BaseItemKind.BOX_SET }
}

/**
 * Every collection the user can see and which collections each Collections page lists
 */
@Serializable
data class CollectionTree(
    val builtAt: Long = 0,
    /** Collection ID to its entry */
    val collections: Map<String, CollectionEntry> = emptyMap(),
    /** [listKey] to the IDs of the collections that page lists, in server order */
    val lists: Map<String, List<String>> = emptyMap(),
) {
    fun entry(id: UUID): CollectionEntry? = collections[id.toString()]
}

fun listKey(
    parentId: UUID,
    recursive: Boolean,
) = "${parentId}_$recursive"

/**
 * Keeps a map of how the user's collections nest, saved on disk, so the Collections pages and collection layouts
 * don't have to ask the server every time.
 *
 * The saved map is loaded when a user is chosen and rebuilt in the background when it is older than [MAX_AGE].
 * Pages also call [refreshIfOlderThan] when opened, so changes show up after a short while.
 */
@Singleton
class CollectionTreeService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

        private val _tree = MutableStateFlow<CollectionTree?>(null)

        /** The current tree, or null before it is loaded for the current user */
        val tree: StateFlow<CollectionTree?> = _tree

        private val refreshMutex = Mutex()

        init {
            scope.launch {
                serverRepository.currentUserFlow
                    .map { it?.id }
                    .distinctUntilChanged()
                    .collectLatest { userId ->
                        _tree.value = null
                        if (userId != null) {
                            _tree.value = loadFromDisk(userId)
                            // Let the app finish starting before building in the background
                            delay(STARTUP_DELAY)
                            refreshIfOlderThan(MAX_AGE)
                        }
                    }
            }
        }

        /**
         * Get the tree, building it now if nothing is saved yet
         */
        suspend fun get(): CollectionTree = _tree.value ?: refresh(skipIfNewerThan = 1.minutes)

        /**
         * Get an entry, fetching that collection now if it isn't in the tree yet (e.g. created since the last build)
         */
        suspend fun entry(id: UUID): CollectionEntry? {
            get().entry(id)?.let { return it }
            val userId = serverRepository.currentUser?.id ?: return null
            val dto =
                GetItemsRequestHandler
                    .execute(api, slimRequest(userId).copy(ids = listOf(id)))
                    .content.items
                    .firstOrNull() ?: return null
            val entry =
                fetchEntry(userId, dto, null).let {
                    it.copy(backdropItem = it.backdropItem ?: dto.takeIf { d -> !d.backdropImageTags.isNullOrEmpty() })
                }
            update { it.copy(collections = it.collections + (id.toString() to entry)) }
            return entry
        }

        /**
         * The collections a Collections page lists, fetching the list now if it isn't saved yet
         */
        suspend fun list(
            parentId: UUID,
            recursive: Boolean,
        ): List<CollectionEntry> {
            val tree = get()
            val ids = tree.lists[listKey(parentId, recursive)] ?: fetchList(parentId, recursive)
            return ids.mapNotNull { entry(UUID.fromString(it)) }
        }

        /**
         * Rebuild in the background if the tree is older than [maxAge]
         */
        fun refreshIfOlderThan(maxAge: Duration) {
            val builtAt = _tree.value?.builtAt ?: 0
            if (System.currentTimeMillis() - builtAt > maxAge.inWholeMilliseconds && !refreshMutex.isLocked) {
                scope.launch {
                    try {
                        refresh()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error refreshing collection tree")
                    }
                }
            }
        }

        /**
         * Rebuild the whole tree from the server and save it
         */
        suspend fun refresh(skipIfNewerThan: Duration = Duration.ZERO): CollectionTree {
            val userId = serverRepository.currentUser?.id ?: return CollectionTree()
            return refreshMutex.withLock {
                val old = _tree.value
                // Another caller may have just finished building while this one waited for the lock
                if (old != null && System.currentTimeMillis() - old.builtAt < skipIfNewerThan.inWholeMilliseconds) {
                    return@withLock old
                }
                Timber.d("Building collection tree")
                val all =
                    GetItemsRequestHandler
                        .execute(
                            api,
                            slimRequest(userId).copy(
                                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                recursive = true,
                            ),
                        ).content.items
                val semaphore = Semaphore(CONCURRENCY)
                val entries =
                    coroutineScope {
                        all
                            .map { dto ->
                                async {
                                    semaphore.withPermit {
                                        try {
                                            dto.id.toString() to fetchEntry(userId, dto, old?.entry(dto.id))
                                        } catch (ex: Exception) {
                                            Timber.w(ex, "Error fetching collection %s", dto.id)
                                            old?.entry(dto.id)?.let { dto.id.toString() to it }
                                        }
                                    }
                                }
                            }.awaitAll()
                            .filterNotNull()
                            .toMap()
                    }
                val withBackdrops = resolveNestedBackdrops(entries)
                val lists =
                    old
                        ?.lists
                        ?.mapValues { (key, ids) ->
                            val (parentId, recursive) = key.split("_")
                            try {
                                fetchListIds(userId, UUID.fromString(parentId), recursive.toBoolean())
                            } catch (ex: Exception) {
                                Timber.w(ex, "Error fetching collection list %s", key)
                                ids
                            }
                        }.orEmpty()
                val tree = CollectionTree(System.currentTimeMillis(), withBackdrops, lists)
                _tree.value = tree
                save(userId, tree)
                Timber.d("Built collection tree with %s collections", tree.collections.size)
                tree
            }
        }

        private suspend fun fetchList(
            parentId: UUID,
            recursive: Boolean,
        ): List<String> {
            val userId = serverRepository.currentUser?.id ?: return emptyList()
            val ids = fetchListIds(userId, parentId, recursive)
            update { it.copy(lists = it.lists + (listKey(parentId, recursive) to ids)) }
            return ids
        }

        private suspend fun fetchListIds(
            userId: UUID,
            parentId: UUID,
            recursive: Boolean,
        ): List<String> =
            GetItemsRequestHandler
                .execute(
                    api,
                    GetItemsRequest(
                        userId = userId,
                        parentId = parentId,
                        recursive = recursive,
                        includeItemTypes = listOf(BaseItemKind.BOX_SET),
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        enableImages = false,
                        enableUserData = false,
                        enableTotalRecordCount = false,
                    ),
                ).content.items
                .map { it.id.toString() }

        /**
         * Fetch what's in a collection and pick its backdrop: keep the previous pick if it's still in there,
         * otherwise a random item with a backdrop
         */
        private suspend fun fetchEntry(
            userId: UUID,
            dto: BaseItemDto,
            old: CollectionEntry?,
        ): CollectionEntry {
            val children =
                GetItemsRequestHandler
                    .execute(api, slimRequest(userId).copy(parentId = dto.id))
                    .content.items
            val candidates =
                children.filter { it.type != BaseItemKind.BOX_SET && !it.backdropImageTags.isNullOrEmpty() }
            val backdrop =
                old?.backdropItem?.takeIf { previous -> candidates.any { it.id == previous.id } }
                    ?: candidates.randomOrNull()
            return CollectionEntry(dto, children, backdrop)
        }

        /**
         * Collections holding no items with a backdrop borrow one from their child collections, else use their own
         */
        private fun resolveNestedBackdrops(entries: Map<String, CollectionEntry>): Map<String, CollectionEntry> {
            fun find(
                entry: CollectionEntry,
                depth: Int,
            ): BaseItemDto? {
                entry.backdropItem?.let { return it }
                if (depth >= MAX_NESTED_DEPTH) return null
                return entry.childCollections.firstNotNullOfOrNull { child ->
                    entries[child.id.toString()]?.let { find(it, depth + 1) }
                }
            }
            return entries.mapValues { (_, entry) ->
                if (entry.backdropItem != null) {
                    entry
                } else {
                    entry.copy(
                        backdropItem =
                            find(entry, 0)
                                ?: entry.dto.takeIf { !it.backdropImageTags.isNullOrEmpty() },
                    )
                }
            }
        }

        private fun slimRequest(userId: UUID) =
            GetItemsRequest(
                userId = userId,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(ItemFields.SORT_NAME),
                enableImageTypes = listOf(ImageType.BACKDROP),
                imageTypeLimit = 1,
                enableUserData = false,
                enableTotalRecordCount = false,
            )

        private fun update(block: (CollectionTree) -> CollectionTree) {
            val userId = serverRepository.currentUser?.id ?: return
            val updated = block(_tree.value ?: CollectionTree())
            _tree.value = updated
            scope.launch { save(userId, updated) }
        }

        private fun file(userId: UUID) = File(context.filesDir, "collection_tree_$userId.json")

        private fun loadFromDisk(userId: UUID): CollectionTree? =
            try {
                file(userId).takeIf { it.exists() }?.readText()?.let {
                    json.decodeFromString(CollectionTree.serializer(), it)
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Error reading saved collection tree")
                null
            }

        private fun save(
            userId: UUID,
            tree: CollectionTree,
        ) {
            try {
                val target = file(userId)
                val temp = File(target.parentFile, target.name + ".tmp")
                temp.writeText(json.encodeToString(CollectionTree.serializer(), tree))
                temp.renameTo(target)
            } catch (ex: Exception) {
                Timber.w(ex, "Error saving collection tree")
            }
        }

        companion object {
            /** Rebuild the saved tree in the background when it is older than this */
            val MAX_AGE = 6.hours

            /** When a Collections page opens, refresh in the background if the tree is older than this */
            val PAGE_REFRESH_AGE = 10.minutes

            private val STARTUP_DELAY = 15.seconds
        }
    }
