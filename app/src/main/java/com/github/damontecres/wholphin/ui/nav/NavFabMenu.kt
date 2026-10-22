package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.util.LocalClock

/**
 * Where pages start, measured from the left edge of the screen. The left nav drawer used to take
 * this space; now that navigation lives in the top-right menu it can be made smaller (needs a Run,
 * and moves every page's left edge). Examples: 72.dp (same as with the drawer), 48.dp, 24.dp
 */
internal val PageStartPadding = 72.dp

/**
 * The navigation menu in the top-right corner, in the style of a Material FAB menu ("speed dial"):
 * a round button that opens into a list of items underneath it.
 *
 * Reach it with D-pad Up from the top of a page. OK opens it, focus then stays inside the menu
 * until Back or the button closes it again. [items] are the menu's entries, see [NavFabMenuItem].
 */
@Composable
fun BoxScope.NavFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showClock: Boolean,
    fabFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    items: @Composable ColumnScope.() -> Unit,
) {
    // Gap between the button and the top/right edges of the screen. Examples: 16.dp, 24.dp, 32.dp
    val edgeGap = 20.dp
    // Size of the round button. Examples: 48.dp, 56.dp (Material FAB), 64.dp
    val fabSize = 48.dp
    // Size of the icon inside the button. Examples: 20.dp, 24.dp, 28.dp
    val fabIconSize = 24.dp
    // Gap between the clock and the button. Examples: 8.dp, 16.dp, 24.dp
    val clockGap = 16.dp
    // Gap between the button and the first item, and between items. Examples: 8.dp, 12.dp, 16.dp
    val itemGap = 10.dp
    // How dark the shade behind the open menu gets, at the right edge. Examples: .5f, .7f, .85f
    val scrimOpacity = .75f
    // How far in from the right the shade reaches, as a share of the screen. Examples: .3f, .5f, 1f
    val scrimReach = .5f
    // Room kept around the button and items so they aren't cut off when they grow with focus
    val growRoom = 8.dp

    if (expanded) {
        // Shade behind the open menu, darkest at the right edge so the items read over the backdrop
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            (1f - scrimReach) to Color.Transparent,
                            1f to Color.Black.copy(alpha = scrimOpacity),
                        ),
                    ),
        )
    }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(itemGap),
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(top = edgeGap, end = (edgeGap - growRoom).coerceAtLeast(0.dp))
                // While open, focus can't leave the menu; Back or the button closes it
                .focusProperties {
                    onExit = {
                        if (expanded) cancelFocusChange()
                    }
                }.focusGroup(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(clockGap),
            modifier = Modifier.padding(end = growRoom),
        ) {
            if (showClock) {
                val timeString by LocalClock.current.timeString
                Text(
                    text = timeString,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Surface(
                onClick = { onExpandedChange(!expanded) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor =
                            if (expanded) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = .6f)
                            },
                        contentColor =
                            if (expanded) {
                                MaterialTheme.colorScheme.inverseOnSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                modifier =
                    Modifier
                        .size(fabSize)
                        .focusRequester(fabFocusRequester),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = stringResource(R.string.navigation_menu),
                    modifier =
                        Modifier
                            .size(fabIconSize)
                            .align(Alignment.Center),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(itemGap),
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        // Lines the item icons up under the middle of the button (each pill has
                        // 6.dp of padding at its right end), plus room to grow with focus
                        .padding(
                            bottom = edgeGap,
                            start = growRoom * 2,
                            end = ((fabSize - NavFabItemIconSize) / 2 - 6.dp).coerceAtLeast(0.dp) + growRoom,
                        ),
                content = items,
            )
        }
    }
}

// Size of the round icon at the right end of each menu item. Examples: 32.dp, 36.dp, 40.dp
internal val NavFabItemIconSize = 36.dp

/**
 * One entry of the [NavFabMenu]: a pill with the label on the left and a round icon on the right.
 * [selected] marks the page you're on. [icon] draws the icon in the colour it's given.
 */
@Composable
fun NavFabMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    dimmed: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    // How see-through the pill is when not focused. Examples: .5f, .7f, .9f
    val pillOpacity = if (dimmed) .45f else .7f
    // How much the focused item grows. Examples: 1f (not at all), 1.05f, 1.1f
    val focusedScale = 1.05f
    // Width of the ring around the page you're on. Examples: 1.dp, 2.dp, 3.dp
    val selectedRingWidth = 2.dp

    val focused by interactionSource.collectIsFocusedAsState()
    val selectedBorder =
        if (selected) {
            Border(
                border = BorderStroke(selectedRingWidth, MaterialTheme.colorScheme.border),
                shape = CircleShape,
            )
        } else {
            Border.None
        }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = pillOpacity),
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = selectedBorder,
                focusedBorder = selectedBorder,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                if (subtext != null) {
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            val iconColor =
                when {
                    focused -> MaterialTheme.colorScheme.inverseOnSurface
                    selected -> MaterialTheme.colorScheme.border
                    else -> MaterialTheme.colorScheme.onSurface
                }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(NavFabItemIconSize)
                        .background(
                            color = iconColor.copy(alpha = .12f),
                            shape = CircleShape,
                        ),
            ) {
                icon(iconColor)
            }
        }
    }
}
