package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.components.TimeDisplay
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.spacedByWithFooter
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val navDrawerService: NavDrawerService,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val backdropService: BackdropService,
        private val musicService: MusicService,
    ) : ViewModel() {
        val serviceState = navDrawerService.state

        private val _state = MutableStateFlow(NavDrawerState())
        val state: StateFlow<NavDrawerState> = _state

        fun onClickDrawerItem(
            index: Int,
            item: NavDrawerItem,
        ) {
            when (item) {
                NavDrawerItem.Favorites -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Favorites,
                    )
                }

                NavDrawerItem.More -> {
                    setShowMore(!state.value.moreExpanded)
                }

                NavDrawerItem.Discover -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Discover,
                    )
                }

                is ServerNavDrawerItem -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(item.destination)
                }
            }
        }

        fun setIndex(index: Int) {
            _state.update { it.copy(selectedIndex = index) }
        }

        fun setShowMore(value: Boolean) {
            _state.update { it.copy(moreExpanded = value) }
        }

        fun getUserImage(user: JellyfinUser): String = api.imageApi.getUserImageUrl(user.id)

        /**
         * Determine which nav drawer item should be highlighted as currently selected
         */
        fun updateSelectedIndex() {
            viewModelScope.launchDefault {
                val asDestinations =
                    buildList {
                        addAll(serviceState.value.items)
                        addAll(serviceState.value.moreItems)
                    }.map {
                        when (it) {
                            is ServerNavDrawerItem -> it.destination
                            is NavDrawerItem.Favorites -> Destination.Favorites
                            is NavDrawerItem.Discover -> Destination.Discover
                            else -> null
                        }
                    }

                val backstack = navigationManager.backStack.toList().reversed()
                for (i in 0..<backstack.size) {
                    val key = backstack[i]
                    val index =
                        if (key is Destination.Home) {
                            HOME_INDEX
                        } else if (key is Destination.Search) {
                            SEARCH_INDEX
                        } else if (key is Destination.NowPlaying) {
                            NOW_PLAYING_INDEX
                        } else {
                            val idx = asDestinations.indexOf(key)
                            if (idx >= 0) {
                                idx
                            } else {
                                null
                            }
                        }
                    Timber.v("Found $index => $key")
                    if (index != null) {
                        _state.update { it.copy(selectedIndex = index) }
                        break
                    }
                }
            }
        }

        fun navigateToSetup(userList: SetupDestination) {
            viewModelScope.launchDefault {
                musicService.stop()
                setupNavigationManager.navigateTo(userList)
            }
        }
    }

data class NavDrawerState(
    val selectedIndex: Int = -1,
    val moreExpanded: Boolean = false,
)

/**
 * An item that can be shown in the nav drawer
 *
 * Some are built-in such as Favorites. Others are created dynamically for libraries.
 */
sealed interface NavDrawerItem {
    val id: String

    fun name(context: Context): String

    object Favorites : NavDrawerItem {
        override val id: String
            get() = "a_favorites"

        override fun name(context: Context): String = context.getString(R.string.favorites)
    }

    object More : NavDrawerItem {
        override val id: String
            get() = "a_more"

        override fun name(context: Context): String = context.getString(R.string.more)
    }

    object Discover : NavDrawerItem {
        override val id: String
            get() = "a_discover"

        override fun name(context: Context): String = context.getString(R.string.discover)
    }
}

/**
 * A server provided nav drawer item, typically a library
 */
data class ServerNavDrawerItem(
    val itemId: UUID,
    val name: String,
    val destination: Destination,
    val type: CollectionType,
) : NavDrawerItem {
    override val id: String = getId(itemId)

    override fun name(context: Context): String = name

    companion object {
        fun getId(itemId: UUID) = "s_" + itemId.toServerString()
    }
}

private const val HOME_INDEX = -1
private const val SEARCH_INDEX = -2
private const val NOW_PLAYING_INDEX = -3

