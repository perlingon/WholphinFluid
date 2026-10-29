package com.github.damontecres.wholphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.damontecres.wholphin.ui.components.TabDetails

/**
 * A page's tabs, shown as a second level of the nav drawer instead of as a tab row on the page
 */
@Stable
class DrawerSubmenu(
    val tabs: List<TabDetails>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit,
)

/**
 * Holds the submenu of the page shown beside a nav drawer. Each page (nav entry) has its own drawer,
 * so this only ever holds the tabs of that one page
 */
@Stable
class DrawerSubmenuHost {
    var submenu by mutableStateOf<DrawerSubmenu?>(null)
        private set
    private var owner: Any? = null

    fun set(
        owner: Any,
        submenu: DrawerSubmenu,
    ) {
        this.owner = owner
        this.submenu = submenu
    }

    fun clear(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            this.submenu = null
        }
    }
}

val LocalDrawerSubmenuHost = staticCompositionLocalOf<DrawerSubmenuHost?> { null }

/**
 * Show [tabs] in the nav drawer's submenu while this is composed and [enabled]
 *
 * @return true when the tabs are shown in the nav drawer, so the page shouldn't show its own tab row.
 * False when the page isn't shown beside a nav drawer (full screen pages)
 */
@Composable
fun rememberDrawerSubmenu(
    tabs: List<TabDetails>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
): Boolean {
    val host = LocalDrawerSubmenuHost.current ?: return false
    val owner = remember { Any() }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val select = remember { { index: Int -> currentOnSelect(index) } }
    val show = enabled && selectedIndex in tabs.indices
    LaunchedEffect(host, show, tabs, selectedIndex) {
        if (show) {
            host.set(owner, DrawerSubmenu(tabs, selectedIndex, select))
        } else {
            host.clear(owner)
        }
    }
    DisposableEffect(host) {
        onDispose { host.clear(owner) }
    }
    return true
}
