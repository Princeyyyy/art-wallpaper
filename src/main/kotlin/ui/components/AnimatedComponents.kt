import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.getLastModifiedTime

// ── In-Memory Bitmap Cache for Instant UI Rendering ─────────────────────────

/**
 * Bounded LRU bitmap cache.
 *
 * Fix #15 (Low): Separated full-resolution cache (capped at 8 items, max ~80MB) from
 * thumbnail cache (capped at 24 items, max ~9MB) to prevent up to 360MB memory bloat
 * while preserving instant navigation for recent wallpapers.
 */
object ArtworkBitmapCache {
    private const val MAX_FULL_SIZE = 8
    private const val MAX_THUMB_SIZE = 24

    private val fullLru = object : LinkedHashMap<String, Pair<Long, ImageBitmap>>(MAX_FULL_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, ImageBitmap>>?): Boolean =
            size > MAX_FULL_SIZE
    }
    private val thumbLru = object : LinkedHashMap<String, Pair<Long, ImageBitmap>>(MAX_THUMB_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, ImageBitmap>>?): Boolean =
            size > MAX_THUMB_SIZE
    }
    private val lock = Any()

    fun get(path: Path, isThumbnail: Boolean = false, expectedModTime: Long = -1L): ImageBitmap? {
        val key = path.toAbsolutePath().toString()
        val currentMod = if (expectedModTime >= 0L) {
            expectedModTime
        } else {
            try { if (path.exists()) path.getLastModifiedTime().toMillis() else 0L } catch (_: Exception) { 0L }
        }
        synchronized(lock) {
            val map = if (isThumbnail) thumbLru else fullLru
            val entry = map[key] ?: return null
            return if (entry.first == currentMod) entry.second else null
        }
    }

    fun put(path: Path, bitmap: ImageBitmap, isThumbnail: Boolean = false, modTime: Long = -1L) {
        val key = path.toAbsolutePath().toString()
        val currentMod = if (modTime >= 0L) {
            modTime
        } else {
            try { if (path.exists()) path.getLastModifiedTime().toMillis() else 0L } catch (_: Exception) { 0L }
        }
        synchronized(lock) {
            val map = if (isThumbnail) thumbLru else fullLru
            map[key] = currentMod to bitmap
        }
    }

    fun invalidate(path: Path) {
        val key = path.toAbsolutePath().toString()
        synchronized(lock) {
            fullLru.remove(key)
            thumbLru.remove(key)
        }
    }

    fun clear() {
        synchronized(lock) {
            fullLru.clear()
            thumbLru.clear()
        }
    }
}

/**
 * Decodes high-resolution artwork asynchronously on Dispatchers.IO.
 * Avoids blocking the Compose rendering thread, ensuring smooth 60fps animations.
 */
@Composable
fun rememberArtworkBitmap(path: Path?): ImageBitmap? {
    if (path == null) return null

    val fileModTime = remember(path) {
        try { if (path.exists()) path.getLastModifiedTime().toMillis() else 0L } catch (_: Exception) { 0L }
    }

    var bitmap by remember(path, fileModTime) {
        mutableStateOf(ArtworkBitmapCache.get(path, isThumbnail = false, expectedModTime = fileModTime))
    }

    LaunchedEffect(path, fileModTime) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                if (path.exists()) {
                    val bytes = path.readBytes()
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap().also {
                        ArtworkBitmapCache.put(path, it, isThumbnail = false, modTime = fileModTime)
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        }
        bitmap = loaded
    }

    return bitmap
}

/**
 * Fix #3 (High): Loads downsampled thumbnail bitmap using ImageIO source subsampling.
 *
 * Avoids decoding full 4K frames (up to 24MB raw native Skia memory) before downscaling.
 * Instead, DCT-level subsampling reads only the necessary coefficients, reducing peak native
 * memory per thumbnail by over 95%.
 */