/**
 * Display the left side navigation drawer with [DestinationContent] on the right
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    drawerState: DrawerState,
    navDrawerListState: LazyListState,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    openOnStart: Boolean = false,
    onOpenedOnStart: () -> Unit = {},
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server.id}_${user.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
) {
    LaunchedEffect(Unit) { viewModel.updateSelectedIndex() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    val focusRequester = remember { FocusRequester() }
    var drawerHasFocus by remember { mutableStateOf(false) }

    // The current page's tabs, shown as a second level of the drawer (see DrawerSubmenu)
    val submenuHost = remember { DrawerSubmenuHost() }
    val submenu = submenuHost.submenu
    val selectedTabFocusRequester = submenu?.let { it.tabs.getOrNull(it.selectedIndex)?.tabFocusRequester }
    var topHasFocus by remember { mutableStateOf(false) }
    var submenuHasFocus by remember { mutableStateOf(false) }
    // Submenu items can't take focus while the top level has it, so pressing right on the top level
    // doesn't land on them. This lets them while focus is being moved there on purpose
    var enteringSubmenu by remember { mutableStateOf(false) }
    // After a submenu item is clicked, the page may take focus even though the drawer still has it
    var releaseToContent by remember { mutableStateOf(false) }
    val showSubmenu = drawerState.isOpen && submenu != null && submenuHasFocus

    fun focusSubmenu() {
        enteringSubmenu = true
        scope.launch {
            // The submenu's items may not be laid out yet, so keep trying for a few frames
            var frames = 0
            while (!submenuHasFocus && frames++ < 30) {
                submenuHost.submenu
                    ?.let { it.tabs.getOrNull(it.selectedIndex) }
                    ?.tabFocusRequester
                    ?.tryRequestFocus()
                withFrameNanos { }
            }
            enteringSubmenu = false
        }
    }

    // Opening a drawer item that has a submenu (a page with tabs) moves on to the submenu
    val openedFromDrawer = remember { drawerState.isOpen && !openOnStart }
    var submenuShownOnArrival by remember { mutableStateOf(false) }
    LaunchedEffect(submenu != null) {
        if (openedFromDrawer && submenu != null && !submenuShownOnArrival) {
            submenuShownOnArrival = true
            focusSubmenu()
        }
    }
    // When focus moves from the previous page's drawer to this one, the previous one may close the
    // shared drawer state after this one opened it
    LaunchedEffect(drawerHasFocus, drawerState.currentValue) {
        if (drawerHasFocus && !drawerState.isOpen) drawerState.setValue(DrawerValue.Open)
        if (!drawerHasFocus) releaseToContent = false
    }
    BackHandler(enabled = submenuHasFocus) {
        focusRequester.tryRequestFocus()
    }

    // When the app starts, the drawer opens with focus on the current page's item. Its items may
    // not be laid out on the first frame, so keep trying for a few frames
    LaunchedEffect(Unit) {
        if (openOnStart) {
            onOpenedOnStart.invoke()
            repeat(30) {
                if (drawerHasFocus) return@LaunchedEffect
                focusRequester.tryRequestFocus()
                withFrameNanos { }
            }
            if (!drawerHasFocus) drawerState.setValue(DrawerValue.Closed)
        }
    }

    // If the user presses back while on the home page, open the nav drawer, another back press will quit the app
    BackHandler(enabled = (drawerState.currentValue == DrawerValue.Closed && destination is Destination.Home)) {
        drawerState.setValue(DrawerValue.Open)
        focusRequester.requestFocus()
    }
    val serviceState by viewModel.serviceState.collectAsState()
    val state by viewModel.state.collectAsState()
    val moreExpanded = state.moreExpanded
    // A negative index is a built-in page, >=0 is a library
    val selectedIndex = state.selectedIndex

    BackHandler(enabled = moreExpanded && drawerState.currentValue == DrawerValue.Open) {
        viewModel.setShowMore(false)
    }

    val closedDrawerWidth = CollapsedDrawerItemWidth
    // With the submenu showing, the top level slides left (out of view, apart from SubmenuParentPeek)
    val openDrawerWidth =
        if (showSubmenu) SubmenuParentPeek + SubmenuDrawerWidth else ExpandedDrawerItemWidth
    val submenuShift by animateDpAsState(
        targetValue = if (showSubmenu) ExpandedDrawerItemWidth - SubmenuParentPeek else 0.dp,
        animationSpec = spring(stiffness = DrawerAnimationStiffness),
        label = "submenuShift",
    )
    val submenuAlpha by animateFloatAsState(
        targetValue = if (showSubmenu) 1f else 0f,
        animationSpec = spring(stiffness = DrawerAnimationStiffness),
        label = "submenuAlpha",
    )
    val offset by animateIntOffsetAsState(
        targetValue =
            IntOffset(
                x =
                    with(density) {
                        if (drawerState.isOpen) (openDrawerWidth - closedDrawerWidth).roundToPx() else 0
                    },
                y = 0,
            ),
        animationSpec =
            spring(
                stiffness = DrawerAnimationStiffness,
                visibilityThreshold = IntOffset.VisibilityThreshold,
            ),
    )

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            val isOpen = drawerValue.isOpen
            val spacedBy = 4.dp
            val searchFocusRequester = remember { FocusRequester() }

            ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                Box(
                    modifier =
                        Modifier
                            .width(ExpandedDrawerItemWidth + if (submenu != null) SubmenuDrawerWidth else 0.dp)
                            .fillMaxHeight()
                            .onFocusChanged { drawerHasFocus = it.hasFocus }
                            .focusProperties {
                                onEnter = {
                                    // Pressing left from the page opens the submenu if there is one,
                                    // otherwise the current page's item
                                    if (requestedFocusDirection == FocusDirection.Left) {
                                        (selectedTabFocusRequester ?: focusRequester).tryRequestFocus()
                                    }
                                }
                            }.focusGroup(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacedBy),
                        modifier =
                            Modifier
                                .offset { IntOffset(-submenuShift.roundToPx(), 0) }
                                .graphicsLayer { alpha = 1f - (1f - SubmenuParentAlpha) * submenuAlpha }
                                .width(ExpandedDrawerItemWidth)
                                .fillMaxHeight()
                                .onFocusChanged { topHasFocus = it.hasFocus }
                                .onPreviewKeyEvent {
                                    // Right from the top level goes to the submenu, the page is beyond it
                                    if (submenu != null &&
                                        it.type == KeyEventType.KeyDown &&
                                        it.key == Key.DirectionRight
                                    ) {
                                        focusSubmenu()
                                        true
                                    } else {
                                        false
                                    }
                                },
                    ) {
                        // Even though some must be clicked, focusing on it should clear other focused items
                        val interactionSource = remember { MutableInteractionSource() }
                        val userImageUrl = remember(user) { viewModel.getUserImage(user) }
                        ProfileIcon(
                            user = user,
                            imageUrl = userImageUrl,
                            serverName = server.name ?: server.url,
                            drawerOpen = isOpen,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.navigateToSetup(
                                    SetupDestination.UserList(server),
                                )
                            },
                            modifier = Modifier,
                        )
                        AnimatedVisibility(
                            visible = serviceState.nowPlayingEnabled,
                            enter = expandVertically(expandFrom = Alignment.Top),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.now_playing),
                                subtext = serviceState.nowPlayingTitle,
                                selected = selectedIndex == NOW_PLAYING_INDEX,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.setIndex(NOW_PLAYING_INDEX)
                                    viewModel.navigationManager.navigateTo(Destination.NowPlaying)
                                },
                                modifier =
                                    Modifier
                                        .ifElse(
                                            selectedIndex == NOW_PLAYING_INDEX,
                                            Modifier.focusRequester(focusRequester),
                                        ),
                            )
                        }
                        LazyColumn(
                            state = navDrawerListState,
                            contentPadding = PaddingValues(0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedByWithFooter(spacedBy),
                            modifier =
                                Modifier
                                    .focusGroup()
                                    .focusProperties {
                                        onEnter = {
                                            if (requestedFocusDirection == FocusDirection.Down) {
                                                searchFocusRequester.tryRequestFocus()
                                            } else {
                                                focusRequester.tryRequestFocus()
                                            }
                                        }
                                    }.fillMaxHeight(),
                        ) {
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                IconNavItem(
                                    text = stringResource(R.string.search),
                                    selected = selectedIndex == SEARCH_INDEX,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.setIndex(SEARCH_INDEX)
                                        viewModel.navigationManager.navigateToFromDrawer(Destination.Search())
                                    },
                                    modifier =
                                        Modifier
                                            .focusRequester(searchFocusRequester)
                                            .ifElse(
                                                selectedIndex == SEARCH_INDEX,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                IconNavItem(
                                    text = stringResource(R.string.home),
                                    selected = selectedIndex == HOME_INDEX,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.setIndex(HOME_INDEX)
                                        if (destination is Destination.Home) {
                                            viewModel.navigationManager.reloadHome()
                                            onClearBackdrop.invoke()
                                        } else {
                                            viewModel.navigationManager.goToHome()
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                selectedIndex == HOME_INDEX,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }
                            itemsIndexed(serviceState.items) { index, it ->
                                val interactionSource = remember { MutableInteractionSource() }
                                NavItem(
                                    library = it,
                                    selected = selectedIndex == index,
                                    moreExpanded = moreExpanded,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.onClickDrawerItem(index, it)
                                    },
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                selectedIndex == index,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }
                            if (serviceState.moreItems.isNotEmpty()) {
                                item {
                                    val index = serviceState.items.size
                                    val interactionSource = remember { MutableInteractionSource() }
                                    NavItem(
                                        library = NavDrawerItem.More,
                                        selected = false,
                                        moreExpanded = moreExpanded,
                                        drawerOpen = isOpen,
                                        interactionSource = interactionSource,
                                        onClick = {
                                            viewModel.onClickDrawerItem(index, NavDrawerItem.More)
                                        },
                                        modifier =
                                            Modifier
                                                .ifElse(
                                                    selectedIndex == index,
                                                    Modifier.focusRequester(focusRequester),
                                                ),
                                    )
                                }
                            }
                            if (moreExpanded) {
                                itemsIndexed(serviceState.moreItems) { index, it ->
                                    val adjustedIndex =
                                        remember(serviceState) { (index + serviceState.items.size) }
                                    val interactionSource = remember { MutableInteractionSource() }
                                    NavItem(
                                        library = it,
                                        selected = selectedIndex == adjustedIndex,
                                        moreExpanded = moreExpanded,
                                        drawerOpen = isOpen,
                                        onClick = {
                                            viewModel.onClickDrawerItem(
                                                adjustedIndex,
                                                it,
                                            )
                                        },
                                        containerColor =
                                            if (isOpen) {
                                                MaterialTheme.colorScheme.surface.copy(alpha = .5f)
                                            } else {
                                                Color.Transparent
                                            },
                                        interactionSource = interactionSource,
                                        modifier =
                                            Modifier
                                                .ifElse(
                                                    selectedIndex == adjustedIndex,
                                                    Modifier.focusRequester(focusRequester),
                                                ),
                                    )
                                }
                            }
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                IconNavItem(
                                    text = stringResource(R.string.settings),
                                    selected = false,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.navigationManager.navigateTo(
                                            Destination.Settings(
                                                PreferenceScreenOption.BASIC,
                                            ),
                                        )
                                    },
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                    if (submenu != null) {
                        DrawerSubmenuColumn(
                            submenu = submenu,
                            drawerOpen = isOpen,
                            focusable = !topHasFocus || enteringSubmenu,
                            spacedBy = spacedBy,
                            onClick = { index ->
                                submenu.onSelect(index)
                                // Move on into the page, once the tab's content can take focus
                                releaseToContent = true
                                scope.launch {
                                    repeat(60) {
                                        if (!drawerHasFocus) return@launch
                                        submenu.tabs
                                            .getOrNull(index)
                                            ?.contentFocusRequester
                                            ?.tryRequestFocus()
                                        withFrameNanos { }
                                    }
                                }
                            },
                            onLeft = { focusRequester.tryRequestFocus() },
                            modifier =
                                Modifier
                                    .offset { IntOffset((ExpandedDrawerItemWidth - submenuShift).roundToPx(), 0) }
                                    .graphicsLayer { alpha = submenuAlpha }
                                    .onFocusChanged { submenuHasFocus = it.hasFocus },
                        )
                    }
                }
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Drawer content
            CompositionLocalProvider(
                LocalNavDrawerOpen provides drawerState.isOpen,
                LocalDrawerSubmenuHost provides submenuHost,
            ) {
                DestinationContent(
                    destination = destination,
                    preferences = preferences,
                    onClearBackdrop = onClearBackdrop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .offset {
                                offset
                            }.padding(start = closedDrawerWidth + Spacing.sm, end = Spacing.md)
                            .focusProperties {
                                onEnter = {
                                    // While the drawer has focus, the page can't take focus for
                                    // itself (eg when its content finishes loading), only by the
                                    // user moving out of the drawer
                                    if (requestedFocusDirection == FocusDirection.Enter &&
                                        drawerHasFocus &&
                                        !releaseToContent
                                    ) {
                                        cancelFocusChange()
                                    }
                                }
                            }.focusGroup(),
                )
            }
            if (preferences.appPreferences.interfacePreferences.showClock) {
                TimeDisplay()
            }
        }
    }
}

@Composable
fun NavigationDrawerScope.ProfileIcon(
    user: JellyfinUser,
    imageUrl: String?,
    serverName: String,
    drawerOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        // The user's picture sits on the right, next to the right-aligned name
        trailingContent = {
            UserIconCardImage(
                id = user.id,
                name = user.name,
                imageUrl = imageUrl,
                alpha = if (drawerOpen) 1f else .5f,
                modifier = Modifier.size(DrawerIconSize),
            )
        },
        supportingContent = {
            Text(
                text = serverName,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        interactionSource = interactionSource,
    ) {
        Text(
            text = (user.name ?: user.id.toString()).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = NavItemFontWeight,
            letterSpacing = NavItemLetterSpacing,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun NavigationDrawerScope.IconNavItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        supportingContent =
            subtext?.let {
                {
                    Text(
                        text = it,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        interactionSource = interactionSource,
    ) {
        NavItemText(text, navItemColor(selected, focused, drawerOpen))
    }
}

@Composable
fun NavigationDrawerScope.NavItem(
    library: NavDrawerItem,
    onClick: () -> Unit,
    selected: Boolean,
    moreExpanded: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    containerColor: Color = Color.Transparent,
) {
    val context = LocalContext.current
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        colors = navDrawerItemColors(containerColor),
        trailingContent =
            if (library is NavDrawerItem.More) {
                {
                    Icon(
                        imageVector = if (moreExpanded) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowLeft,
                        contentDescription = null,
                    )
                }
            } else {
                null
            },
        interactionSource = interactionSource,
    ) {
        NavItemText(library.name(context), navItemColor(selected, focused, drawerOpen))
    }
}

// Nav drawer item names are upper case, in a heavier weight
internal val NavItemFontWeight = FontWeight.SemiBold
internal val NavItemLetterSpacing = 1.sp

/**
 * A nav drawer item's name: right-aligned, coloured to show whether it is focused or the current page
 */
