package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
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
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
 * Display [DestinationContent] with the navigation menu ([NavFabMenu]) in the top-right corner.
 *
 * [drawerState] is open while the menu is open
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    drawerState: DrawerState,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server.id}_${user.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
) {
    LaunchedEffect(Unit) { viewModel.updateSelectedIndex() }
    val context = LocalContext.current

    val fabFocusRequester = remember { FocusRequester() }
    val selectedFocusRequester = remember { FocusRequester() }
    val homeFocusRequester = remember { FocusRequester() }

    val expanded = drawerState.isOpen

    fun setExpanded(value: Boolean) {
        if (!value) {
            // Move focus to the button before the items go away, so it isn't lost
            fabFocusRequester.tryRequestFocus()
            viewModel.setShowMore(false)
        }
        drawerState.setValue(if (value) DrawerValue.Open else DrawerValue.Closed)
    }

    // A page starts with the menu closed, even if it was open on the page before
    LaunchedEffect(Unit) { drawerState.setValue(DrawerValue.Closed) }

    // When the menu opens, focus the page you're on, or Home
    LaunchedEffect(expanded) {
        if (expanded && !selectedFocusRequester.tryRequestFocus()) {
            homeFocusRequester.tryRequestFocus()
        }
    }

    // If the user presses back while on the home page, open the menu, another back press will quit the app
    BackHandler(enabled = (!expanded && destination is Destination.Home)) {
        fabFocusRequester.tryRequestFocus()
        setExpanded(true)
    }
    BackHandler(enabled = expanded) {
        setExpanded(false)
    }
    val serviceState by viewModel.serviceState.collectAsState()
    val state by viewModel.state.collectAsState()
    val moreExpanded = state.moreExpanded
    // A negative index is a built-in page, >=0 is a library
    val selectedIndex = state.selectedIndex

    // Registered after the one above so that Back folds "More" away before closing the menu
    BackHandler(enabled = moreExpanded && expanded) {
        viewModel.setShowMore(false)
    }

    // Closes the menu, then runs the item's action
    fun select(action: () -> Unit) {
        setExpanded(false)
        action.invoke()
    }

    fun Modifier.selectedFocus(isSelected: Boolean) = ifElse(isSelected, Modifier.focusRequester(selectedFocusRequester))

    Box(modifier = modifier) {
        DestinationContent(
            destination = destination,
            preferences = preferences,
            onClearBackdrop = onClearBackdrop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = PageStartPadding, end = 16.dp),
        )
        NavFabMenu(
            expanded = expanded,
            onExpandedChange = { setExpanded(it) },
            showClock = preferences.appPreferences.interfacePreferences.showClock,
            fabFocusRequester = fabFocusRequester,
        ) {
            if (serviceState.nowPlayingEnabled) {
                NavFabMenuItem(
                    label = stringResource(R.string.now_playing),
                    subtext = serviceState.nowPlayingTitle,
                    selected = selectedIndex == NOW_PLAYING_INDEX,
                    icon = { color -> FabVectorIcon(Icons.Default.PlayArrow, color) },
                    onClick = {
                        select {
                            viewModel.setIndex(NOW_PLAYING_INDEX)
                            viewModel.navigationManager.navigateTo(Destination.NowPlaying)
                        }
                    },
                    modifier = Modifier.selectedFocus(selectedIndex == NOW_PLAYING_INDEX),
                )
            }
            NavFabMenuItem(
                label = stringResource(R.string.search),
                selected = selectedIndex == SEARCH_INDEX,
                icon = { color -> FabVectorIcon(Icons.Default.Search, color) },
                onClick = {
                    select {
                        viewModel.setIndex(SEARCH_INDEX)
                        viewModel.navigationManager.navigateToFromDrawer(Destination.Search())
                    }
                },
                modifier = Modifier.selectedFocus(selectedIndex == SEARCH_INDEX),
            )
            NavFabMenuItem(
                label = stringResource(R.string.home),
                selected = selectedIndex == HOME_INDEX,
                icon = { color -> FabVectorIcon(Icons.Default.Home, color) },
                onClick = {
                    select {
                        viewModel.setIndex(HOME_INDEX)
                        if (destination is Destination.Home) {
                            viewModel.navigationManager.reloadHome()
                            onClearBackdrop.invoke()
                        } else {
                            viewModel.navigationManager.goToHome()
                        }
                    }
                },
                modifier =
                    Modifier
                        .focusRequester(homeFocusRequester)
                        .selectedFocus(selectedIndex == HOME_INDEX),
            )
            serviceState.items.forEachIndexed { index, item ->
                NavFabMenuItem(
                    label = item.name(context),
                    selected = selectedIndex == index,
                    icon = { color -> FabLibraryIcon(item, color) },
                    onClick = { select { viewModel.onClickDrawerItem(index, item) } },
                    modifier = Modifier.selectedFocus(selectedIndex == index),
                )
            }
            if (serviceState.moreItems.isNotEmpty()) {
                val moreIndex = serviceState.items.size
                NavFabMenuItem(
                    label = NavDrawerItem.More.name(context),
                    selected = false,
                    icon = { color ->
                        FabVectorIcon(
                            if (moreExpanded) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowLeft,
                            color,
                        )
                    },
                    // Folds the extra libraries in or out, the menu stays open
                    onClick = { viewModel.onClickDrawerItem(moreIndex, NavDrawerItem.More) },
                )
                if (moreExpanded) {
                    serviceState.moreItems.forEachIndexed { index, item ->
                        val adjustedIndex = index + serviceState.items.size
                        NavFabMenuItem(
                            label = item.name(context),
                            selected = selectedIndex == adjustedIndex,
                            dimmed = true,
                            icon = { color -> FabLibraryIcon(item, color) },
                            onClick = { select { viewModel.onClickDrawerItem(adjustedIndex, item) } },
                            modifier = Modifier.selectedFocus(selectedIndex == adjustedIndex),
                        )
                    }
                }
            }
            NavFabMenuItem(
                label = stringResource(R.string.settings),
                selected = false,
                icon = { color -> FabVectorIcon(Icons.Default.Settings, color) },
                onClick = {
                    select {
                        viewModel.navigationManager.navigateTo(
                            Destination.Settings(PreferenceScreenOption.BASIC),
                        )
                    }
                },
            )
            val userImageUrl = remember(user) { viewModel.getUserImage(user) }
            NavFabMenuItem(
                label = user.name ?: user.id.toString(),
                subtext = server.name ?: server.url,
                selected = false,
                icon = {
                    UserIconCardImage(
                        id = user.id,
                        name = user.name,
                        imageUrl = userImageUrl,
                        modifier = Modifier.size(NavFabItemIconSize),
                    )
                },
                onClick = {
                    select { viewModel.navigateToSetup(SetupDestination.UserList(server)) }
                },
            )
        }
    }
}

