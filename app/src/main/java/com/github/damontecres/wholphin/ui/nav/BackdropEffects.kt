package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.WholphinApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

// The heavy lifting behind the backdrop: composing the baked background and generating film grain.
// Kept apart from Backdrop.kt on purpose. While tuning values there with Live Edit, the code in
// that file runs far slower than normal, which froze the app when these loops lived in it.

/**
 * A square of random grey noise that repeats seamlessly, used as film grain. Neutral grey leaves
 * the picture untouched under an Overlay blend; lighter and darker specks lighten and darken it.
 *
 * @param contrast how far the specks stray from neutral grey, 0 to 1
 * @param clumping how much the grain gathers into larger, uneven patches instead of being evenly
 * fine, 0 to 1
 */
internal fun grainTile(
    contrast: Float,
    clumping: Float,
): ImageBitmap =
    synchronized(grainTiles) {
        grainTiles.getOrPut(contrast to clumping) { makeGrainTile(contrast, clumping) }
    }

// Tiles already generated, so each is only ever made once
private val grainTiles = HashMap<Pair<Float, Float>, ImageBitmap>()

// Large enough that the repeat is not noticeable on screen
internal const val GRAIN_TILE_SIZE = 1024

// Size, in grains, of the uneven patches that clumping adds
private const val GRAIN_CLUMP_SIZE = 4

private fun makeGrainTile(
    contrast: Float,
    clumping: Float,
): ImageBitmap {
    val size = GRAIN_TILE_SIZE
    val random = java.util.Random(7)
    val spread = 127f * contrast.coerceIn(0f, 1f) / 2f
    val clump = clumping.coerceIn(0f, 1f)

    // A coarser layer of noise, read smoothly, gives patches where the grain runs lighter or
    // darker and makes neighbouring grains vary in size
    val coarseSize = size / GRAIN_CLUMP_SIZE
    val coarse = FloatArray(coarseSize * coarseSize) { random.nextGaussian().toFloat() }

    fun coarseAt(
        x: Int,
        y: Int,
    ): Float {
        val u = x.toFloat() / GRAIN_CLUMP_SIZE
        val v = y.toFloat() / GRAIN_CLUMP_SIZE
        val x0 = u.toInt() % coarseSize
        val y0 = v.toInt() % coarseSize
        val x1 = (x0 + 1) % coarseSize
        val y1 = (y0 + 1) % coarseSize
        val fx = u - u.toInt()
        val fy = v - v.toInt()
        val top = coarse[y0 * coarseSize + x0] * (1f - fx) + coarse[y0 * coarseSize + x1] * fx
        val bottom = coarse[y1 * coarseSize + x0] * (1f - fx) + coarse[y1 * coarseSize + x1] * fx
        return top * (1f - fy) + bottom * fy
    }

    val pixels =
        IntArray(size * size) { index ->
            val fine = random.nextGaussian().toFloat()
            // The smooth reading flattens the coarse layer, so it is boosted to keep its weight
            val mixed = fine * (1f - clump) + coarseAt(index % size, index / size) * 1.6f * clump
            val grey = (128 + mixed * spread).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

// Recently baked backgrounds, so going back to a backdrop that was already shown is instant
internal val bakedBackgrounds = BakedBackgrounds()

/**
 * Keeps baked backgrounds in memory and saves them as files, so they survive the app being closed.
 * A saved file only counts for the build that made it, because a code change can change the look.
 * Debug builds don't save files: with Live Edit the look changes without a new build
 */
internal class BakedBackgrounds {
    // Limited by size rather than count, so larger bakes can't fill the memory
    private val memory =
        object : LruCache<Any, ImageBitmap>(MEMORY_BYTES) {
            override fun sizeOf(
                key: Any,
                value: ImageBitmap,
            ): Int = value.width * value.height * 4
        }

    private val saveDisk = !BuildConfig.DEBUG
    private val folder by lazy { File(WholphinApplication.instance.cacheDir, "baked_backdrops") }
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /**
     * The background for this key if it is in memory
     */
    fun get(key: Any): ImageBitmap? = memory.get(key)

    /**
     * The background for this key from memory, or else from its saved file
     */
    suspend fun load(key: Any): ImageBitmap? {
        memory.get(key)?.let { return it }
        if (!saveDisk) return null
        return withContext(Dispatchers.IO) {
            val file = fileFor(key)
            if (!file.exists()) return@withContext null
            try {
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()?.also {
                    Timber.tag("BackdropBake").i("Loaded %dx%d from a saved file", it.width, it.height)
                    memory.put(key, it)
                    // Marks it as recently used, so it is kept when old files are cleared out
                    file.setLastModified(System.currentTimeMillis())
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read saved backdrop background")
                null
            }
        }
    }

    fun put(
        key: Any,
        image: ImageBitmap,
    ) {
        memory.put(key, image)
        if (!saveDisk) return
        writer.launch {
            try {
                folder.mkdirs()
                val file = fileFor(key)
                val temp = File(folder, file.name + ".tmp")
                temp.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 90, it) }
                temp.renameTo(file)
                trim()
            } catch (ex: Exception) {
                Timber.w(ex, "Could not save backdrop background")
            }
        }
    }

    // The file name is a fingerprint of the key and the build, so any change to the look gets
    // new files
    private fun fileFor(key: Any): File {
        val digest = MessageDigest.getInstance("SHA-1").digest("${BuildConfig.BUILD_TIME}|$key".toByteArray())
        return File(folder, digest.joinToString("") { "%02x".format(it) } + ".jpg")
    }

    /**
     * Removes every saved background, from memory and from disk
     */
    fun clear() {
        memory.evictAll()
        writer.launch { folder.listFiles()?.forEach { it.delete() } }
    }

    // Removes the least recently used files beyond the size limit, including those of older builds
    private fun trim() {
        val files = folder.listFiles { file -> file.name.endsWith(".jpg") } ?: return
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= diskBytes) break
            total -= file.length()
            file.delete()
        }
    }

    companion object {
        private const val MEMORY_BYTES = 40 * 1024 * 1024

        // Share of the image cache size setting that saved backgrounds may use on disk. The
        // image cache gets the rest, so the setting stays the total
        const val DISK_SHARE = 0.15

        // How much the saved backgrounds may take up on disk. Set from the image cache size
        // setting when the app starts
        @Volatile
        var diskBytes = 30L * 1024 * 1024
    }
}

