package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.theme.Spacing

/**
 * A menu along the top of the screen, hidden until focused. It's laid out above the page, so
 * pressing up once the page has reached its top moves focus here; down goes back into the page
 */
@Composable
fun TopMenu(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = spring(stiffness = DrawerAnimationStiffness),
        label = "topMenuReveal",
    )
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = hasFocus) {
        focusManager.moveFocus(FocusDirection.Down)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier =
            modifier
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusGroup()
                .graphicsLayer {
                    // Slides down from above the screen as it's revealed
                    alpha = reveal
                    translationY = (reveal - 1f) * size.height
                }.padding(top = Spacing.md),
    ) {
        TopMenuItem(
            text = stringResource(R.string.search),
            onClick = onSearch,
        )
    }
}

@Composable
private fun TopMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Text(
        text = text.uppercase(),
        color = navItemColor(selected = false, focused = focused, drawerOpen = true),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = NavItemFontWeight,
        letterSpacing = NavItemLetterSpacing,
        maxLines = 1,
        modifier =
            modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}
