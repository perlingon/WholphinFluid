package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionCardGrid
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.startsWithEmoji
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

enum class CollectionLayout {
    LOADING,

    /** The collection only holds other collections, so show them as cards like the Collections root page */
    COLLECTION_CARDS,

    /**
     * Emoji collection with "Show emoji collections first" and grouping both on: its own items in three columns,
     * its child collections grouped below in four columns like the Collections page
     */
    EMOJI_CONTENTS,

    /** Regular collection details page */
    DETAILS,
}

@HiltViewModel(assistedFactory = CollectionLayoutViewModel.Factory::class)
class CollectionLayoutViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        @Assisted private val itemId: UUID,
        @Assisted private val emojiLayout: Boolean,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: UUID,
                emojiLayout: Boolean,
            ): CollectionLayoutViewModel
        }

        private val _layout = MutableStateFlow(CollectionLayout.LOADING)
        val layout: StateFlow<CollectionLayout> = _layout

        init {
            viewModelScope.launchIO {
                _layout.value =
                    try {
                        computeLayout()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error checking collection contents for %s", itemId)
                        CollectionLayout.DETAILS
                    }
            }
        }

        private suspend fun computeLayout(): CollectionLayout {
            val userId = serverRepository.currentUser?.id
            if (emojiLayout && api.userLibraryApi.getItem(itemId = itemId).content.name.startsWithEmoji()) {
                return CollectionLayout.EMOJI_CONTENTS
            }
            val otherItems =
                GetItemsRequestHandler.countMatching(
                    api,
                    GetItemsRequest(
                        userId = userId,
                        parentId = itemId,
                        excludeItemTypes = listOf(BaseItemKind.BOX_SET),
                        limit = 0,
                        enableTotalRecordCount = true,
                    ),
                )
            if (otherItems > 0) return CollectionLayout.DETAILS
            val childCollections =
                GetItemsRequestHandler.countMatching(
                    api,
                    GetItemsRequest(
                        userId = userId,
                        parentId = itemId,
                        includeItemTypes = listOf(BaseItemKind.BOX_SET),
                        limit = 0,
                        enableTotalRecordCount = true,
                    ),
                )
            return if (childCollections > 0) CollectionLayout.COLLECTION_CARDS else CollectionLayout.DETAILS
        }
    }

/**
 * Show a collection: as a card grid if it only holds other collections, otherwise as the details page
 */
@Composable
fun CollectionPage(
    preferences: UserPreferences,
    itemId: UUID,
    modifier: Modifier = Modifier,
    emojiLayout: Boolean =
        preferences.appPreferences.advancedPreferences.let { it.emojiCollectionsAsMain && it.groupCollections },
    viewModel: CollectionLayoutViewModel =
        hiltViewModel<CollectionLayoutViewModel, CollectionLayoutViewModel.Factory>(
            key = "${itemId}_layout_$emojiLayout",
            creationCallback = { it.create(itemId, emojiLayout) },
        ),
) {
    val layout by viewModel.layout.collectAsState()
    when (layout) {
        CollectionLayout.LOADING -> {
            LoadingPage(modifier.focusable())
        }

        CollectionLayout.COLLECTION_CARDS -> {
            CollectionCardGrid(
                itemId = itemId,
                recursive = false,
                showTitle = true,
                modifier = modifier,
            )
        }

        CollectionLayout.EMOJI_CONTENTS -> {
            CollectionCardGrid(
                itemId = itemId,
                recursive = false,
                emojiFirst = true,
                groupNested = true,
                contentsMode = true,
                showTitle = true,
                modifier = modifier,
            )
        }

        CollectionLayout.DETAILS -> {
            CollectionDetails(
                preferences = preferences,
                itemId = itemId,
                modifier = modifier,
            )
        }
    }
}