/**
 * One tile of the baked background: a copy of the backdrop image, usually blurred, laid over an
 * area of the screen. The background is built from any number of these, drawn in order.
 *
 * Every tile has the same settings. Each has a default, so a tile only needs to state the ones
 * that differ.
 */
internal data class Tile(
    // The edge of the image this tile reflects, if any. It is left out when that edge is one
    // flat colour. Null = always drawn
    val reflects: ImageEdge? = null,
    // Where the tile's left and top edges sit, as shares of the screen: 0f, 0f is the top-left
    // corner, 1f, 1f the bottom-right. Either may be negative or above 1 to reach off screen
    val left: Float = 0f,
    val top: Float = 0f,
    // Width as a share of the screen's width, and height as a share of the screen's height. The
    // picture is squeezed or pulled to fit, so the two together set its shape
    val width: Float = 1f,
    val height: Float = 1f,
    // Mirrored left to right, in place
    val flip: Boolean = false,
    // Upside down, in place
    val flipVertical: Boolean = false,
    // Turned about its own middle, in degrees: positive = clockwise
    val rotation: Float = 0f,
    // Slanted about its own middle, in degrees. skewX leans the vertical edges (positive = the
    // bottom moves right), skewY the horizontal edges (positive = the right side moves down)
    val skewX: Float = 0f,
    val skewY: Float = 0f,
    // The picture pulled out towards one edge, see Stretch. Null = none
    val stretch: Stretch? = null,
    // Width in pixels of the version of the image that is stretched over the tile: lower = blurrier
    val resolution: Int = 64,
    // Extra blur on this tile alone, as a share of the screen's width: 0 = none
    val smooth: Float = 0f,
    // How it is drawn, bottom to top, as blend mode to opacity. Several lines combine modes
    val layers: List<Pair<BlendMode, Float>> = listOf(BlendMode.SrcOver to 1f),
    // Where it sits relative to the group. With both false it is part of the group, which is
    // blurred together as one and blended on as a whole.
    // behind = drawn first, straight onto the glows, so it ends up under the group
    // onTop = drawn last, straight onto the result, so it ends up over the group
    val behind: Boolean = false,
    val onTop: Boolean = false,
    // Soft edges, see Feather. Null = use the default given where the tiles are set up
    val feather: Feather? = null,
    // How strongly it is drawn when any edge of the image is one flat colour, as a share of its
    // layers' opacity: 1f = unchanged, 0.5f = half, 0f = left out
    val flatEdgeOpacity: Float = 1f,
)

