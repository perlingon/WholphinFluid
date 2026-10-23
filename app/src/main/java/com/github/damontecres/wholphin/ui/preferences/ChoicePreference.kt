package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.DialogPopupContent
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.theme.Spacing

@Composable
fun <T> ChoicePreference(
    title: String,
    summary: String?,
    possibleValues: List<T>,
    selectedIndex: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueDisplay: @Composable (index: Int, item: T) -> Unit = { _, item -> Text(item.toString()) },
    subtitleDisplay: (index: Int, item: T) -> @Composable (() -> Unit)? = { _, _ -> null },
) {
    var dialogParams by remember { mutableStateOf<DialogParams?>(null) }
    ClickPreference(
        title = title,
        summary = summary,
        onClick = {
            dialogParams =
                DialogParams(
                    title = title,
                    fromLongClick = false,
                    items =
                        possibleValues.mapIndexed { index, item ->
                            DialogItem(
                                headlineContent = { valueDisplay.invoke(index, item) },
                                leadingContent = {
                                    SelectedLeadingContent(index == selectedIndex)
                                },
                                supportingContent = subtitleDisplay.invoke(index, item),
                                onClick = {
                                    onValueChange.invoke(index)
                                    dialogParams = null
                                },
                            )
                        },
                )
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
    val panelPlacement = LocalPanelDialogPlacement.current
    AnimatedVisibility(dialogParams != null) {
        dialogParams?.let {
            if (panelPlacement != null) {
                PanelDialogPopup(
                    params = it,
                    placement = panelPlacement,
                    onDismissRequest = { dialogParams = null },
                )
            } else {
                DialogPopup(
                    showDialog = true,
                    title = it.title,
                    dialogItems = it.items,
                    onDismissRequest = { dialogParams = null },
                    waitToLoad = false,
                    dismissOnClick = false,
                )
            }
        }
    }
}

/**
 * Where choice lists open when the preferences are shown in a side panel, e.g. the subtitle style
 * panel during playback: over the panel, at the right edge of the screen, instead of in the middle
 *
 * @param width width of the panel
 * @param endInset gap between the panel and the right edge of the screen
 */
data class PanelDialogPlacement(
    val width: Dp,
    val endInset: Dp,
)

val LocalPanelDialogPlacement = compositionLocalOf<PanelDialogPlacement?> { null }

@Composable
private fun PanelDialogPopup(
    params: DialogParams,
    placement: PanelDialogPlacement,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Keep the video visible around the panel
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(end = placement.endInset),
        ) {
            DialogPopupContent(
                title = params.title,
                dialogItems = params.items,
                waiting = false,
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(placement.width)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                        .padding(Spacing.md),
            )
        }
    }
}