@Composable
fun rememberArtworkThumbnail(path: Path?, maxDimension: Int = 400): ImageBitmap? {
    if (path == null) return null

    val thumbKey = remember(path, maxDimension) {
        val dir = path.parent
        val name = path.nameWithoutExtension
        val ext = path.extension
        dir?.resolve("${name}_thumb${maxDimension}.$ext") ?: path
    }

    val fileModTime = remember(path) {
        try { if (path.exists()) path.getLastModifiedTime().toMillis() else 0L } catch (_: Exception) { 0L }
    }

    var bitmap by remember(thumbKey, fileModTime) {
        mutableStateOf(ArtworkBitmapCache.get(thumbKey, isThumbnail = true, expectedModTime = fileModTime))
    }

    LaunchedEffect(thumbKey, fileModTime) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                if (!path.exists()) return@withContext null
                val file = path.toFile()
                ImageIO.createImageInputStream(file)?.use { iis ->
                    val readers = ImageIO.getImageReaders(iis)
                    if (readers.hasNext()) {
                        val reader = readers.next()
                        try {
                            reader.input = iis
                            val srcW = reader.getWidth(0)
                            val srcH = reader.getHeight(0)
                            val maxDim = maxOf(srcW, srcH)
                            val subsample = (maxDim / maxDimension).coerceAtLeast(1)
                            val param = reader.defaultReadParam
                            if (subsample > 1) {
                                param.setSourceSubsampling(subsample, subsample, 0, 0)
                            }
                            val sampled = reader.read(0, param)
                            val sampledW = sampled.width
                            val sampledH = sampled.height
                            val scale = maxDimension.toFloat() / maxOf(sampledW, sampledH)
                            val finalBitmap = if (scale < 1.0f) {
                                val dstW = (sampledW * scale).toInt().coerceAtLeast(1)
                                val dstH = (sampledH * scale).toInt().coerceAtLeast(1)
                                val scaled = java.awt.image.BufferedImage(dstW, dstH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                                val g = scaled.createGraphics()
                                try {
                                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                                    g.drawImage(sampled, 0, 0, dstW, dstH, null)
                                } finally {
                                    g.dispose()
                                }
                                scaled.toComposeImageBitmap()
                            } else {
                                sampled.toComposeImageBitmap()
                            }
                            finalBitmap.also { ArtworkBitmapCache.put(thumbKey, it, isThumbnail = true, modTime = fileModTime) }
                        } finally {
                            reader.dispose()
                        }
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }
        bitmap = loaded
    }

    return bitmap
}

// ── Interactive Pulsing Button ───────────────────────────────────────────────

/**
 * Fix #16 (Low): PulsingButton uses LaunchedEffect for the reset duration instead of
 * allocating a new CoroutineScope launch on every single button press.
 */
@Composable
fun PulsingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        backgroundColor = ArtColors.GalleryGold,
        contentColor = if (ArtColors.isDark) Color(0xFF0E0E10) else Color.White,
        disabledBackgroundColor = ArtColors.GalleryGoldDim.copy(alpha = 0.4f),
        disabledContentColor = (if (ArtColors.isDark) Color(0xFF0E0E10) else Color.White).copy(alpha = 0.5f)
    ),
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(120)
            isPressed = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Button(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier.scale(scale),
        enabled = enabled,
        colors = colors,
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        content()
    }
}

// ── Step Indicator Pill ───────────────────────────────────────────────────────

@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 1..totalSteps) {
            val isCurrent = step == currentStep
            val isDone = step < currentStep
            val width by animateDpAsState(
                targetValue = if (isCurrent) 24.dp else 8.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
            val color by animateColorAsState(
                targetValue = when {
                    isCurrent -> ArtColors.GalleryGold
                    isDone -> ArtColors.GalleryGoldDim
                    else -> ArtColors.DividerLine
                },
                animationSpec = tween(250)
            )

            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

// ── Onboarding Option Card ───────────────────────────────────────────────────

@Composable
fun OnboardingOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) ArtColors.GalleryGold else ArtColors.DividerLine,
        animationSpec = tween(200)
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) ArtColors.SurfaceVariant else ArtColors.Surface,
        animationSpec = tween(200)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle2,
                    color = ArtColors.PaperWhite
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.caption,
                    color = ArtColors.PaperMuted,
                    lineHeight = 16.sp
                )
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}