package com.github.damontecres.wholphin.ui.nav

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.AnimatedTitles
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber

/**
 * Prepares backdrop backgrounds ahead of time, so that by the time an item gets focus its baked
 * background is usually ready and the transition is a plain crossfade between two finished images.
 *
 * Cards ask for their item's backdrop as they appear on screen ([PrebakeBackdrop]). Requests are
 * worked through one at a time in the background, most recent first, so what was scrolled into
 * view last is prepared first. Pages therefore fill in from what is visible, and more is added as
 * you scroll.
 */
object BackdropPrebaker {
    // How many requests may wait at once. Older ones are dropped when more arrive, which keeps
    // the work bounded when scrolling quickly through a long page
    private const val MAX_WAITING = 40

    // Pause between two backdrops, to leave the device room for everything else
    private const val PAUSE_MS = 40L

    // Image address and whether the title is animated
    private val waiting = ArrayDeque<Pair<String, Boolean>>()
    private val done = LruCache<String, Boolean>(400)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var baker: (suspend (String, Boolean) -> Unit)? = null

    /**
     * Sets what actually bakes a backdrop. The [Backdrop] on screen provides it, because the
     * recipe (theme, glows, blur layers, vignettes) lives there. Null when no backdrop is shown
     */
    fun setBaker(newBaker: (suspend (String, Boolean) -> Unit)?) {
        synchronized(this) {
            baker = newBaker
        }
        if (newBaker != null) startWorker()
    }

    /**
     * Asks for the background of this backdrop image to be prepared
     */
    fun request(
        imageUrl: String?,
        animated: Boolean,
    ) {
        if (imageUrl.isNullOrBlank()) return
        synchronized(this) {
            if (done.get(imageUrl) != null) return
            waiting.removeAll { it.first == imageUrl }
            waiting.addFirst(imageUrl to animated)
            while (waiting.size > MAX_WAITING) waiting.removeLast()
        }
        startWorker()
    }

    /**
     * Asks for the backdrops of the first few items to be prepared, with the first item done
     * first. For lists that are known before their cards are on screen, such as home rows that
     * load behind the loading spinner
     */
    suspend fun requestFirst(
        items: List<BaseItem?>,
        imageUrlService: ImageUrlService,
        count: Int = 4,
    ) {
        // Each request goes to the front of the queue, so the first item is asked for last
        items.filterNotNull().take(count).asReversed().forEach { item ->
            request(backdropImageUrl(item, imageUrlService), AnimatedTitles.isAnimated(item))
        }
    }

    private fun startWorker() {
        synchronized(this) {
            if (worker?.isActive == true) return
            worker =
                scope.launch {
                    while (true) {
                        val (request, bake) =
                            synchronized(this@BackdropPrebaker) {
                                val currentBaker = baker
                                if (currentBaker == null || waiting.isEmpty()) {
                                    worker = null
                                    return@launch
                                }
                                waiting.removeFirst() to currentBaker
                            }
                        try {
                            bake(request.first, request.second)
                            done.put(request.first, true)
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not prepare backdrop background")
                        }
                        delay(PAUSE_MS)
                    }
                }
        }
    }
}

/**
 * Put in a card to have its item's backdrop background prepared ahead of time
 */
@Composable
fun PrebakeBackdrop(item: BaseItem?) {
    val imageUrlService = LocalImageUrlService.current
    LaunchedEffect(item?.id) {
        if (item != null) {
            BackdropPrebaker.request(backdropImageUrl(item, imageUrlService), AnimatedTitles.isAnimated(item))
        }
    }
}

// The same address the backdrop itself will use when this item gets focus
private fun backdropImageUrl(
    item: BaseItem,
    imageUrlService: ImageUrlService,
): String? =
    if (item.type == BaseItemKind.GENRE) {
        item.imageUrlOverride
    } else {
        imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
    }

/**
 * Hands the prebaker the current recipe for baking a backdrop, and takes it away again when the
 * backdrop leaves the screen. Lives here rather than in Backdrop.kt because Live Edit cannot
 * update a file that contains an onDispose block
 */
@Composable
internal fun RegisterBackdropBaker(baker: suspend (String, Boolean) -> Unit) {
    SideEffect { BackdropPrebaker.setBaker(baker) }
    DisposableEffect(Unit) {
        onDispose { BackdropPrebaker.setBaker(null) }
    }
}
