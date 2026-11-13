import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import java.nio.file.Path
import org.slf4j.LoggerFactory
import service.ServiceController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.io.path.exists

internal fun buildArtworkCitation(metadata: ArtworkMetadata): String {
    val artistPart = metadata.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    val yearPart = metadata.year?.let { " ($it)" } ?: ""
    val sourcePart = metadata.source.ifBlank { "The Metropolitan Museum of Art" }
    return "${metadata.title} — $artistPart$yearPart, $sourcePart"
}

internal fun buildMetMuseumUrl(metadata: ArtworkMetadata): String? {
    val cleanId = metadata.id.trim()
    return if (cleanId.isNotEmpty() && cleanId.all { it.isDigit() }) {
        "https://www.metmuseum.org/art/collection/search/$cleanId"
    } else {
        null
    }
}

internal fun parseReadableReleaseNotes(rawNotes: String): List<String> {
    return rawNotes.lines()
        .map { line ->
            line.trim()
                .removePrefix("### ")
                .removePrefix("## ")
                .removePrefix("# ")
                .replace("**", "")
                .replace("`", "")
                .trim()
        }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf("Includes performance, visual polish, and stability improvements.") }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    serviceController: ServiceController,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    coroutineScope: CoroutineScope,
    updateManager: service.UpdateManager? = null,
    openUpdateDialog: Boolean = false,
    onUpdateDialogConsumed: () -> Unit = {}
) {
    val logger = LoggerFactory.getLogger("MainScreen")
    val isMac = remember { MacPlatform.isMac }
    val osLabel = if (isMac) "macOS" else "Windows"
    val trayLabel = if (isMac) "menu bar" else "Windows tray"

    val currentSettings by Settings.currentSettings.collectAsState()
    val isServiceRunning by serviceController.isServiceRunning.collectAsState()
    var isFirstRun by remember { mutableStateOf(settings.isFirstRun) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showArtworkDetails by remember { mutableStateOf(false) }
    var showCollectionBrowser by remember { mutableStateOf(false) }
    var copiedCitation by remember { mutableStateOf(false) }

    val resolvedUpdateManager = remember(updateManager) { updateManager ?: service.UpdateManager() }
    val updateStatus by resolvedUpdateManager.status.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(openUpdateDialog) {
        if (openUpdateDialog) {
            showUpdateDialog = true
            onUpdateDialogConsumed()
        }
    }

    LaunchedEffect(Unit) {
        serviceController.wallpaperManager.syncWithActiveDesktopWallpaper()
        if (resolvedUpdateManager.status.value is service.UpdateStatus.Idle && currentSettings.autoCheckUpdates) {
            resolvedUpdateManager.checkForUpdates(silent = true)
        }
    }

    LaunchedEffect(copiedCitation) {
        if (copiedCitation) {
            delay(2_000L)
            copiedCitation = false
        }
    }

    AnimatedContent(
        targetState = isFirstRun,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) with fadeOut(animationSpec = tween(300))
        }
    ) { firstRunActive ->
        if (firstRunActive) {
            GuidedOnboarding(
                settings = settings,
                serviceController = serviceController,
                currentArtwork = currentArtwork,
                onSettingsChange = onSettingsChange,
                onComplete = {
                    val updated = Settings.currentSettings.value.copy(isFirstRun = false)
                    Settings.updateCurrentSettings(updated)
                    onSettingsChange(updated)
                    isFirstRun = false
                },
                onError = { err -> errorMessage = err },
                coroutineScope = coroutineScope
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(ArtColors.Canvas)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Top Navigation Bar ──────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource("icons/64x64.png"),
                                contentDescription = "Art Wallpaper Logo",
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Column {
                                Text(
                                    "Art Wallpaper",
                                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Medium),
                                    color = ArtColors.PaperWhite,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    "The Met Open Access",
                                    style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                                    color = ArtColors.GalleryGold
                                )
                            }
                        }

                        // Right Action Controls: Collection, Update Pill, Quick Theme Toggle, Settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Offline Collection Drawer Button
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = ArtColors.SurfaceVariant,
                                border = BorderStroke(1.dp, ArtColors.CardBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { showCollectionBrowser = true }
                            ) {
                                val galleryAccent = ArtColors.GalleryGold
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(10.dp)) {
                                        val tile = size.width * 0.42f
                                        val gap = size.width * 0.16f
                                        val stroke = Stroke(width = 1.4f)
                                        drawRoundRect(galleryAccent, Offset.Zero, Size(tile, tile), CornerRadius(1.5f, 1.5f), stroke)
                                        drawRoundRect(galleryAccent, Offset(tile + gap, 0f), Size(tile, tile), CornerRadius(1.5f, 1.5f), stroke)
                                        drawRoundRect(galleryAccent, Offset(0f, tile + gap), Size(tile, tile), CornerRadius(1.5f, 1.5f), stroke)
                                        drawRoundRect(galleryAccent, Offset(tile + gap, tile + gap), Size(tile, tile), CornerRadius(1.5f, 1.5f), stroke)
                                    }
                                    Text(
                                        "Collection",
                                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                        color = ArtColors.PaperWhite
                                    )
                                }
                            }

                            // Update Available / Downloading Pill
                            when (val uStatus = updateStatus) {
                                is service.UpdateStatus.Available -> {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = ArtColors.GalleryGold.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, ArtColors.GalleryGold),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { showUpdateDialog = true }
                                    ) {
                                        val goldColor = ArtColors.GalleryGold
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Canvas(modifier = Modifier.size(6.dp)) {
                                                drawCircle(color = goldColor)
                                            }
                                            Text(
                                                "v${uStatus.latestVersion} Ready",
                                                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                                color = goldColor
                                            )
                                        }
                                    }
                                }
                                is service.UpdateStatus.Downloading -> {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = ArtColors.GalleryGold.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { showUpdateDialog = true }
                                    ) {
                                        Text(
                                            "Updating ${(uStatus.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                            color = ArtColors.GalleryGold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                else -> Unit
                            }

                            // Quick Theme Switcher
                            val nextTheme = when (currentSettings.themeMode) {
                                AppTheme.SYSTEM -> AppTheme.LIGHT
                                AppTheme.LIGHT  -> AppTheme.DARK
                                AppTheme.DARK   -> AppTheme.SYSTEM
                            }
                            val themeLabel = when (currentSettings.themeMode) {
                                AppTheme.SYSTEM -> "System"
                                AppTheme.LIGHT  -> "Light"
                                AppTheme.DARK   -> "Dark"
                            }
                            val isDarkTheme = ArtColors.isDark
                            val themeGold = ArtColors.GalleryGold

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = ArtColors.SurfaceVariant,
                                border = BorderStroke(1.dp, ArtColors.CardBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        val updated = currentSettings.copy(themeMode = nextTheme)
                                        Settings.updateCurrentSettings(updated)
                                        onSettingsChange(updated)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(8.dp)) {
                                        if (isDarkTheme) {
                                            drawCircle(color = themeGold)
                                        } else {
                                            drawCircle(color = themeGold, style = Stroke(width = 1.5f))
                                        }
                                    }
                                    Text(
                                        themeLabel,
                                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                        color = ArtColors.PaperWhite
                                    )
                                }
                            }

                            // Settings Button
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.size(34.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Preferences",
                                    tint = ArtColors.PaperMuted
                                )
                            }
                        }
                    }

                    // ── Dismissible Update Banner ──────────────────────────────────
                    val activeUpdate = updateStatus as? service.UpdateStatus.Available
                    val downloadingUpdate = updateStatus as? service.UpdateStatus.Downloading
                    val installingUpdate = updateStatus is service.UpdateStatus.Installing
                    val showUpdateBanner = (activeUpdate != null && currentSettings.dismissedUpdateVersion != activeUpdate.latestVersion) || downloadingUpdate != null || installingUpdate

                    if (showUpdateBanner) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = ArtColors.GalleryGold.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.55f))
                        ) {
                            if (installingUpdate) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = ArtColors.GalleryGold,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "Installing update... Art Wallpaper will restart automatically.",
                                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = ArtColors.PaperWhite
                                    )
                                }
                            } else if (downloadingUpdate != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Downloading Art Wallpaper update (${(downloadingUpdate.progress * 100).toInt()}%)...",
                                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                            color = ArtColors.PaperWhite
                                        )
                                        TextButton(
                                            onClick = { showUpdateDialog = true },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("DETAILS", color = ArtColors.GalleryGold, style = MaterialTheme.typography.caption)
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = downloadingUpdate.progress,
                                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                        color = ArtColors.GalleryGold,
                                        backgroundColor = ArtColors.DividerLine
                                    )
                                }
                            } else if (activeUpdate != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val gold = ArtColors.GalleryGold
                                        Canvas(modifier = Modifier.size(8.dp)) {
                                            drawCircle(color = gold)
                                        }
                                        val sizeInfo = service.UpdateManager.formatAssetSizeMb(activeUpdate.asset)?.let { " · $it" } ?: ""
                                        Text(
                                            "Art Wallpaper v${activeUpdate.latestVersion} is ready$sizeInfo",
                                            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium),
                                            color = ArtColors.PaperWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TextButton(
                                            onClick = { showUpdateDialog = true },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("What's New", color = ArtColors.GalleryGold, style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold))
                                        }

                                        Button(
                                            onClick = {
                                                val asset = activeUpdate.asset
                                                if (asset != null) {
                                                    coroutineScope.launch {
                                                        resolvedUpdateManager.downloadAndInstall(asset)
                                                    }
                                                } else {
                                                    try {
                                                        uriHandler.openUri(activeUpdate.releaseUrl)
                                                    } catch (e: Exception) {
                                                        logger.warn("Could not open release URL", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.height(28.dp).pointerHoverIcon(PointerIcon.Hand),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = ArtColors.GalleryGold,
                                                contentColor = if (ArtColors.isDark) Color(0xFF0E0E10) else Color.White
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Update Now", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
                                        }

                                        IconButton(
                                            onClick = {
                                                val updated = currentSettings.copy(dismissedUpdateVersion = activeUpdate.latestVersion)
                                                Settings.updateCurrentSettings(updated)
                                                onSettingsChange(updated)
                                            },
                                            modifier = Modifier.size(26.dp).pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Dismiss update banner",
                                                tint = ArtColors.PaperMuted,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Main Artwork Exhibition Card ───────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, ArtColors.CardBorder), RoundedCornerShape(12.dp))
                            .background(ArtColors.Surface)
                    ) {
                        Crossfade(
                            targetState = currentArtwork,
                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                        ) { targetArtwork ->
                            if (targetArtwork != null) {
                                val (path, metadata) = targetArtwork
                                val bitmap = rememberArtworkBitmap(path)

                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = metadata.title,
                                            // ContentScale.Fit preserves the authentic walnut moulding frame
                                            // edges that ContentScale.Crop clips on wide landscapes / tall portraits.
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(ArtColors.Surface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = ArtColors.GalleryGold,
                                                modifier = Modifier.size(32.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }

                                    // Gallery Exhibition Placard (3-stop scrim prevents text-contrast
                                    // collisions on bright/pastel paintings while remaining elegant on darks)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colorStops = arrayOf(
                                                        0.00f to Color.Transparent,
                                                        0.35f to Color(0x55000000),
                                                        0.70f to Color(0xBB0A0A0C),
                                                        1.00f to Color(0xF00A0A0C)
                                                    )
                                                )
                                            )
                                            .padding(horizontal = 22.dp, vertical = 16.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                metadata.title,
                                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                metadata.artist?.let { artist ->
                                                    Text(
                                                        artist,
                                                        style = MaterialTheme.typography.subtitle2,
                                                        color = Color.White.copy(alpha = 0.9f)
                                                    )
                                                }
                                                metadata.year?.let { year ->
                                                    Text(
                                                        "· $year",
                                                        style = MaterialTheme.typography.subtitle2,
                                                        color = Color.White.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }

                                            // Curatorial Chips & Quick Museum Actions
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    CuratorialChip("The Met Archive")
                                                    val desc = metadata.description
                                                    if (!desc.isNullOrBlank()) {
                                                        val firstLine = desc.lines().firstOrNull()?.trim()
                                                        if (!firstLine.isNullOrBlank()) {
                                                            val shortMedium = if (firstLine.length > 32) firstLine.take(30) + "..." else firstLine
                                                            CuratorialChip(shortMedium)
                                                        }
                                                    }
                                                }

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    PlacardActionChip(
                                                        text = "Full Info",
                                                        onClick = { showArtworkDetails = true }
                                                    )
                                                    PlacardActionChip(
                                                        text = if (copiedCitation) "Copied" else "Cite",
                                                        highlighted = copiedCitation,
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(buildArtworkCitation(metadata)))
                                                            copiedCitation = true
                                                        }
                                                    )
                                                    val metUrl = buildMetMuseumUrl(metadata)
                                                    if (metUrl != null) {
                                                        PlacardActionChip(
                                                            text = "The Met",
                                                            onClick = {
                                                                try {
                                                                    uriHandler.openUri(metUrl)
                                                                } catch (e: Exception) {
                                                                    logger.warn("Could not open Met page", e)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Welcoming empty state
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ArtColors.Surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Image(
                                            painter = painterResource("icons/128x128.png"),
                                            contentDescription = "Art Wallpaper",
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                        )
                                        Text(
                                            "Your Gallery Awaits",
                                            style = MaterialTheme.typography.h6,
                                            color = ArtColors.PaperWhite
                                        )
                                        Text(
                                            "Click NEXT ARTWORK to curate a masterpiece for your desktop",
                                            style = MaterialTheme.typography.caption,
                                            color = ArtColors.PaperMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Curating / Downloading Progress Overlay
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f)
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = ArtColors.GalleryGold,
                                        modifier = Modifier.size(46.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Text(
                                        "Curating next masterpiece...",
                                        style = MaterialTheme.typography.subtitle1,
                                        color = Color.White
                                    )
                                    Text(
                                        "Fetching high-res painting & rendering true desktop screen fit",
                                        style = MaterialTheme.typography.caption,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // ── Controls & Status Bar ──────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFE0A84B),
                                            CircleShape
                                        )
                                )
                                Text(
                                    if (currentSettings.hasSetUpdateTime) {
                                        String.format(
                                            java.util.Locale.ROOT,
                                            "Daily rotation at %02d:%02d",
                                            currentSettings.updateTimeHour,
                                            currentSettings.updateTimeMinute
                                        )
                                    } else {
                                        "Daily rotation at 07:00"
                                    },
                                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium),
                                    color = ArtColors.PaperWhite
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isServiceRunning) Color(0xFF4CAF50).copy(alpha = 0.14f) else ArtColors.GalleryGold.copy(alpha = 0.14f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isServiceRunning) Color(0xFF4CAF50).copy(alpha = 0.45f) else ArtColors.GalleryGold.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            coroutineScope.launch {
                                                if (isServiceRunning) {
                                                    serviceController.stopService()
                                                } else {
                                                    serviceController.startService()
                                                }
                                            }
                                        }
                                ) {
                                    Text(
                                        text = if (isServiceRunning) "Pause" else "Paused · Resume",
                                        style = MaterialTheme.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (isServiceRunning) Color(0xFF66BB6A) else ArtColors.GalleryGold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                when {
                                    isLoading -> "Downloading high-resolution painting from The Met..."
                                    !isServiceRunning -> "Auto-rotation paused · Running silently in $trayLabel"
                                    else -> "Running silently in $trayLabel · Screen-fit active"
                                },
                                style = MaterialTheme.typography.caption,
                                color = if (isLoading) ArtColors.GalleryGold else ArtColors.PaperMuted
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (currentArtwork != null && !isLoading) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            serviceController.reprocessWallpaper(currentSettings.wallpaperFitMode)
                                        }
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        backgroundColor = Color.Transparent,
                                        contentColor = ArtColors.GalleryGold
                                    ),
                                    border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("APPLY DESKTOP", style = MaterialTheme.typography.button)
                                }
                            }

                            PulsingButton(
                                onClick = {
                                    if (!isLoading) {
                                        isLoading = true
                                        val previousTitle = currentArtwork?.second?.title
                                        logger.info("Manual wallpaper fetch requested")

                                        coroutineScope.launch(Dispatchers.Default) {
                                            try {
                                                serviceController.nextWallpaper()

                                                withTimeout(45_000L) {
                                                    serviceController.wallpaperManager.currentArtwork.first { next ->
                                                        next != null && next.second.title != previousTitle && next.first.exists()
                                                    }
                                                }
                                            } catch (e: TimeoutCancellationException) {
                                                withContext(Dispatchers.Main) {
                                                    errorMessage = "Network timed out. Please retry."
                                                }
                                            } catch (e: CancellationException) {
                                                // Normal coroutine cancellation
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    errorMessage = "Failed to curate artwork: ${e.message}"
                                                }
                                            } finally {
                                                withContext(Dispatchers.Main) {
                                                    isLoading = false
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = if (ArtColors.isDark) Color(0xFF0E0E10) else Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("NEXT ARTWORK", style = MaterialTheme.typography.button)
                            }
                        }
                    }

                    // ── Error Message Banner ───────────────────────────────────────
                    errorMessage?.let { error ->
                        Snackbar(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            backgroundColor = ArtColors.SurfaceVariant,
                            contentColor = ArtColors.PaperWhite,
                            action = {
                                TextButton(onClick = { errorMessage = null }) {
                                    Text("Dismiss", color = ArtColors.GalleryGold)
                                }
                            }
                        ) {
                            Text(error, style = MaterialTheme.typography.body2)
                        }
                    }
                }
            }

            // ── Settings Dialog ────────────────────────────────────────────
            if (showSettings) {
                SettingsDialog(
                    settings = currentSettings,
                    serviceController = serviceController,
                    updateManager = resolvedUpdateManager,
                    onOpenUpdateDetails = { showUpdateDialog = true },
                    onSettingsChange = { newSettings ->
                        val wasSyncLockScreen = currentSettings.syncLockScreen
                        Settings.updateCurrentSettings(newSettings)
                        serviceController.wallpaperManager.updateSettings(newSettings)
                        onSettingsChange(newSettings)
                        if (!MacPlatform.isMac && newSettings.syncLockScreen && !wasSyncLockScreen) {
                            coroutineScope.launch(Dispatchers.IO) {
                                serviceController.wallpaperManager.syncLockScreenWithCurrentWallpaper()
                            }
                        }
                        coroutineScope.launch {
                            delay(500)
                            serviceController.restartService()
                        }
                    },
                    onDismiss = { showSettings = false }
                )
            }

            // ── Release Notes & Update Dialog ──────────────────────────────
            if (showUpdateDialog) {
                UpdateDetailsDialog(
                    updateStatus = updateStatus,
                    onInstall = { available ->
                        available.asset?.let { asset ->
                            coroutineScope.launch {
                                resolvedUpdateManager.downloadAndInstall(asset)
                            }
                        } ?: run {
                            try {
                                uriHandler.openUri(available.releaseUrl)
                            } catch (e: Exception) {
                                logger.warn("Could not open release URL", e)
                            }
                        }
                    },
                    onCheckAgain = {
                        coroutineScope.launch {
                            resolvedUpdateManager.checkForUpdates(silent = false)
                        }
                    },
                    onOpenReleasePage = { url ->
                        try {
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            logger.warn("Could not open GitHub release page", e)
                        }
                    },
                    onSkipVersion = { version ->
                        val updated = currentSettings.copy(dismissedUpdateVersion = version)
                        Settings.updateCurrentSettings(updated)
                        onSettingsChange(updated)
                        showUpdateDialog = false
                    },
                    onDismiss = { showUpdateDialog = false }
                )
            }

            // ── Full Artwork Details Dialog ────────────────────────────────
            if (showArtworkDetails && currentArtwork != null) {
                ArtworkDetailsDialog(
                    path = currentArtwork.first,
                    metadata = currentArtwork.second,
                    copiedCitation = copiedCitation,
                    onCopyCitation = {
                        clipboardManager.setText(AnnotatedString(buildArtworkCitation(currentArtwork.second)))
                        copiedCitation = true
                    },
                    onOpenMetPage = { url ->
                        try {
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            logger.warn("Could not open Met page", e)
                        }
                    },
                    onDismiss = { showArtworkDetails = false }
                )
            }

            // ── Recent Collection Offline Browser ──────────────────────────
            if (showCollectionBrowser) {
                CollectionBrowserDialog(
                    serviceController = serviceController,
                    activeArtworkId = currentArtwork?.second?.id,
                    onSelectArtwork = { chosenPath, chosenMeta ->
                        showCollectionBrowser = false
                        coroutineScope.launch {
                            try {
                                serviceController.selectStoredArtwork(chosenPath, chosenMeta)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errorMessage = "Could not apply cached artwork: ${e.message}"
                            }
                        }
                    },
                    onDismiss = { showCollectionBrowser = false }
                )
            }
        }
    }
}
