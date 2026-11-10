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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.getLastModifiedTime

// ── In-Memory Bitmap Cache for Instant UI Rendering ─────────────────────────

object ArtworkBitmapCache {
    private val memoryCache = ConcurrentHashMap<String, Pair<Long, ImageBitmap>>()

    fun get(path: Path): ImageBitmap? {
        val entry = memoryCache[path.toAbsolutePath().toString()] ?: return null
        val currentMod = try { if (path.exists()) path.getLastModifiedTime().toMillis() else 0L } catch (_: Exception) { 0L }
        return if (entry.first == currentMod) entry.second else null
    }

    fun put(path: Path, bitmap: ImageBitmap) {
        if (memoryCache.size > 8) {
            memoryCache.clear() // Prevent memory bloat; keep recent few in RAM
        }
        val currentMod = try { if (path.exists()) path.getLastModifiedTime().toMillis() else 0L } catch (_: Exception) { 0L }
        memoryCache[path.toAbsolutePath().toString()] = currentMod to bitmap
    }

    fun invalidate(path: Path) {
        memoryCache.remove(path.toAbsolutePath().toString())
    }

    fun clear() {
        memoryCache.clear()
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

    var bitmap by remember(path, fileModTime) { mutableStateOf(ArtworkBitmapCache.get(path)) }

    LaunchedEffect(path, fileModTime) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                if (path.exists()) {
                    val bytes = path.readBytes()
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap().also {
                        ArtworkBitmapCache.put(path, it)
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

// ── Interactive Pulsing Button ───────────────────────────────────────────────

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
    val scope = rememberCoroutineScope()
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
            scope.launch {
                delay(120)
                isPressed = false
            }
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
