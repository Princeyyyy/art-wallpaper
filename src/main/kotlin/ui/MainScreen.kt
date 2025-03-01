import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.nio.file.Path
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.slf4j.LoggerFactory
import service.ServiceController
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

    // Move loading state to composition scope
    val loadingState = remember { mutableStateOf(false) }
    var isLoadingState by loadingState

    LaunchedEffect(isLoading) {
        isLoadingState = isLoading
        logger.info("Loading state changed: $isLoading")
    }

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
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar with settings - removed running state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Art Wallpaper",
                    style = MaterialTheme.typography.h6
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colors.primary
                    )
                }
            }

            // Current artwork display
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                elevation = 4.dp,
                backgroundColor = MaterialTheme.colors.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (currentArtwork != null) {
                        val (path, metadata) = currentArtwork
                        Image(
                            bitmap = remember(path) {
                                SkiaImage.makeFromEncoded(path.toFile().readBytes())
                                    .toComposeImageBitmap()
                            },
                            contentDescription = metadata.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Artwork info overlay
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(),
                            color = MaterialTheme.colors.surface.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.medium,
                            elevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    metadata.title,
                                    style = MaterialTheme.typography.h6,
                                    color = MaterialTheme.colors.onSurface
                                )
                                metadata.artist?.let {
                                    Text(
                                        "By $it",
                                        style = MaterialTheme.typography.subtitle1,
                                        color = MaterialTheme.colors.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        // No artwork placeholder
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "No wallpaper set yet",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Loading overlay
                    if (isLoadingState) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .zIndex(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Exploring Art History... 🎨",
                                    style = MaterialTheme.typography.h6,
                                    color = Color.White
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "Searching through centuries of masterpieces... 🖼️",
                                        style = MaterialTheme.typography.body1,
                                        color = Color.White.copy(alpha = 0.9f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "From canvas to your screen, art is on its way! ✨",
                                        style = MaterialTheme.typography.body1,
                                        color = Color.White.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Get ready for your next daily masterpiece! 🌟",
                                        style = MaterialTheme.typography.body1,
                                        color = Color.White.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isLoading) {
                            "Fetching new artwork..."
                        } else if (settings.hasSetUpdateTime) {
                            "Your wallpaper updates daily at ${String.format("%02d:%02d", settings.updateTimeHour, settings.updateTimeMinute)}"
                        } else {
                            "Your wallpaper updates daily at 07:00"
                        },
                        style = MaterialTheme.typography.body1
                    )
                    Text(
                        if (isLoading) {
                            "Please wait while we find something beautiful! ✨"
                        } else {
                            "Sit back and enjoy the daily art! ✨"
                        },
                        style = MaterialTheme.typography.body2.copy(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Button(
                    onClick = {
                        if (!isLoading) {
                            isLoading = true
                            val previousTitle = currentArtwork?.second?.title
                            logger.info("Starting wallpaper update. Previous artwork: $previousTitle")
                            
                            coroutineScope.launch(Dispatchers.Default) {
                                try {
                                    serviceController.nextWallpaper()
                                    
                                    // Wait for the wallpaper to change with a shorter timeout
                                    withTimeout(30000) { // 30 seconds timeout
                                        serviceController.wallpaperManager.currentArtwork
                                            .collect { newArtwork ->
                                                if (newArtwork != null && 
                                                    newArtwork.second.title != previousTitle &&
                                                    newArtwork.first.exists()) {
                                                    logger.info("New artwork detected - Title: ${newArtwork.second.title}")
                                                    cancel() // Cancel the timeout coroutine
                                                }
                                            }
                                    }
                                } catch (e: Exception) {
                                    logger.error("Failed to set next wallpaper", e)
                                    withContext(Dispatchers.Main) {
                                        errorMessage = when (e) {
                                            is TimeoutCancellationException -> "Taking longer than expected to find suitable artwork. Please try again."
                                            is CancellationException -> null // Normal completion
                                            else -> "Failed to set wallpaper: ${e.message}"
                                        }
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        logger.info("Wallpaper update completed. Loading state set to false")
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colors.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Next Wallpaper")
                    }
                }
            }

            // Error message display
            errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { errorMessage = null }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }

        // Settings dialog
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onSettingsChange = { newSettings -> 
                    newSettings.save()
                    onSettingsChange(newSettings)
                    // Debounce restart
                    coroutineScope.launch {
                        delay(1000) // Wait for rapid changes to settle
                        serviceController.restartService()
                    }
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

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
    var showCurating by remember { mutableStateOf(false) }
    
    // Add effect to handle currentArtwork changes
    LaunchedEffect(currentArtwork) {
        if (currentArtwork != null && showCurating) {
            // Give time for the wallpaper to be visible
            delay(2000)
            
            // Update settings and start service
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (showCurating) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Curating your masterpiece... 🎨",
                    style = MaterialTheme.typography.h6
                )
                Text(
                    "We're searching through the world's finest art collections to find something special just for you! ✨",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "From the halls of the Met to the galleries of Europe... 🏛️",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                    )
                    Text(
                        "Discovering timeless beauty just for your desktop 🖼️",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                    )
                    Text(
                        "Get ready to be inspired! 🌟",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.9f)
                    )
                    Text(
                        "Your personal art gallery is just moments away... ⭐",
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    "Welcome to ArtWallpaper! 🎨",
                    style = MaterialTheme.typography.h4,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Transform your desktop into a daily gallery of masterpieces ✨",
                    style = MaterialTheme.typography.h6,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Every day is an opportunity to discover something beautiful! 🌅",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.9f)
                    )
                    Text(
                        "From Renaissance masterpieces to modern classics, let your desktop tell a new story every day 📖",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                    )
                    Text(
                        "Curated from the world's greatest museums, bringing art history right to your screen 🏛️",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "Ready to begin your artistic journey? 🎭",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.9f)
                    )
                    Text(
                        "Let's make your desktop a window to the world of art! 🖼️",
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                    )
                }
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
                                        // Wait for the wallpaper to change with a shorter timeout
                                        withTimeout(30000) { // 30 seconds timeout
                                            serviceController.wallpaperManager.currentArtwork
                                                .collect { newArtwork ->
                                                    if (newArtwork != null && newArtwork.first.exists()) {
                                                        logger.info("Initial artwork set successfully: ${newArtwork.second.title}")
                                                        // Update settings and start service
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
                                                        return@collect // Use return@withTimeout instead of cancel()
                                                    }
                                                }
                                        }
                                    } catch (e: Exception) {
                                        when (e) {
                                            is TimeoutCancellationException -> throw e // Re-throw timeout
                                            is CancellationException -> {
                                                // Normal completion - ignore the exception
                                                logger.info("Setup completed successfully")
                                            }
                                            else -> throw e // Re-throw other exceptions
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.error("Failed to complete first run setup", e)
                                    withContext(Dispatchers.Main) {
                                        val errorMessage = when (e) {
                                            is TimeoutCancellationException -> "Taking longer than expected to find suitable artwork. Please try again."
                                            else -> "Failed to complete setup: ${e.message}"
                                        }
                                        onError(errorMessage)
                                        showCurating = false
                                        onIsInitializingChange(false)
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isInitializing,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Begin Your Art Journey 🚀")
                }
            }
        }
    }
} 