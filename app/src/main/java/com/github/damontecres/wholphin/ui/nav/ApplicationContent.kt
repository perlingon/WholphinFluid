package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Top scrim configuration for text readability (clock, season tabs)
const val TOP_SCRIM_ALPHA = 0.55f
const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel() {
        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
        }
    }

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    // The nav drawer is open when the app starts, with focus in it
    val drawerState = rememberDrawerState(DrawerValue.Open)
    var drawerOpenOnStart by remember { mutableStateOf(true) }
    Box(
        modifier = modifier,
    ) {
        val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
        Backdrop(
            drawerIsOpen = drawerState.isOpen,
            backdropStyle = backdropStyle,
            enableTopScrim = enableTopScrim,
            viewModel = viewModel,
        )
        // While the top menu (search bar) is shown, the backdrop is dimmed along with the rest of the UI
        var topMenuHasFocus by remember { mutableStateOf(false) }
        val topMenuDim by animateFloatAsState(
            targetValue = if (topMenuHasFocus) TopMenuBackdropDim else 0f,
            animationSpec = spring(stiffness = DrawerAnimationStiffness),
            label = "topMenuBackdropDim",
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = topMenuDim)) },
        )
        val navDrawerListState = rememberLazyListState()
        // The nav drawer stays in place around the pages, so changing pages only animates the page.
        // Full screen pages hide it
        val current = navigationManager.backStack.lastOrNull() ?: Destination.Home()
        NavDrawer(
            destination = current,
            preferences = preferences,
            user = user,
            server = server,
            drawerState = drawerState,
            navDrawerListState = navDrawerListState,
            onClearBackdrop = viewModel::clearBackdrop,
            modifier = Modifier.fillMaxSize(),
            openOnStart = drawerOpenOnStart,
            onOpenedOnStart = { drawerOpenOnStart = false },
            hidden = current.fullScreen,
            onTopMenuFocusChange = { topMenuHasFocus = it },
        ) { contentModifier ->
            val submenuHost = LocalDrawerSubmenuHost.current
            NavDisplay(
                backStack = navigationManager.backStack,
                onBack = { navigationManager.goBack() },
                modifier = contentModifier,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = { key ->
                    key as Destination
                    val contentKey = "${key}_${server.id}_${user.id}"
                    NavEntry(key, contentKey = contentKey) {
                        CompositionLocalProvider(
                            LocalDestination provides key,
                            // Full screen pages keep their own tab rows
                            LocalDrawerSubmenuHost provides if (key.fullScreen) null else submenuHost,
                        ) {
                            DestinationContent(
                                destination = key,
                                preferences = preferences,
                                onClearBackdrop = viewModel::clearBackdrop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
            )
        }
    }
}

// How dark the backdrop gets while the top menu is shown
private const val TopMenuBackdropDim = .75f
