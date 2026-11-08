import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.nio.file.Path
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.slf4j.LoggerFactory
import service.ServiceController
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.io.path.exists

@Composable
fun MainScreen(
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    serviceController: ServiceController,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    coroutineScope: CoroutineScope
) {
    val logger = LoggerFactory.getLogger("MainScreen")
    val currentSettings by Settings.currentSettings.collectAsState()
    var isFirstRun by remember { mutableStateOf(settings.isFirstRun) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }

    if (isFirstRun) {
        WelcomeScreen(
            isInitializing = isInitializing,
            onIsInitializingChange = { isInitializing = it },
            settings = settings,
            serviceController = serviceController,
            onFirstRunComplete = { isFirstRun = false },
            onSettingsChange = onSettingsChange,
            onError = { error -> errorMessage = error },
            coroutineScope = coroutineScope,
            currentArtwork = currentArtwork
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(ArtColors.Canvas)) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Top bar ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Art Wallpaper",
                        style = MaterialTheme.typography.h6,
                        color = ArtColors.PaperWhite,
                        letterSpacing = 0.5.sp
                    )
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = ArtColors.PaperMuted
                        )
                    }
                }

                // ── Artwork card — full-bleed ────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (currentArtwork != null) {
                        val (path, metadata) = currentArtwork

                        // Full-bleed image
                        Image(
                            bitmap = remember(path) {
                                SkiaImage.makeFromEncoded(path.toFile().readBytes())
                                    .toComposeImageBitmap()
                            },
                            contentDescription = metadata.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Gradient scrim at bottom for readability
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, ArtColors.ScrimBlack)
                                    )
                                )
                        )

                        // Artwork info overlay
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                metadata.title,
                                style = MaterialTheme.typography.h6,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            metadata.artist?.let { artist ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    buildString {
                                        append(artist)
                                        metadata.year?.let { append(" · $it") }
                                    },
                                    style = MaterialTheme.typography.subtitle2,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }

                    } else {
                        // No artwork placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ArtColors.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "No artwork yet",
                                    style = MaterialTheme.typography.subtitle1,
                                    color = ArtColors.PaperMuted
                                )
                                Text(
                                    "Press Next Artwork to begin",
                                    style = MaterialTheme.typography.body2,
                                    color = ArtColors.PaperMuted.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // ── Loading overlay ──────────────────────────────────────
                    AnimatedVisibility(
                        visible = isLoading,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.fillMaxSize().zIndex(2f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = ArtColors.GalleryGold,
                                    modifier = Modifier.size(44.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Fetching artwork",
                                    style = MaterialTheme.typography.subtitle1,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // ── Controls bar ─────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (currentSettings.hasSetUpdateTime) {
                                "Updates daily at %02d:%02d".format(
                                    currentSettings.updateTimeHour,
                                    currentSettings.updateTimeMinute
                                )
                            } else {
                                "Updates daily at 07:00"
                            },
                            style = MaterialTheme.typography.body2,
                            color = ArtColors.PaperMuted
                        )
                        if (isLoading) {
                            Text(
                                "Searching collections...",
                                style = MaterialTheme.typography.caption,
                                color = ArtColors.GalleryGold.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!isLoading) {
                                isLoading = true
                                val previousTitle = currentArtwork?.second?.title
                                logger.info("Starting wallpaper update. Previous: $previousTitle")

                                coroutineScope.launch(Dispatchers.Default) {
                                    try {
                                        serviceController.nextWallpaper()

                                        withTimeout(30_000L) {
                                            serviceController.wallpaperManager.currentArtwork
                                                .collect { newArtwork ->
                                                    if (newArtwork != null &&
                                                        newArtwork.second.title != previousTitle &&
                                                        newArtwork.first.exists()
                                                    ) {
                                                        logger.info("New artwork: ${newArtwork.second.title}")
                                                        cancel()
                                                    }
                                                }
                                        }
                                    } catch (e: Exception) {
                                        logger.error("Failed to set next wallpaper", e)
                                        withContext(Dispatchers.Main) {
                                            errorMessage = when (e) {
                                                is TimeoutCancellationException ->
                                                    "Taking longer than expected. Please try again."
                                                is CancellationException -> null
                                                else -> "Could not update wallpaper: ${e.message}"
                                            }
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = ArtColors.GalleryGold,
                            contentColor = ArtColors.Canvas,
                            disabledBackgroundColor = ArtColors.GalleryGoldDim.copy(alpha = 0.4f),
                            disabledContentColor = ArtColors.Canvas.copy(alpha = 0.5f)
                        ),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 2.dp
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = ArtColors.Canvas,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            "NEXT ARTWORK",
                            style = MaterialTheme.typography.button
                        )
                    }
                }

                // ── Error snackbar ───────────────────────────────────────────
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

        // Settings dialog
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onSettingsChange = { newSettings ->
                    Settings.updateCurrentSettings(newSettings)
                    onSettingsChange(newSettings)
                    coroutineScope.launch {
                        delay(1000)
                        serviceController.restartService()
                    }
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

// ── Welcome / Onboarding ──────────────────────────────────────────────────────

@Composable
fun WelcomeScreen(
    isInitializing: Boolean,
    onIsInitializingChange: (Boolean) -> Unit,
    settings: Settings,
    serviceController: ServiceController,
    onFirstRunComplete: () -> Unit,
    onSettingsChange: (Settings) -> Unit,
    onError: (String) -> Unit,
    coroutineScope: CoroutineScope,
    currentArtwork: Pair<Path, ArtworkMetadata>?
) {
    val logger = LoggerFactory.getLogger("WelcomeScreen")
    var showCurating by remember { mutableStateOf(false) }

    LaunchedEffect(currentArtwork) {
        if (currentArtwork != null && showCurating) {
            delay(2000)
            withContext(Dispatchers.Main) {
                val updatedSettings = settings.copy(
                    isFirstRun = false,
                    hasEnabledAutoStart = true,
                    startWithSystem = true
                )
                updatedSettings.save()
                onSettingsChange(updatedSettings)
            }
            delay(1000)
            serviceController.startService()
            withContext(Dispatchers.Main) {
                onFirstRunComplete()
                showCurating = false
                onIsInitializingChange(false)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArtColors.Canvas),
        contentAlignment = Alignment.Center
    ) {
        if (showCurating) {
            // ── Curating state ────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(48.dp)
            ) {
                CircularProgressIndicator(
                    color = ArtColors.GalleryGold,
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    "Curating your first artwork",
                    style = MaterialTheme.typography.h6,
                    color = ArtColors.PaperWhite
                )
                Text(
                    "Searching the Metropolitan Museum of Art collection for a piece worth your desktop.",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    color = ArtColors.PaperMuted
                )
            }

        } else {
            // ── Welcome state ─────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 40.dp, vertical = 32.dp)
            ) {
                Text(
                    "ArtWallpaper",
                    style = MaterialTheme.typography.h4,
                    color = ArtColors.GalleryGold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "A new masterpiece. Every morning.",
                    style = MaterialTheme.typography.h6,
                    color = ArtColors.PaperWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // Divider
                Divider(color = ArtColors.DividerLine, thickness = 1.dp)

                Spacer(Modifier.height(24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    WelcomeFeature(
                        label = "Daily rotation",
                        detail = "Your wallpaper changes automatically at a time you choose."
                    )
                    WelcomeFeature(
                        label = "Museum-grade curation",
                        detail = "Every piece drawn from the Met's public collection — over 400,000 works."
                    )
                    WelcomeFeature(
                        label = "Works offline",
                        detail = "Previously seen artwork stays on your device as a fallback."
                    )
                }

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (!isInitializing) {
                            showCurating = true
                            onIsInitializingChange(true)

                            coroutineScope.launch(Dispatchers.Default) {
                                try {
                                    WindowsAutoStart.enable()
                                    serviceController.nextWallpaper()

                                    try {
                                        withTimeout(30_000L) {
                                            serviceController.wallpaperManager.currentArtwork
                                                .collect { newArtwork ->
                                                    if (newArtwork != null && newArtwork.first.exists()) {
                                                        logger.info("Initial artwork: ${newArtwork.second.title}")
                                                        withContext(Dispatchers.Main) {
                                                            val updatedSettings = settings.copy(
                                                                isFirstRun = false,
                                                                hasEnabledAutoStart = true,
                                                                startWithSystem = true
                                                            )
                                                            updatedSettings.save()
                                                            onSettingsChange(updatedSettings)
                                                        }
                                                        delay(1000)
                                                        serviceController.startService()
                                                        withContext(Dispatchers.Main) {
                                                            onFirstRunComplete()
                                                            showCurating = false
                                                            onIsInitializingChange(false)
                                                        }
                                                        return@collect
                                                    }
                                                }
                                        }
                                    } catch (e: Exception) {
                                        when (e) {
                                            is TimeoutCancellationException -> throw e
                                            is CancellationException -> logger.info("Setup completed")
                                            else -> throw e
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.error("Failed to complete first run", e)
                                    withContext(Dispatchers.Main) {
                                        onError(
                                            when (e) {
                                                is TimeoutCancellationException ->
                                                    "Taking longer than expected. Please try again."
                                                else -> "Setup failed: ${e.message}"
                                            }
                                        )
                                        showCurating = false
                                        onIsInitializingChange(false)
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isInitializing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ArtColors.GalleryGold,
                        contentColor = ArtColors.Canvas,
                        disabledBackgroundColor = ArtColors.GalleryGoldDim.copy(alpha = 0.4f),
                        disabledContentColor = ArtColors.Canvas.copy(alpha = 0.5f)
                    ),
                    elevation = ButtonDefaults.elevation(0.dp),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("BEGIN", style = MaterialTheme.typography.button)
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Requires an internet connection on first launch.",
                    style = MaterialTheme.typography.caption,
                    color = ArtColors.PaperMuted.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WelcomeFeature(label: String, detail: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Gold bullet
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .background(ArtColors.GalleryGold, shape = RoundedCornerShape(50))
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.subtitle2,
                color = ArtColors.PaperWhite
            )
            Text(
                detail,
                style = MaterialTheme.typography.body2,
                color = ArtColors.PaperMuted
            )
        }
    }
}