/**
 * Pulls a tile's picture out towards one of its edges, as the tile appears on screen (after any
 * flip): the part nearest that edge is stretched the most, the far edge is left as it is. Like a
 * reflection smeared outwards.
 *
 * @param edge the edge the picture is pulled towards
 * @param strength 0f = none, 1f = clearly stretched, 2f = strongly, 4f = very strongly
 */
internal data class Stretch(
    val edge: StretchEdge,
    val strength: Float,
)

internal enum class StretchEdge { LEFT, TOP, RIGHT, BOTTOM }

/**
 * [image] drawn at [size] with its picture pulled towards [edge] (in the image's own
 * coordinates), through a grid of points that the picture is stretched over
 */
private fun stretched(
    image: ImageBitmap,
    size: IntSize,
    edge: StretchEdge,
    strength: Float,
): ImageBitmap {
    val output = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    // Points across and down: plenty for a smooth curve on these small, blurry pictures
    val columns = 16
    val rows = 16
    val power = 1f + strength.coerceAtLeast(0f)
    val horizontal = edge == StretchEdge.LEFT || edge == StretchEdge.RIGHT
    val verts = FloatArray((columns + 1) * (rows + 1) * 2)
    var i = 0
    for (row in 0..rows) {
        for (column in 0..columns) {
            var u = column.toFloat() / columns
            var v = row.toFloat() / rows
            // Measured from the stretched edge, a point of the picture at distance d (0..1)
            // lands at d^(1 / power): close to the edge, a little picture covers a lot of tile
            if (horizontal) {
                u =
                    if (edge == StretchEdge.LEFT) {
                        u.pow(1f / power)
                    } else {
                        1f - (1f - u).pow(1f / power)
                    }
            } else {
                v =
                    if (edge == StretchEdge.TOP) {
                        v.pow(1f / power)
                    } else {
                        1f - (1f - v).pow(1f / power)
                    }
            }
            verts[i++] = u * size.width
            verts[i++] = v * size.height
        }
    }
    val source = image.asAndroidBitmap()
    // The picture is split evenly over the grid, whatever its own size
    android.graphics.Canvas(output).apply {
        drawBitmapMesh(
            source,
            columns,
            rows,
            verts,
            0,
            null,
            0,
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
    }
    return output.asImageBitmap()
}

/**
 * Soft edges on a tile: how far in from each side it fades out, as a share of its own width
 * (left and right) or height (top and bottom). 0f = a hard edge, 0.2f = the outer fifth fades,
 * 0.5f = fades all the way to the middle, 1f = across the whole tile. Above 1 the fade is longer
 * than the tile and never gets solid (2f = still half see-through at the far side). Fades from
 * opposite sides multiply where they overlap. The sides are as the tile appears on screen, after any
 * flip. Written like CSS margins:
 * - Feather(0.2f): all four sides
 * - Feather(0.1f, 0.2f): top and bottom, then left and right
 * - Feather(0.1f, 0.2f, 0f): top, then left and right, then bottom
 * - Feather(0.1f, 0f, 0.2f, 0.3f): top, right, bottom, left
 */
internal data class Feather(
    val top: Float,
    val right: Float,
    val bottom: Float,
    val left: Float,
) {
    constructor(all: Float) : this(all, all, all, all)
    constructor(vertical: Float, horizontal: Float) : this(vertical, horizontal, vertical, horizontal)
    constructor(top: Float, horizontal: Float, bottom: Float) : this(top, horizontal, bottom, horizontal)

    val any: Boolean get() = top > 0f || right > 0f || bottom > 0f || left > 0f
}

/**
 * Composes the backdrop's background once, into a small image: the theme background and colour
 * glows, then each tile with its blend modes, then
 * optional smoothing, then the overlay (vignettes).
 * The result costs a single layer on screen.
 *
 * @return the image, or null if it could not be made
 */
internal suspend fun bakeBackdropBackground(
    context: Context,
    imageUrl: Any?,
    width: Int,
    height: Int,
    density: Density,
    layoutDirection: LayoutDirection,
    imageAspect: Float,
    tiles: List<Tile>,
    flatEdgeStrip: Float,
    flatEdgeTolerance: Float,
    flatEdgeDarkLevel: Float,
    flatEdgeDarkTolerance: Float,
    flatEdgeTint: Float,
    flatEdgeTintSolid: Float,
    flatEdgeTintReach: Float,
    imageLeft: Float,
    imageRight: Float,
    drawBackground: DrawScope.() -> Unit,
    drawOverlay: DrawScope.() -> Unit,
    groupSmoothRadius: Int,
    groupLayers: List<Pair<BlendMode, Float>>,
    groupOpacity: Float,
    smoothRadius: Int,
    outlineCopies: Boolean,
    // Development aid: leaves out all smoothing, see debugNoSmoothing in Backdrop
    noSmoothing: Boolean = false,
): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val started = System.nanoTime()
        try {
            // Tiles that reflect an edge of the image which is one flat colour would only add a
            // flat patch, so they are left out
            val flatEdges =
                if (imageUrl != null && tiles.any { it.reflects != null }) {
                    findFlatEdges(
                        context,
                        imageUrl,
                        flatEdgeStrip,
                        flatEdgeTolerance,
                        flatEdgeDarkLevel,
                        flatEdgeDarkTolerance,
                    )
                } else {
                    emptyMap()
                }
            val shownTiles = tiles.filter { it.reflects == null || it.reflects !in flatEdges }
            if (imageUrl != null) flatEdgesByImage.put(imageUrl, flatEdges.keys)

            // The tiny versions of the backdrop that get stretched into blurs, one per resolution
            val tinyVersions = HashMap<Int, ImageBitmap?>()
            if (imageUrl != null) {
                shownTiles.map { it.resolution }.distinct().forEach { resolution ->
                    val tinyHeight = (resolution / imageAspect).toInt().coerceAtLeast(1)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .size(resolution, tinyHeight)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                    val result = context.imageLoader.execute(request)
                    var soft: Bitmap? = null
                    if (result is SuccessResult) {
                        soft =
                            result.image
                                .asDrawable(context.resources)
                                .toBitmap(config = Bitmap.Config.ARGB_8888)
                    }
                    // Make sure it really is the tiny size asked for, so the stretch blurs it
                    // as intended
                    if (soft != null && (soft.width != resolution || soft.height != tinyHeight)) {
                        soft = Bitmap.createScaledBitmap(soft, resolution, tinyHeight, true)
                    }
                    tinyVersions[resolution] = soft?.asImageBitmap()
                }
            }

            val output = ImageBitmap(width, height)
            val outputSize = Size(width.toFloat(), height.toFloat())
            CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                drawBackground()
                // Where the image's left or right edge is one flat colour, the colour base takes
                // that colour on that side: full from the screen's edge to just past the image's
                // edge, so the image's edge always sits on it, then fading out behind the image
                val tint = flatEdgeTint.coerceIn(0f, 1f)
                val solid = flatEdgeTintSolid.coerceAtLeast(0f)
                val reach = flatEdgeTintReach.coerceAtLeast(solid + 0.001f)
                if (tint > 0f) {
                    flatEdges[ImageEdge.LEFT]?.let { color ->
                        val full = (imageLeft + solid).coerceIn(0f, 0.998f)
                        val gone = (imageLeft + reach).coerceIn(full + 0.001f, 1f)
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    0f to color.copy(alpha = tint),
                                    full to color.copy(alpha = tint),
                                    gone to color.copy(alpha = 0f),
                                ),
                        )
                    }
                    flatEdges[ImageEdge.RIGHT]?.let { color ->
                        val full = (imageRight - solid).coerceIn(0.002f, 1f)
                        val gone = (imageRight - reach).coerceIn(0f, full - 0.001f)
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    gone to color.copy(alpha = 0f),
                                    full to color.copy(alpha = tint),
                                    1f to color.copy(alpha = tint),
                                ),
                        )
                    }
                }
            }
            // Three passes. First the tiles marked behind go straight onto the glows. Then the
            // group is drawn on a see-through sheet of its own, blurred together as one, and
            // that sheet is blended on top. Then the tiles marked onTop go straight onto the
            // result. Last, everything is smoothed together
            listOf(
                Triple(shownTiles.filter { it.behind && !it.onTop }, 0, false),
                Triple(shownTiles.filter { !it.behind && !it.onTop }, if (noSmoothing) 0 else groupSmoothRadius, true),
                Triple(shownTiles.filter { it.onTop }, 0, false),
            ).forEach { (passTiles, passRadius, isGroup) ->
                val target = if (isGroup) ImageBitmap(width, height) else output
                CanvasDrawScope().draw(density, layoutDirection, Canvas(target), outputSize) {
                    passTiles.forEach { copy ->
                        // As a share of the screen's height
                        val tileHeight = copy.height
                        val blurred = tinyVersions[copy.resolution]
                        if (blurred != null) {
                            val offset =
                                IntOffset(
                                    (size.width * copy.left).roundToInt(),
                                    (size.height * copy.top).roundToInt(),
                                )
                            val area =
                                IntSize(
                                    (size.width * copy.width).roundToInt().coerceAtLeast(1),
                                    (size.height * tileHeight).roundToInt().coerceAtLeast(1),
                                )
                            val smoothRadius = if (noSmoothing) 0 else (size.width * copy.smooth).roundToInt()
                            // The fades are made before the flip, so a flipped tile swaps its sides
                            val feather =
                                copy.feather
                                    ?.let {
                                        Feather(
                                            top = if (copy.flipVertical) it.bottom else it.top,
                                            right = if (copy.flip) it.left else it.right,
                                            bottom = if (copy.flipVertical) it.top else it.bottom,
                                            left = if (copy.flip) it.right else it.left,
                                        )
                                    }?.takeIf { it.any }
                            // What gets drawn, and where within the tile (before the flip)
                            val drawn: ImageBitmap?
                            val drawOffset: IntOffset
                            val drawSize: IntSize
                            // A turned or slanted tile can bring parts from off screen into view,
                            // so it is prepared whole
                            val transformed =
                                copy.rotation != 0f || copy.skewX != 0f || copy.skewY != 0f ||
                                    (copy.stretch != null && copy.stretch.strength > 0f)
                            if (smoothRadius < 1 && feather == null) {
                                // A plain stretch of the tiny version: nothing to prepare
                                drawn = blurred
                                drawOffset = offset
                                drawSize = area
                            } else {
                                // Only the part of the tile that can be seen is prepared. A blur
                                // reaches about three radii, so that much more around the screen
                                // is kept to blur the visible edge correctly
                                val margin = 3 * smoothRadius
                                val visibleLeft = maxOf(offset.x, -margin)
                                val visibleTop = maxOf(offset.y, -margin)
                                val visibleRight = minOf(offset.x + area.width, size.width.roundToInt() + margin)
                                val visibleBottom = minOf(offset.y + area.height, size.height.roundToInt() + margin)
                                if (transformed) {
                                    drawn =
                                        tileCrop(
                                            blurred,
                                            area,
                                            IntOffset.Zero,
                                            area,
                                            smoothRadius,
                                            feather,
                                            density,
                                            layoutDirection,
                                        )
                                    drawOffset = offset
                                    drawSize = area
                                } else if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) {
                                    // Entirely off screen
                                    drawn = null
                                    drawOffset = offset
                                    drawSize = area
                                } else {
                                    // The same part of the tile before the flip, in the tile's
                                    // own coordinates
                                    val cropLeft =
                                        if (copy.flip) area.width - (visibleRight - offset.x) else visibleLeft - offset.x
                                    val cropTop =
                                        if (copy.flipVertical) area.height - (visibleBottom - offset.y) else visibleTop - offset.y
                                    val cropSize = IntSize(visibleRight - visibleLeft, visibleBottom - visibleTop)
                                    drawn =
                                        tileCrop(
                                            blurred,
                                            area,
                                            IntOffset(cropLeft, cropTop),
                                            cropSize,
                                            smoothRadius,
                                            feather,
                                            density,
                                            layoutDirection,
                                        )
                                    drawOffset = IntOffset(offset.x + cropLeft, offset.y + cropTop)
                                    drawSize = cropSize
                                }
                            }
                            // The stretch, made before the flip like the fades, so a flipped tile
                            // swaps its edge
                            val stretchedDrawn =
                                copy.stretch
                                    ?.takeIf { it.strength > 0f }
                                    ?.let {
                                        val edge =
                                            when (it.edge) {
                                                StretchEdge.LEFT -> if (copy.flip) StretchEdge.RIGHT else StretchEdge.LEFT
                                                StretchEdge.RIGHT -> if (copy.flip) StretchEdge.LEFT else StretchEdge.RIGHT
                                                StretchEdge.TOP -> if (copy.flipVertical) StretchEdge.BOTTOM else StretchEdge.TOP
                                                StretchEdge.BOTTOM -> if (copy.flipVertical) StretchEdge.TOP else StretchEdge.BOTTOM
                                            }
                                        drawn?.let { image -> stretched(image, drawSize, edge, it.strength) }
                                    } ?: drawn
                            // Mirrored about the copy's own middle, so it stays where it is
                            val middle = Offset(offset.x + area.width / 2f, offset.y + area.height / 2f)
                            if (stretchedDrawn != null) {
                                withTransform({
                                    // Turned, then slanted, then flipped, all about the middle
                                    // (the last one listed is applied to the picture first)
                                    if (copy.rotation != 0f) rotate(copy.rotation, pivot = middle)
                                    if (copy.skewX != 0f || copy.skewY != 0f) {
                                        translate(middle.x, middle.y)
                                        transform(
                                            Matrix().apply {
                                                this[1, 0] = tan(Math.toRadians(copy.skewX.toDouble())).toFloat()
                                                this[0, 1] = tan(Math.toRadians(copy.skewY.toDouble())).toFloat()
                                            },
                                        )
                                        translate(-middle.x, -middle.y)
                                    }
                                    if (copy.flip || copy.flipVertical) {
                                        scale(
                                            scaleX = if (copy.flip) -1f else 1f,
                                            scaleY = if (copy.flipVertical) -1f else 1f,
                                            pivot = middle,
                                        )
                                    }
                                }) {
                                    val flatDim = if (flatEdges.isEmpty()) 1f else copy.flatEdgeOpacity
                                    copy.layers.forEach { (layerBlendMode, layerOpacity) ->
                                        drawImage(
                                            image = stretchedDrawn,
                                            dstOffset = drawOffset,
                                            dstSize = drawSize,
                                            alpha = (layerOpacity * flatDim).coerceIn(0f, 1f),
                                            blendMode = layerBlendMode,
                                            // A plain stretch, which is what turns the tiny version into a blur
                                            filterQuality = FilterQuality.Low,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                boxBlur(target.asAndroidBitmap(), passRadius)
                val sheetOpacity = groupOpacity.coerceIn(0f, 1f)
                if (isGroup && passTiles.isNotEmpty() && sheetOpacity > 0f) {
                    // The group's sheet onto the glows, with the group's own blend modes
                    val before =
                        if (sheetOpacity < 1f) {
                            output.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()
                        } else {
                            null
                        }
                    CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                        groupLayers.forEach { (layerBlendMode, layerOpacity) ->
                            drawImage(
                                image = target,
                                alpha = layerOpacity.coerceIn(0f, 1f),
                                blendMode = layerBlendMode,
                            )
                        }
                        if (before != null) drawImage(image = before, alpha = 1f - sheetOpacity)
                    }
                }
            }
            // The final smoothing, before the vignettes so they keep their shape
            if (!noSmoothing) boxBlur(output.asAndroidBitmap(), smoothRadius)
            CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                drawOverlay()
                if (outlineCopies) {
                    // Development aid: a coloured frame around each tile, drawn last so
                    // nothing hides it. In order: magenta, cyan, yellow, green
                    val frameColors = listOf(Color.Magenta, Color.Cyan, Color.Yellow, Color.Green)
                    tiles.forEachIndexed { index, copy ->
                        val tileHeight = copy.height
                        drawRect(
                            color = frameColors[index % frameColors.size],
                            topLeft = Offset(size.width * copy.left, size.height * copy.top),
                            size = Size(size.width * copy.width, size.height * tileHeight),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }
            // To compare devices and settings: filter Logcat on "BackdropBake"
            Timber.tag("BackdropBake").i(
                "Baked %dx%d in %d ms, flat edges: %s",
                width,
                height,
                (System.nanoTime() - started) / 1_000_000,
                flatEdges.keys.ifEmpty { "none" },
            )
            output
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Could not bake backdrop background")
            null
        }
    }

/**
 * An edge of the backdrop image, for tiles that reflect it
 */
internal enum class ImageEdge { LEFT, BOTTOM, RIGHT }

// The flat edges found for recently baked images, for BackdropDebug.showFlatEdges
internal val flatEdgesByImage = LruCache<Any, Set<ImageEdge>>(200)

/**
 * Development aid: a red arrow on each of these edges of the area, pointing outwards
 */
internal fun DrawScope.drawFlatEdgeArrows(edges: Set<ImageEdge>) {
    if (edges.isEmpty()) return
    val arrow = minOf(size.width, size.height) * 0.08f
    val inset = arrow * 0.5f
    val color = Color(0xFFFF3B30)
    edges.forEach { edge ->
        val path = Path()
        when (edge) {
            ImageEdge.LEFT -> {
                val y = size.height / 2f
                path.moveTo(inset, y)
                path.lineTo(inset + arrow, y - arrow * 0.6f)
                path.lineTo(inset + arrow, y + arrow * 0.6f)
            }

            ImageEdge.RIGHT -> {
                val y = size.height / 2f
                path.moveTo(size.width - inset, y)
                path.lineTo(size.width - inset - arrow, y - arrow * 0.6f)
                path.lineTo(size.width - inset - arrow, y + arrow * 0.6f)
            }

            ImageEdge.BOTTOM -> {
                val x = size.width / 2f
                path.moveTo(x, size.height - inset)
                path.lineTo(x - arrow * 0.6f, size.height - inset - arrow)
                path.lineTo(x + arrow * 0.6f, size.height - inset - arrow)
            }
        }
        path.close()
        drawPath(path, color)
    }
}

// Width of the version of the image that is checked for flat edges
private const val EDGE_CHECK_WIDTH = 480

/**
 * The edges of the image that are one flat colour: along each, a strip [stripShare] of the
 * image's width (or height, for the bottom) deep is checked, and it is flat when no colour
 * channel varies by more than [tolerance] (a standard deviation, 0 to 255). A dark edge, whose
 * average brightness is at most [darkLevel], uses [darkTolerance] instead.
 *
 * @return each flat edge with its average colour
 */
private suspend fun findFlatEdges(
    context: Context,
    imageUrl: Any,
    stripShare: Float,
    tolerance: Float,
    darkLevel: Float,
    darkTolerance: Float,
): Map<ImageEdge, Color> {
    val request =
        ImageRequest
            .Builder(context)
            .data(imageUrl)
            .size(EDGE_CHECK_WIDTH, EDGE_CHECK_WIDTH)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return emptyMap()
    val bitmap = result.image.asDrawable(context.resources).toBitmap(config = Bitmap.Config.ARGB_8888)
    val width = bitmap.width
    val height = bitmap.height
    val strip = (width * stripShare).roundToInt().coerceIn(1, max(1, minOf(width, height) / 2))

    // Standard deviation of each colour channel over the area; flat when all are small.
    // Returns the area's average colour when it is flat, otherwise null
    fun flatColor(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ): Color? {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x, y, w, h)
        val sums = DoubleArray(3)
        val squares = DoubleArray(3)
        for (color in pixels) {
            val channels = intArrayOf((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
            for (c in 0..2) {
                sums[c] += channels[c]
                squares[c] += channels[c].toDouble() * channels[c]
            }
        }
        val count = pixels.size.toDouble()
        val means = DoubleArray(3) { sums[it] / count }
        // Perceived brightness of the edge's average colour
        val brightness = 0.299 * means[0] + 0.587 * means[1] + 0.114 * means[2]
        val allowed = if (brightness <= darkLevel) darkTolerance else tolerance
        val flat =
            (0..2).all { c ->
                sqrt((squares[c] / count - means[c] * means[c]).coerceAtLeast(0.0)) <= allowed
            }
        return if (flat) {
            Color(means[0].toInt(), means[1].toInt(), means[2].toInt())
        } else {
            null
        }
    }

    return buildMap {
        flatColor(0, 0, strip, height)?.let { put(ImageEdge.LEFT, it) }
        flatColor(width - strip, 0, strip, height)?.let { put(ImageEdge.RIGHT, it) }
        flatColor(0, height - strip, width, strip)?.let { put(ImageEdge.BOTTOM, it) }
    }
}

/**
 * Part of a tile, ready to draw: the tiny version stretched over the whole tile ([tileSize]), of
 * which only the area at [cropOffset] and [cropSize] is made. That area is blurred by
 * [smoothRadius] and its edges fade out by [feather], both measured on the whole tile, so the part
 * looks exactly like the same part of the whole tile. Kept to a sensible size; the blur radius
 * shrinks along with it
 */
private fun tileCrop(
    tiny: ImageBitmap,
    tileSize: IntSize,
    cropOffset: IntOffset,
    cropSize: IntSize,
    smoothRadius: Int,
    feather: Feather?,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val shrink = minOf(1f, 2048f / maxOf(cropSize.width, cropSize.height))
    val width = (cropSize.width * shrink).roundToInt().coerceAtLeast(1)
    val height = (cropSize.height * shrink).roundToInt().coerceAtLeast(1)
    // Where the whole tile sits, seen from the crop
    val tileLeft = -cropOffset.x * shrink
    val tileTop = -cropOffset.y * shrink
    val tileWidth = tileSize.width * shrink
    val tileHeight = tileSize.height * shrink
    val result = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(density, layoutDirection, Canvas(result), size) {
        // A plain stretch, so a tiny source still turns into the same blur
        drawImage(
            image = tiny,
            dstOffset = IntOffset(tileLeft.roundToInt(), tileTop.roundToInt()),
            dstSize = IntSize(tileWidth.roundToInt().coerceAtLeast(1), tileHeight.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Low,
        )
    }
    if (smoothRadius >= 1) {
        boxBlur(result.asAndroidBitmap(), (smoothRadius * shrink).roundToInt().coerceAtLeast(1))
    }
    if (feather != null) {
        CanvasDrawScope().draw(density, layoutDirection, Canvas(result), size) {
            // Each side fades on its own, from see-through at its edge to solid that far in, and
            // the fades multiply where they overlap. A fade longer than the tile (above 1) never
            // gets solid: 2f is still half see-through at the opposite edge
            fun fadeFrom(
                edge: Offset,
                towards: Offset,
                length: Float,
            ) {
                if (length <= 0f) return
                drawRect(
                    brush =
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            start = edge,
                            end = edge + towards * length,
                        ),
                    blendMode = BlendMode.DstIn,
                )
            }
            val tileRight = tileLeft + tileWidth
            val tileBottom = tileTop + tileHeight
            fadeFrom(Offset(tileLeft, 0f), Offset(tileWidth, 0f), feather.left)
            fadeFrom(Offset(tileRight, 0f), Offset(-tileWidth, 0f), feather.right)
            fadeFrom(Offset(0f, tileTop), Offset(0f, tileHeight), feather.top)
            fadeFrom(Offset(0f, tileBottom), Offset(0f, -tileHeight), feather.bottom)
        }
    }
    return result
}

/**
 * Blurs a bitmap in place, see-through parts included. Three passes of a box blur come very close to a Gaussian blur
 * and are fast enough for the small baked image
 */
private fun boxBlur(
    bitmap: Bitmap,
    radius: Int,
) {
    if (radius < 1) return
    val width = bitmap.width
    val height = bitmap.height
    var pixels = IntArray(width * height)
    var scratch = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    // Colours are weighted by how solid they are while blurring, so see-through areas do not
    // bleed black into their surroundings
    for (i in pixels.indices) {
        val color = pixels[i]
        val alpha = color ushr 24
        if (alpha != 0xFF) {
            pixels[i] =
                (alpha shl 24) or
                (((color shr 16) and 0xFF) * alpha / 255 shl 16) or
                (((color shr 8) and 0xFF) * alpha / 255 shl 8) or
                ((color and 0xFF) * alpha / 255)
        }
    }
    repeat(3) {
        boxBlurPass(pixels, scratch, width, height, radius, horizontal = true)
        boxBlurPass(scratch, pixels, width, height, radius, horizontal = false)
    }
    for (i in pixels.indices) {
        val color = pixels[i]
        val alpha = color ushr 24
        if (alpha != 0xFF && alpha != 0) {
            pixels[i] =
                (alpha shl 24) or
                (minOf(255, ((color shr 16) and 0xFF) * 255 / alpha) shl 16) or
                (minOf(255, ((color shr 8) and 0xFF) * 255 / alpha) shl 8) or
                minOf(255, (color and 0xFF) * 255 / alpha)
        }
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun boxBlurPass(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val window = 2 * radius + 1
    for (line in 0 until lines) {
        // Position in the pixel array of the i-th pixel along this row or column, clamped at the ends
        fun at(i: Int): Int {
            val clamped = i.coerceIn(0, length - 1)
            return if (horizontal) line * width + clamped else clamped * width + line
        }
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val color = input[at(i)]
            alpha += color ushr 24
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        for (i in 0 until length) {
            output[at(i)] =
                ((alpha / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = input[at(i - radius)]
            val entering = input[at(i + radius + 1)]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