@Composable
private fun FabVectorIcon(
    icon: ImageVector,
    color: Color,
) {
    Icon(
        icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(DrawerIconSize),
    )
}

/**
 * The icon of a library or built-in page, the same ones the nav drawer used
 */
@Composable
private fun FabLibraryIcon(
    library: NavDrawerItem,
    color: Color,
) {
    val useFont = library !is ServerNavDrawerItem || library.type != CollectionType.LIVETV
    val icon = remember(library) { navItemIcon(library) }
    if (useFont) {
        Text(
            text = stringResource(icon),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontFamily = FontAwesome,
            color = color,
        )
    } else {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(DrawerIconSize),
        )
    }
}

private fun navItemIcon(library: NavDrawerItem): Int =
    when (library) {
        NavDrawerItem.Favorites -> {
            R.string.fa_heart
        }

        NavDrawerItem.More -> {
            R.string.fa_ellipsis
        }

        NavDrawerItem.Discover -> {
            R.string.fa_magnifying_glass_plus
        }

        is ServerNavDrawerItem -> {
            when (library.type) {
                CollectionType.MOVIES -> R.string.fa_film
                CollectionType.TVSHOWS -> R.string.fa_tv
                CollectionType.HOMEVIDEOS -> R.string.fa_video
                CollectionType.LIVETV -> R.drawable.gf_dvr
                CollectionType.MUSIC -> R.string.fa_music
                CollectionType.BOXSETS -> R.string.fa_open_folder
                CollectionType.PLAYLISTS -> R.string.fa_list_ul
                else -> R.string.fa_film
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
        leadingContent = {
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
            )
        },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = user.name ?: user.id.toString(),
            maxLines = 1,
        )
    }
}

@Composable
fun NavigationDrawerScope.IconNavItem(
    text: String,
    icon: ImageVector,
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
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(DrawerIconSize),
            )
        },
        supportingContent =
            subtext?.let {
                {
                    Text(
                        text = it,
                        maxLines = 1,
                    )
                }
            },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = text,
            maxLines = 1,
        )
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
    containerColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val useFont = library !is ServerNavDrawerItem || library.type != CollectionType.LIVETV
    val icon = remember(library) { navItemIcon(library) }
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        colors =
            NavigationDrawerItemDefaults.colors(
                containerColor = containerColor,
            ),
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (useFont) {
                    Text(
                        text = stringResource(icon),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontFamily = FontAwesome,
                        color = color,
                        modifier = Modifier,
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(DrawerIconSize),
                    )
                }
            }
        },
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
        Text(
            modifier = Modifier,
            text = library.name(context),
            maxLines = 1,
        )
    }
}

@Composable
@ReadOnlyComposable
fun navItemColor(
    selected: Boolean,
    focused: Boolean,
    drawerOpen: Boolean,
): Color {
    val theme = LocalTheme.current
    if (theme == AppThemeColors.OLED_BLACK) {
        return when {
            selected && focused -> Color.Black
            selected && !drawerOpen -> Color.White.copy(alpha = .5f)
            selected && drawerOpen -> Color.White.copy(alpha = .85f)
            focused -> Color.Black.copy(alpha = .5f)
            drawerOpen -> Color(0xFF707070)
            else -> Color(0xFF505050).copy(alpha = .66f)
        }
    } else {
        val alpha =
            when {
                drawerOpen -> .85f
                selected && !drawerOpen -> .5f
                else -> .2f
            }
        return when {
            selected && focused -> {
                when (theme) {
                    AppThemeColors.UNRECOGNIZED,
                    AppThemeColors.PURPLE,
                    AppThemeColors.BLUE,
                    AppThemeColors.GREEN,
                    AppThemeColors.ORANGE,
                    AppThemeColors.RED,
                    AppThemeColors.BROWN,
                    AppThemeColors.VIVID,
                    AppThemeColors.MUTED,
                    AppThemeColors.DARK,
                    AppThemeColors.DARKEST,
                    -> MaterialTheme.colorScheme.border

                    AppThemeColors.BOLD_BLUE,
                    AppThemeColors.OLED_BLACK,
                    -> MaterialTheme.colorScheme.primary
                }
            }

            selected -> {
                MaterialTheme.colorScheme.border
            }

            focused -> {
                LocalContentColor.current
            }

            else -> {
                MaterialTheme.colorScheme.onSurface
            }
        }.copy(alpha = alpha)
    }
}

val DrawerState.isOpen: Boolean get() = this.currentValue.isOpen

val DrawerValue.isOpen: Boolean get() = this == DrawerValue.Open