@Composable
internal fun NavItemText(
    text: String,
    color: Color,
) {
    Text(
        text = text.uppercase(),
        color = color,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = NavItemFontWeight,
        letterSpacing = NavItemLetterSpacing,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Whether the nav drawer is open, for pages that should hold back from taking focus while it is
 */
val LocalNavDrawerOpen = compositionLocalOf { false }

/**
 * The text colour of a nav drawer item. Items have no background, so this is all that shows focus:
 * the focused item takes the theme's focus colour, the current page's item is brighter than the rest
 */
@Composable
@ReadOnlyComposable
fun navItemColor(
    selected: Boolean,
    focused: Boolean,
    drawerOpen: Boolean,
): Color {
    val color =
        when {
            focused -> {
                when (LocalTheme.current) {
                    AppThemeColors.BOLD_BLUE,
                    AppThemeColors.OLED_BLACK,
                    -> MaterialTheme.colorScheme.primary

                    else -> MaterialTheme.colorScheme.border
                }
            }

            selected -> MaterialTheme.colorScheme.onSurface

            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = NavItemUnselectedAlpha)
        }
    return if (drawerOpen) color else color.copy(alpha = color.alpha * .5f)
}

// How bright the items that aren't the current page are
internal const val NavItemUnselectedAlpha = .6f

/**
 * The second level of the nav drawer: the current page's tabs, laid out like the top level
 */
@Composable
private fun NavigationDrawerScope.DrawerSubmenuColumn(
    submenu: DrawerSubmenu,
    drawerOpen: Boolean,
    focusable: Boolean,
    spacedBy: Dp,
    onClick: (Int) -> Unit,
    onLeft: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacedBy),
        modifier =
            modifier
                .width(SubmenuDrawerWidth)
                .fillMaxHeight()
                .onPreviewKeyEvent {
                    // Left goes back up to the top level, at the current page's item
                    it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && onLeft()
                }.focusProperties {
                    onExit = {
                        // Keep up and down within the submenu
                        if (requestedFocusDirection == FocusDirection.Up ||
                            requestedFocusDirection == FocusDirection.Down
                        ) {
                            cancelFocusChange()
                        }
                    }
                }.focusGroup()
                .verticalScroll(rememberScrollState()),
    ) {
        // Leave room for the profile item at the top of the top level
        Spacer(Modifier.height(NavigationDrawerItemDefaults.ContainerHeightTwoLine))
        submenu.tabs.forEachIndexed { index, tab ->
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            NavigationDrawerItem(
                selected = false,
                onClick = { onClick(index) },
                interactionSource = interactionSource,
                modifier =
                    Modifier
                        .focusRequester(tab.tabFocusRequester)
                        .focusProperties { canFocus = focusable },
            ) {
                NavItemText(
                    tab.title.getString(),
                    navItemColor(index == submenu.selectedIndex, focused, drawerOpen),
                )
            }
        }
    }
}

// The submenu's width, and how much of the top level still shows to its left while it's open
// (dimmed to SubmenuParentAlpha). With no peek, the top level slides fully out of view
internal val SubmenuDrawerWidth = ExpandedDrawerItemWidth
internal val SubmenuParentPeek = 0.dp
internal const val SubmenuParentAlpha = .4f

val DrawerState.isOpen: Boolean get() = this.currentValue.isOpen

val DrawerValue.isOpen: Boolean get() = this == DrawerValue.Open
