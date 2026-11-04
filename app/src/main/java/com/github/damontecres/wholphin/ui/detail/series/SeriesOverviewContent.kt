package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.ExtrasRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.InCollectionsRow
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.nav.DrawerSubmenuHeading
import com.github.damontecres.wholphin.ui.nav.rememberDrawerSubmenu
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.theme.FluidLayout
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.ui.util.rememberDelayedNestedScroll
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PersonKind
import kotlin.time.Duration

@Composable
fun SeriesOverviewContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    seasonExtras: List<ExtrasItem>,
    chosenStreams: ChosenStreams?,
    peopleInEpisode: List<Person>,
    position: SeriesOverviewPosition,
    firstItemFocusRequester: FocusRequester,
    episodeRowFocusRequester: FocusRequester,
    castCrewRowFocusRequester: FocusRequester,
    guestStarRowFocusRequester: FocusRequester,
    extrasRowFocusRequester: FocusRequester,
    onChangeSeason: (Int) -> Unit,
    onFocusEpisode: (Int) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    overviewOnClick: () -> Unit,
    personOnClick: (Person) -> Unit,
    canDelete: (BaseItem) -> Boolean,
    onConfirmDelete: (BaseItem) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onChooseVersion: (BaseItem, MediaSourceInfo) -> Unit,
    goToSeries: () -> Unit,
    similar: List<BaseItem>,
    onClickSimilar: (BaseItem) -> Unit,
    onLongClickSimilar: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var selectedTabIndex by rememberSaveable(position.seasonTabIndex) { mutableIntStateOf(position.seasonTabIndex) }
    LaunchedEffect(selectedTabIndex) {
        logTab("series_overview", selectedTabIndex)
    }
    val tabRowFocusRequester = remember { FocusRequester() }

    val focusedEpisode =
        (episodes as? EpisodeList.Success)?.episodes?.getOrNull(position.episodeRowIndex)
    var pageHasFocus by remember { mutableStateOf(false) }
    var cardRowHasFocus by remember { mutableStateOf(false) }
    // Episode row: how dark the episodes that aren't focused get (0-1), and their size next to the
    // focused one
    val episodeDim = .5f
    val episodeUnfocusedScale = .9f
    val episodeFocusedScale = 1f
    // Gap between episodes, and the extra room on each side of the focused one (same as the player's row)
    val episodeGap = Spacing.sm
    val focusedEpisodeGap = Spacing.sm

    val scrollState = rememberScrollState()
    val scrollConnection = rememberDelayedNestedScroll()
    var requestFocusAfterSeason by remember { mutableStateOf(false) }

    val tabFocusRequesters = remember(seasons) { List(seasons.size) { FocusRequester() } }
    val contentFocusRequesters = remember(seasons) { List(seasons.size) { FocusRequester() } }
    val tabs =
        remember(seasons) {
            seasons.mapIndexed { index, season ->
                TabDetails(
                    title =
                        StringStringProvider(
                            season?.name
                                ?: season?.data?.indexNumber?.let { formatSeasonNumber(it) }
                                ?: "",
                        ),
                    tabFocusRequester = tabFocusRequesters[index],
                    contentFocusRequester = contentFocusRequesters[index],
                )
            }
        }

    val currentOnChangeSeason by rememberUpdatedState(onChangeSeason)
    val onClickSeason =
        remember {
            { index: Int ->
                selectedTabIndex = index
                currentOnChangeSeason(index)
                requestFocusAfterSeason = true
            }
        }
    // Beside the nav drawer, the seasons are a submenu in the drawer instead of a row on the page
    val seasonsInDrawer =
        rememberDrawerSubmenu(
            tabs = tabs,
            selectedIndex = selectedTabIndex,
            onSelect = onClickSeason,
            // The series' logo or title already says whose seasons these are, so the heading is
            // a way to the series' own page instead
            heading = DrawerSubmenuHeading(stringResource(R.string.go_to_series), goToSeries),
            // A series with only one season has no season menu, and no series page to go to
            enabled = seasons.size > 1,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(vertical = Spacing.md)
                    .focusGroup()
                    .nestedScroll(scrollConnection)
                    .verticalScroll(scrollState)
                    .onFocusChanged { pageHasFocus = it.hasFocus },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier =
                    Modifier
                        .focusGroup()
                        .bringIntoViewRequester(bringIntoViewRequester),
            ) {
                val paddingValues =
                    remember(preferences.appPreferences.interfacePreferences.showClock) {
                        if (preferences.appPreferences.interfacePreferences.showClock) {
                            PaddingValues(start = 0.dp, end = 184.dp)
                        } else {
                            PaddingValues(start = 0.dp, end = Spacing.md)
                        }
                    }
                if (!seasonsInDrawer) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        tabs = tabs,
                        onClick = onClickSeason,
                        modifier =
                            Modifier
                                .focusRequester(tabRowFocusRequester)
                                .padding(paddingValues)
                                .padding(bottom = Spacing.xs)
                                .fillMaxWidth(),
                    )
                }
                // Placed and sized like the title or logo on a movie's page. Without the season
                // tabs above it, it needs the header's own top gap (the page already has Spacing.md)
                TitleOrLogo(
                    item = series,
                    showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                    modifier =
                        Modifier
                            .ifElse(
                                seasonsInDrawer,
                                Modifier.padding(top = HeaderUtils.topPadding - Spacing.md),
                            ).fillMaxWidth(.75f)
                            .padding(start = HeaderUtils.startPadding),
                )
                FocusedEpisodeHeader(
                    preferences = preferences,
                    ep = focusedEpisode,
                    chosenStreams = chosenStreams,
                    overviewOnClick = overviewOnClick,
                    overviewOnFocus = {
                        if (it.isFocused) {
                            scope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(.6f),
                )

//                key(position.seasonTabIndex) {
                when (val eps = episodes) {
                    EpisodeList.Loading -> {
                        LoadingPage()
                    }

                    is EpisodeList.Error -> {
                        ErrorMessage(eps.message, eps.exception)
                    }

                    is EpisodeList.Success -> {
                        if (requestFocusAfterSeason) {
                            // Changing seasons, so move focus once the new episodes are loaded
                            LaunchedEffect(Unit) {
                                firstItemFocusRequester.tryRequestFocus()
                                requestFocusAfterSeason = false
                            }
                        }
                        val state = rememberLazyListState(position.episodeRowIndex)
                        var epPosition by rememberInt(position.episodeRowIndex)
                        LazyRow(
                            state = state,
                            horizontalArrangement = Arrangement.spacedBy(episodeGap),
                            contentPadding = PaddingValues(start = FluidLayout.rowStart(Spacing.md), end = Spacing.md),
                            modifier =
                                Modifier
                                    .focusRestorer(episodeRowFocusRequester)
//                                    .focusRequester(episodeRowFocusRequester)
                                    .onFocusChanged {
                                        cardRowHasFocus = it.hasFocus
                                    },
                        ) {
                            itemsIndexed(eps.episodes) { episodeIndex, episode ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val episodeFocused = interactionSource.collectIsFocusedAsState().value
                                if (episodeFocused) {
                                    onFocusEpisode.invoke(episodeIndex)
                                }
                                val highlighted =
                                    if (cardRowHasFocus) episodeFocused else episodeIndex == position.episodeRowIndex
                                val sideGap by animateDpAsState(if (episodeFocused) focusedEpisodeGap else 0.dp, label = "focusedEpisodeGap")
                                BannerCard(
                                    name = episode?.name,
                                    item = episode,
                                    aspectRatio =
                                        episode
                                            ?.aspectRatio
                                            ?.coerceAtLeast(AspectRatios.FOUR_THREE)
                                            ?: (AspectRatios.WIDE),
                                    cornerText = episode?.ui?.episodeCornerText,
                                    played = episode?.data?.userData?.played ?: false,
                                    playPercent =
                                        episode?.data?.userData?.playedPercentage
                                            ?: 0.0,
                                    onClick = {
                                        epPosition = episodeIndex
                                        if (episode != null) onClick.invoke(episode)
                                    },
                                    onLongClick = {
                                        epPosition = episodeIndex
                                        if (episode != null) onLongClick.invoke(episode)
                                    },
                                    modifier =
                                        Modifier
                                            .padding(horizontal = sideGap)
                                            .ifElse(
                                                episodeIndex == position.episodeRowIndex,
                                                Modifier
                                                    .focusRequester(firstItemFocusRequester),
                                            ).ifElse(
                                                episodeIndex == epPosition,
                                                Modifier.focusRequester(episodeRowFocusRequester),
                                            ).onFocusChanged {
                                                if (it.isFocused) {
                                                    scope.launch {
                                                        bringIntoViewRequester.bringIntoView()
                                                    }
                                                }
                                            }.onKeyEvent {
                                                if (episode != null && isPlayKeyUp(it)) {
                                                    onClick.invoke(episode)
                                                    return@onKeyEvent true
                                                }
                                                return@onKeyEvent false
                                            },
                                    interactionSource = interactionSource,
                                    cardHeight = 120.dp,
                                    useSeriesForPrimary = false,
                                    // The focused episode (or the selected one while the row isn't
                                    // focused) stays bright and full size, the others are darker and smaller
                                    dimAmount = if (highlighted || !pageHasFocus) 0f else episodeDim,
                                    scale = CardDefaults.scale(scale = episodeUnfocusedScale, focusedScale = episodeFocusedScale),
                                )
                            }
                        }
                    }
//                    }
                }

                focusedEpisode?.let { ep ->
                    FocusedEpisodeFooter(
                        preferences = preferences,
                        ep = ep,
                        chosenStreams = chosenStreams,
                        playOnClick = playOnClick,
                        moreOnClick = moreOnClick,
                        watchOnClick = {
                            watchOnClick.invoke()
                            episodeRowFocusRequester.tryRequestFocus()
                        },
                        favoriteOnClick = favoriteOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                scope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        canDelete = canDelete.invoke(ep),
                        onConfirmDelete = { onConfirmDelete.invoke(ep) },
                        onChooseVersion = { onChooseVersion.invoke(ep, it) },
                        modifier =
                            Modifier
                                .padding(top = Spacing.xs)
                                .fillMaxWidth(),
                    )
                }
            }

            val (guestStars, castAndCrew) =
                remember(peopleInEpisode) {
                    peopleInEpisode.partition { it.type == PersonKind.GUEST_STAR }
                }

            AnimatedVisibility(
                visible = peopleInEpisode.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (castAndCrew.isNotEmpty()) {
                        PersonRow(
                            title = R.string.cast_and_crew,
                            people = castAndCrew,
                            onClick = personOnClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(castCrewRowFocusRequester),
                        )
                    }
                    if (guestStars.isNotEmpty()) {
                        PersonRow(
                            title = R.string.guest_stars,
                            people = guestStars,
                            onClick = personOnClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(guestStarRowFocusRequester),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = seasonExtras.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ExtrasRow(
                    extras = seasonExtras,
                    onClickItem = onClickExtra,
                    onLongClickItem = { _, _ -> },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(extrasRowFocusRequester),
                )
            }
            // The collections the series is in, below the cast and above similar titles
            InCollectionsRow(series.id, Modifier.fillMaxWidth())
            if (similar.isNotEmpty()) {
                ItemRow(
                    title = stringResource(R.string.more_like_this),
                    items = similar,
                    onClickItem = { _, item -> onClickSimilar(item) },
                    onLongClickItem = { _, item -> onLongClickSimilar(item) },
                    cardContent = { _, item, mod, onClick, onLongClick ->
                        SeasonCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                            showImageOverlay = true,
                            imageHeight = Cards.height2x3,
                            imageWidth = Dp.Unspecified,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
