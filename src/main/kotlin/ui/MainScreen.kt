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
                    val showUpdateBanner = (activeUpdate != null && currentSettings.dismissedUpdateVersion != activeUpdate.latestVersion) || downloadingUpdate != null

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
                            if (downloadingUpdate != null) {
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
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
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

                                    // Gallery Exhibition Placard (Semi-transparent scrim overlay)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x99000000),
                                                        Color(0xEB0A0A0C)
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

@Composable
private fun CuratorialChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption.copy(fontSize = 10.sp, letterSpacing = 0.2.sp),
            color = Color.White.copy(alpha = 0.95f),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PlacardActionChip(
    text: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (highlighted) ArtColors.GalleryGold else Color.White.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, if (highlighted) ArtColors.GalleryGold else Color.White.copy(alpha = 0.28f)),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            ),
            color = if (highlighted) Color(0xFF101012) else Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun UpdateDetailsDialog(
    updateStatus: service.UpdateStatus,
    onInstall: (service.UpdateStatus.Available) -> Unit,
    onCheckAgain: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onSkipVersion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(460.dp).heightIn(max = 540.dp),
            shape = RoundedCornerShape(12.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            border = BorderStroke(1.dp, ArtColors.CardBorder),
            elevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Software Update",
                            style = MaterialTheme.typography.h6,
                            color = ArtColors.PaperWhite
                        )
                        Text(
                            "Installed: v${AppInfo.version}",
                            style = MaterialTheme.typography.caption,
                            color = ArtColors.PaperMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ArtColors.PaperMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(14.dp))

                when (updateStatus) {
                    is service.UpdateStatus.Available -> {
                        val sizeText = service.UpdateManager.formatAssetSizeMb(updateStatus.asset)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Art Wallpaper v${updateStatus.latestVersion}",
                                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                color = ArtColors.GalleryGold
                            )
                            if (sizeText != null) {
                                Text(
                                    sizeText,
                                    style = MaterialTheme.typography.caption,
                                    color = ArtColors.PaperMuted
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = ArtColors.SurfaceVariant,
                            border = BorderStroke(1.dp, ArtColors.CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                parseReadableReleaseNotes(updateStatus.releaseNotes).forEach { noteLine ->
                                    Text(
                                        noteLine,
                                        style = MaterialTheme.typography.body2,
                                        color = ArtColors.PaperWhite
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { onSkipVersion(updateStatus.latestVersion) },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("SKIP", color = ArtColors.PaperMuted, style = MaterialTheme.typography.caption)
                                }
                                TextButton(
                                    onClick = { onOpenReleasePage(updateStatus.releaseUrl) },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("GITHUB", color = ArtColors.GalleryGold, style = MaterialTheme.typography.caption)
                                }
                            }

                            PulsingButton(onClick = { onInstall(updateStatus) }) {
                                Text("INSTALL UPDATE", style = MaterialTheme.typography.button)
                            }
                        }
                    }

                    is service.UpdateStatus.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Downloading installer (${(updateStatus.progress * 100).toInt()}%)...",
                                style = MaterialTheme.typography.subtitle2,
                                color = ArtColors.GalleryGold
                            )
                            LinearProgressIndicator(
                                progress = updateStatus.progress,
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = ArtColors.GalleryGold,
                                backgroundColor = ArtColors.DividerLine
                            )
                            Text(
                                "The installer will launch automatically as soon as the download completes.",
                                style = MaterialTheme.typography.caption,
                                color = ArtColors.PaperMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is service.UpdateStatus.Checking -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = ArtColors.GalleryGold, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Checking GitHub releases...", style = MaterialTheme.typography.body2, color = ArtColors.PaperMuted)
                        }
                    }

                    is service.UpdateStatus.UpToDate -> {
                        Text(
                            "You are already running the latest version (v${updateStatus.currentVersion}).",
                            style = MaterialTheme.typography.body2,
                            color = ArtColors.PaperWhite
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = onCheckAgain,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("CHECK AGAIN", color = ArtColors.GalleryGold)
                            }
                            Spacer(Modifier.width(8.dp))
                            PulsingButton(onClick = onDismiss) {
                                Text("DONE")
                            }
                        }
                    }

                    is service.UpdateStatus.Error -> {
                        Text(
                            "Could not retrieve release info: ${updateStatus.message}",
                            style = MaterialTheme.typography.body2,
                            color = ArtColors.PaperWhite
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = onCheckAgain,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("RETRY", color = ArtColors.GalleryGold)
                            }
                            Spacer(Modifier.width(8.dp))
                            PulsingButton(onClick = onDismiss) {
                                Text("CLOSE")
                            }
                        }
                    }

                    else -> {
                        Text("Checking version status...", style = MaterialTheme.typography.body2, color = ArtColors.PaperMuted)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = onCheckAgain,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("CHECK NOW", color = ArtColors.GalleryGold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkDetailsDialog(
    path: Path,
    metadata: ArtworkMetadata,
    copiedCitation: Boolean,
    onCopyCitation: () -> Unit,
    onOpenMetPage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = rememberArtworkBitmap(path)
    val detailLines = remember(metadata.description) {
        metadata.description?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }
    val metUrl = remember(metadata.id) { buildMetMuseumUrl(metadata) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(500.dp).heightIn(max = 580.dp),
            shape = RoundedCornerShape(12.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            border = BorderStroke(1.dp, ArtColors.CardBorder),
            elevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Curatorial Placard",
                        style = MaterialTheme.typography.caption.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold),
                        color = ArtColors.GalleryGold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ArtColors.PaperMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = metadata.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, ArtColors.CardBorder), RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        metadata.title,
                        style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold),
                        color = ArtColors.PaperWhite
                    )

                    val subline = buildString {
                        metadata.artist?.takeIf { it.isNotBlank() }?.let { append(it) }
                        metadata.year?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it)
                        }
                    }
                    if (subline.isNotBlank()) {
                        Text(subline, style = MaterialTheme.typography.subtitle2, color = ArtColors.GalleryGold)
                    }

                    Divider(color = ArtColors.DividerLine)

                    if (detailLines.isEmpty()) {
                        Text(
                            "Source: ${metadata.source}",
                            style = MaterialTheme.typography.body2,
                            color = ArtColors.PaperMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            detailLines.forEachIndexed { index, line ->
                                Text(
                                    text = if (index == 0 && !line.contains(":")) "Medium: $line" else line,
                                    style = MaterialTheme.typography.body2,
                                    color = ArtColors.PaperWhite.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCopyCitation,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = Color.Transparent,
                                contentColor = ArtColors.GalleryGold
                            ),
                            border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.5f))
                        ) {
                            Text(if (copiedCitation) "COPIED" else "COPY CITATION", style = MaterialTheme.typography.button)
                        }
                        if (metUrl != null) {
                            TextButton(
                                onClick = { onOpenMetPage(metUrl) },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("VIEW AT THE MET", color = ArtColors.GalleryGold, style = MaterialTheme.typography.button)
                            }
                        }
                    }

                    PulsingButton(onClick = onDismiss) {
                        Text("CLOSE")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionBrowserDialog(
    serviceController: ServiceController,
    activeArtworkId: String?,
    onSelectArtwork: (Path, ArtworkMetadata) -> Unit,
    onDismiss: () -> Unit
) {
    var storedItems by remember { mutableStateOf<List<Pair<Path, ArtworkMetadata>>?>(null) }

    LaunchedEffect(Unit) {
        storedItems = withContext(Dispatchers.IO) {
            serviceController.getStoredArtworks()
                .distinctBy { it.second.id }
                .sortedByDescending { it.second.fetchedAt }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(640.dp).height(500.dp),
            shape = RoundedCornerShape(12.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            border = BorderStroke(1.dp, ArtColors.CardBorder),
            elevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Recent Collection",
                            style = MaterialTheme.typography.h6,
                            color = ArtColors.PaperWhite
                        )
                        Text(
                            "Select any cached painting to re-apply instantly offline",
                            style = MaterialTheme.typography.caption,
                            color = ArtColors.PaperMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ArtColors.PaperMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(14.dp))

                val itemsList = storedItems
                when {
                    itemsList == null -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ArtColors.GalleryGold, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    }
                    itemsList.isEmpty() -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                "No cached artworks yet. Click NEXT ARTWORK to start building your local collection.",
                                style = MaterialTheme.typography.body2,
                                color = ArtColors.PaperMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(itemsList, key = { it.second.id }) { (path, meta) ->
                                val isActive = meta.id == activeArtworkId
                                val thumb = rememberArtworkBitmap(path)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ArtColors.SurfaceVariant,
                                    border = BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) ArtColors.GalleryGold else ArtColors.CardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onSelectArtwork(path, meta) }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb,
                                                contentDescription = meta.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color(0xE00A0A0C))
                                                    )
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    meta.title,
                                                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                meta.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                                                    Text(
                                                        artist,
                                                        style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                                                        color = Color.White.copy(alpha = 0.78f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = ArtColors.GalleryGold,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                            ) {
                                                Text(
                                                    "ACTIVE",
                                                    style = MaterialTheme.typography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF0E0E10),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Complete 4-Step Guided Onboarding Experience ─────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GuidedOnboarding(
    settings: Settings,
    serviceController: ServiceController,
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    onSettingsChange: (Settings) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit,
    coroutineScope: CoroutineScope
) {
    var step by remember { mutableStateOf(1) }
    var selectedHour by remember { mutableStateOf(settings.updateTimeHour) }
    var selectedMinute by remember { mutableStateOf(settings.updateTimeMinute) }
    var autoStartEnabled by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var syncLockScreenEnabled by remember { mutableStateOf(settings.syncLockScreen) }
    var isCurating by remember { mutableStateOf(false) }
    var curationStage by remember { mutableStateOf("Connecting to museum collection...") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArtColors.Canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Stepper Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource("icons/64x64.png"),
                        contentDescription = "Logo",
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        "SETUP WIZARD",
                        style = MaterialTheme.typography.caption.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = ArtColors.GalleryGold
                    )
                }
                StepIndicator(currentStep = step, totalSteps = 4)
            }

            // Step Body with Horizontal Slide & Fade
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 3 } + fadeIn()) with
                                (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn()) with
                                (slideOutHorizontally { width -> width / 3 } + fadeOut())
                    }
                }
            ) { activeStep ->
                when (activeStep) {
                    1 -> OnboardingStep1Welcome()
                    2 -> OnboardingStep2Schedule(
                        hour = selectedHour,
                        minute = selectedMinute,
                        onTimeChange = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        }
                    )
                    3 -> OnboardingStep3Integration(
                        autoStart = autoStartEnabled,
                        notifications = notificationsEnabled,
                        syncLockScreen = syncLockScreenEnabled,
                        onAutoStartChange = { autoStartEnabled = it },
                        onNotificationsChange = { notificationsEnabled = it },
                        onSyncLockScreenChange = { syncLockScreenEnabled = it }
                    )
                    4 -> OnboardingStep4Curate(
                        isCurating = isCurating,
                        curationStage = curationStage,
                        currentArtwork = currentArtwork,
                        onCurate = {
                            if (!isCurating) {
                                isCurating = true
                                coroutineScope.launch(Dispatchers.Default) {
                                    try {
                                        curationStage = "Connecting to The Met collection..."
                                        delay(400)
                                        if (autoStartEnabled) {
                                            WindowsAutoStart.enable()
                                        }

                                        val lockScreenPref = if (MacPlatform.isMac) false else syncLockScreenEnabled
                                        serviceController.wallpaperManager.updateSettings(
                                            Settings.currentSettings.value.copy(syncLockScreen = lockScreenPref)
                                        )

                                        curationStage = "Selecting unvisited masterpiece..."
                                        serviceController.nextWallpaper()

                                        curationStage = "Enhancing and applying to desktop..."
                                        withTimeout(45_000L) {
                                            serviceController.wallpaperManager.currentArtwork.first { art ->
                                                art != null && art.first.exists()
                                            }
                                        }

                                        curationStage = "Masterpiece applied successfully!"
                                        val finalSettings = Settings.currentSettings.value.copy(
                                            isFirstRun = false,
                                            updateTimeHour = selectedHour,
                                            updateTimeMinute = selectedMinute,
                                            hasSetUpdateTime = true,
                                            startWithSystem = autoStartEnabled,
                                            hasEnabledAutoStart = autoStartEnabled,
                                            notificationsEnabled = notificationsEnabled,
                                            syncLockScreen = lockScreenPref
                                        )
                                        withContext(Dispatchers.Main) {
                                            Settings.updateCurrentSettings(finalSettings)
                                            onSettingsChange(finalSettings)
                                        }
                                        serviceController.startService()
                                    } catch (e: TimeoutCancellationException) {
                                        withContext(Dispatchers.Main) {
                                            onError("Network took longer than usual. Please retry.")
                                            isCurating = false
                                        }
                                    } catch (e: CancellationException) {
                                        // Normal coroutine completion
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            onError("Setup note: ${e.message}")
                                            isCurating = false
                                        }
                                    }
                                }
                            }
                        },
                        onFinish = onComplete
                    )
                }
            }

            // Bottom Navigation Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1 && step < 4 && !isCurating) {
                    TextButton(
                        onClick = { step-- },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        colors = ButtonDefaults.textButtonColors(contentColor = ArtColors.PaperMuted)
                    ) {
                        Text("BACK", style = MaterialTheme.typography.button)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (step < 4) {
                    PulsingButton(
                        onClick = { step++ }
                    ) {
                        Text(if (step == 1) "GET STARTED" else "CONTINUE", style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

// ── Step 1: Welcome ─────────────────────────────────────────────────────────

@Composable
private fun OnboardingStep1Welcome() {
    val osLabel = if (MacPlatform.isMac) "macOS" else "Windows"
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource("icons/128x128.png"),
            contentDescription = "ArtWallpaper Logo",
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(18.dp))
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "ArtWallpaper",
            style = MaterialTheme.typography.h4,
            color = ArtColors.GalleryGold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "A museum gallery on your $osLabel desktop",
            style = MaterialTheme.typography.subtitle1,
            color = ArtColors.PaperWhite,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        Divider(color = ArtColors.DividerLine, thickness = 1.dp)
        Spacer(Modifier.height(24.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            WelcomePillar(
                title = "400,000+ Curated Works",
                description = "Directly sourced from The Metropolitan Museum of Art open access archive."
            )
            WelcomePillar(
                title = "True Display Screen Fit",
                description = "Smart ambient matting prevents stretching, fitting your display resolution perfectly."
            )
            WelcomePillar(
                title = "Automatic Daily Refresh",
                description = "Wakes up silently and changes your background at the exact time you prefer."
            )
        }
    }
}

@Composable
private fun WelcomePillar(title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .background(ArtColors.GalleryGold, CircleShape)
        )
        Column {
            Text(title, style = MaterialTheme.typography.subtitle2, color = ArtColors.PaperWhite)
            Text(description, style = MaterialTheme.typography.body2, color = ArtColors.PaperMuted)
        }
    }
}

// ── Step 2: Schedule ────────────────────────────────────────────────────────

@Composable
private fun OnboardingStep2Schedule(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Daily Schedule",
            style = MaterialTheme.typography.h5,
            color = ArtColors.GalleryGold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Choose when your wallpaper should rotate each day.",
            style = MaterialTheme.typography.body2,
            color = ArtColors.PaperMuted
        )

        Spacer(Modifier.height(20.dp))

        // Preset 1: Morning
        OnboardingOptionCard(
            title = "Morning Coffee (07:00 AM)",
            description = "Start your morning with a fresh artistic masterpiece.",
            selected = hour == 7 && minute == 0,
            onClick = { onTimeChange(7, 0) }
        )

        Spacer(Modifier.height(10.dp))

        // Preset 2: Workday
        OnboardingOptionCard(
            title = "Workday Start (09:00 AM)",
            description = "A new classical painting right as your workday begins.",
            selected = hour == 9 && minute == 0,
            onClick = { onTimeChange(9, 0) }
        )

        Spacer(Modifier.height(10.dp))

        // Preset 3: Evening
        OnboardingOptionCard(
            title = "Evening Unwind (18:00 PM)",
            description = "Relax with fine art after your workday wraps up.",
            selected = hour == 18 && minute == 0,
            onClick = { onTimeChange(18, 0) }
        )

        Spacer(Modifier.height(16.dp))

        // Custom time picker box (hours + 5-minute steps)
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = ArtColors.Surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ArtColors.CardBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    String.format(java.util.Locale.ROOT, "Custom Time: %02d:%02d", hour, minute),
                    style = MaterialTheme.typography.subtitle2,
                    color = ArtColors.PaperWhite
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onTimeChange((hour - 1 + 24) % 24, minute) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("-", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                    Text(
                        String.format(java.util.Locale.ROOT, "%02d", hour),
                        style = MaterialTheme.typography.subtitle1,
                        color = ArtColors.GalleryGold,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                    IconButton(
                        onClick = { onTimeChange((hour + 1) % 24, minute) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("+", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }

                    Text(":", style = MaterialTheme.typography.subtitle1, color = ArtColors.PaperMuted, modifier = Modifier.padding(horizontal = 4.dp))

                    IconButton(
                        onClick = { onTimeChange(hour, (minute - 5 + 60) % 60) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("-", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                    Text(
                        String.format(java.util.Locale.ROOT, "%02d", minute),
                        style = MaterialTheme.typography.subtitle1,
                        color = ArtColors.GalleryGold,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                    IconButton(
                        onClick = { onTimeChange(hour, (minute + 5) % 60) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("+", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                }
            }
        }
    }
}

// ── Step 3: System Integration ──────────────────────────────────────────────

@Composable
private fun OnboardingStep3Integration(
    autoStart: Boolean,
    notifications: Boolean,
    syncLockScreen: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSyncLockScreenChange: (Boolean) -> Unit
) {
    val isMac = MacPlatform.isMac
    val osLabel = if (isMac) "macOS" else "Windows"
    val trayGuideIcon = if (isMac) "tray/tray-mono-black-64.png" else "tray/tray-64.png"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$osLabel Integration",
            style = MaterialTheme.typography.h5,
            color = ArtColors.GalleryGold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isMac) "ArtWallpaper runs quietly in your menu bar without appearing in your Dock." else "ArtWallpaper runs silently in your system tray without interrupting your work.",
            style = MaterialTheme.typography.body2,
            color = ArtColors.PaperMuted
        )

        Spacer(Modifier.height(20.dp))

        // Auto-start toggle card
        OnboardingOptionCard(
            title = if (isMac) "Launch at Login" else "Launch on System Startup",
            description = if (isMac) "Starts quietly in your menu bar when you sign in. Recommended." else "Starts minimized in your taskbar tray when you login. Recommended.",
            selected = autoStart,
            onClick = { onAutoStartChange(!autoStart) },
            trailingContent = {
                Switch(
                    checked = autoStart,
                    onCheckedChange = onAutoStartChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ArtColors.GalleryGold,
                        checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.5f)
                    )
                )
            }
        )

        Spacer(Modifier.height(12.dp))

        // Notifications toggle card
        OnboardingOptionCard(
            title = "Artwork Change Notifications",
            description = "Displays a subtle $osLabel notification showing the artwork title and artist.",
            selected = notifications,
            onClick = { onNotificationsChange(!notifications) },
            trailingContent = {
                Switch(
                    checked = notifications,
                    onCheckedChange = onNotificationsChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ArtColors.GalleryGold,
                        checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.5f)
                    )
                )
            }
        )

        if (!isMac) {
            Spacer(Modifier.height(12.dp))

            // Windows-only lock screen sync toggle card
            OnboardingOptionCard(
                title = "Sync Lock Screen Wallpaper",
                description = "Automatically mirrors your framed artwork onto your Windows lock screen.",
                selected = syncLockScreen,
                onClick = { onSyncLockScreenChange(!syncLockScreen) },
                trailingContent = {
                    Switch(
                        checked = syncLockScreen,
                        onCheckedChange = onSyncLockScreenChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ArtColors.GalleryGold,
                            checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.5f)
                        )
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // System tray guide notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = ArtColors.SurfaceVariant,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ArtColors.CardBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(trayGuideIcon),
                    contentDescription = "Tray Icon",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    if (isMac) "ArtWallpaper lives in the macOS menu bar at the top-right of your screen. Click the icon anytime to open, pause, check updates, or switch artwork." else "ArtWallpaper lives in the Windows notification area (bottom-right taskbar). Right-click the icon anytime to open, pause, check updates, or switch artwork.",
                    style = MaterialTheme.typography.caption,
                    color = ArtColors.PaperMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ── Step 4: Curate First Wallpaper ──────────────────────────────────────────

@Composable
private fun OnboardingStep4Curate(
    isCurating: Boolean,
    curationStage: String,
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    onCurate: () -> Unit,
    onFinish: () -> Unit
) {
    val osLabel = if (MacPlatform.isMac) "macOS" else "Windows"

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (currentArtwork != null) {
            // Success state - reveal artwork
            val (_, metadata) = currentArtwork
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(ArtColors.GalleryGold.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Success",
                    tint = ArtColors.GalleryGold,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Your Gallery is Ready!",
                style = MaterialTheme.typography.h5,
                color = ArtColors.GalleryGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "\"${metadata.title}\"",
                style = MaterialTheme.typography.subtitle1,
                color = ArtColors.PaperWhite,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            metadata.artist?.let {
                Text(
                    "By $it",
                    style = MaterialTheme.typography.body2,
                    color = ArtColors.PaperMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            PulsingButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("ENTER GALLERY", style = MaterialTheme.typography.button)
            }

        } else if (isCurating) {
            // Live curating state
            CircularProgressIndicator(
                color = ArtColors.GalleryGold,
                modifier = Modifier.size(52.dp),
                strokeWidth = 3.dp
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Curating Your First Masterpiece",
                style = MaterialTheme.typography.h6,
                color = ArtColors.PaperWhite,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                curationStage,
                style = MaterialTheme.typography.body2,
                color = ArtColors.GalleryGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Fetching high-resolution scans from The Metropolitan Museum of Art...",
                style = MaterialTheme.typography.caption,
                color = ArtColors.PaperMuted,
                textAlign = TextAlign.Center
            )

        } else {
            // Ready to curate prompt
            Image(
                painter = painterResource("icons/icon.png"),
                contentDescription = "Master Artwork Icon",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Begin Your Art Journey",
                style = MaterialTheme.typography.h5,
                color = ArtColors.GalleryGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Click below to curate your first museum wallpaper and apply it directly to your $osLabel desktop with true screen fit.",
                style = MaterialTheme.typography.body2,
                color = ArtColors.PaperMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            PulsingButton(
                onClick = onCurate,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("CURATE MY FIRST WALLPAPER", style = MaterialTheme.typography.button)
            }
        }
    }
}
