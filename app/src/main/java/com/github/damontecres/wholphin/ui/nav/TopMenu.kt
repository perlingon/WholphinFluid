package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.search.SearchTypeOptionsDialog
import com.github.damontecres.wholphin.ui.search.SearchViewModel
import com.github.damontecres.wholphin.ui.search.SearchViewOptionsDialog
import com.github.damontecres.wholphin.ui.theme.Spacing

/**
 * The search bar along the top of the screen, hidden until focused. It's laid out above the page,
 * so pressing up once the page has reached its top moves focus here, and the rest of the UI moves
 * down out of its way (see [reveal]). Left and right move between the voice button, the text box
 * and the search options; down or back go back into the page
 *
 * @param reveal how far the bar is shown, from 0 (hidden) to 1
 * @param onSearch open the search page for a query
 */
@Composable
fun TopMenu(
    reveal: Float,
    preferences: UserPreferences,
    onSearch: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(key = "top_menu_search"),
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by viewModel.state.collectAsState()
    val seerrActive by viewModel.seerrActive.collectAsState()
    val prefs =
        viewModel.userPreferencesService.flow
            .collectAsState(preferences)
            .value.appPreferences.interfacePreferences.searchPreferences

    var query by rememberSaveable { mutableStateOf("") }
    var hasFocus by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var showFilterTypeDialog by rememberSaveable { mutableStateOf(false) }
    val textFieldFocusRequester = remember { FocusRequester() }

    fun search(searchQuery: String) {
        if (searchQuery.isBlank()) return
        isSearchActive = false
        keyboardController?.hide()
        onSearch(searchQuery)
        query = ""
    }

    // Back closes the keyboard first, then leaves the bar like down does
    BackHandler(enabled = hasFocus) {
        if (isTextFieldFocused && isSearchActive) {
            isSearchActive = false
            keyboardController?.hide()
        } else {
            focusManager.moveFocus(FocusDirection.Down)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .onFocusChanged {
                    hasFocus = it.hasFocus
                    onFocusChange(it.hasFocus)
                    if (!it.hasFocus) viewModel.voiceInputManager.stopListening()
                }.focusProperties {
                    // Only down leaves the bar, so left and right stay within it
                    onExit = {
                        if (requestedFocusDirection != FocusDirection.Down) cancelFocusChange()
                    }
                }.focusRestorer(textFieldFocusRequester)
                .focusGroup()
                .graphicsLayer {
                    // Slides down from above the screen as it's revealed
                    alpha = reveal
                    translationY = (reveal - 1f) * size.height
                }.padding(top = Spacing.md),
    ) {
        if (prefs.showVoiceSearchButton) {
            VoiceSearchButton(
                onSpeechResult = { spokenText -> search(spokenText) },
                voiceInputManager = viewModel.voiceInputManager,
            )
        }
        SearchEditTextBox(
            value = query,
            onValueChange = {
                isSearchActive = true
                query = it
            },
            onSearchClick = { search(query) },
            readOnly = !isSearchActive,
            modifier =
                Modifier
                    .fillMaxWidth(.4f)
                    .focusRequester(textFieldFocusRequester)
                    .onFocusChanged {
                        isTextFieldFocused = it.isFocused
                        if (!it.isFocused) isSearchActive = false
                    }.onPreviewKeyEvent { event ->
                        // Until OK is pressed the text box is read only, so left and right move
                        // focus instead of the cursor
                        val isActivationKey = event.key in listOf(Key.DirectionCenter, Key.Enter)
                        if (event.type == KeyEventType.KeyUp && isActivationKey && !isSearchActive) {
                            isSearchActive = true
                            keyboardController?.show()
                            true
                        } else {
                            false
                        }
                    },
        )
        ExpandableFaButton(
            title = R.string.view_options,
            iconStringRes = R.string.fa_sliders,
            onClick = { showViewOptions = true },
        )
    }

    if (showViewOptions) {
        SearchViewOptionsDialog(
            combinedResults = prefs.combinedSearchResults,
            onCombinedResultsChange = viewModel::setCombinedResults,
            voiceSearchButtonVisible = prefs.showVoiceSearchButton,
            onVoiceSearchButtonVisibleChange = viewModel::setVoiceSearchButtonVisible,
            onClickFilterTypes = { showFilterTypeDialog = true },
            onDismissRequest = { showViewOptions = false },
        )
    }
    if (showFilterTypeDialog) {
        SearchTypeOptionsDialog(
            onDismissRequest = { showFilterTypeDialog = false },
            searchableTypes = state.possibleSearchableTypes,
            excludedSearchableTypes = state.excludedSearchableTypes,
            discoverAvailable = seerrActive,
            discoverEnabled = state.discoverEnabled,
            onClick = viewModel::onClickExcludeSearchableType,
            onClickDiscover = viewModel::onClickExcludeDiscover,
        )
    }
}
