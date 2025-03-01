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
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.swing.Swing

@Composable
fun MainScreen(
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    serviceController: ServiceController,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    coroutineScope: CoroutineScope
) {
    val logger = LoggerFactory.getLogger("MainScreen")
    var isFirstRun by remember { mutableStateOf(settings.isFirstRun) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }

    // Add effect to update isFirstRun when settings change
    LaunchedEffect(settings) {
        isFirstRun = settings.isFirstRun
    }

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
                                .background(Color.Black.copy(alpha = 0.7f))
                                .zIndex(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Fetching New Artwork...",
                                    style = MaterialTheme.typography.h6,
                                    color = Color.White
                                )
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
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                withTimeout(60000) { // Increase timeout to 60 seconds
                                    serviceController.nextWallpaper()
                                }
                            } catch (e: Exception) {
                                logger.error("Failed to update wallpaper", e)
                                errorMessage = when (e) {
                                    is TimeoutCancellationException -> "Operation timed out. The server might be slow or the image too large. Please try again."
                                    else -> "Failed to update: ${e.message}"
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.padding(8.dp),
                    enabled = !isLoading
                ) {
                    Text("Next Wallpaper")
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Curating your masterpiece...",
                    style = MaterialTheme.typography.h6
                )
            }
        } else {
            Button(
                onClick = {
                    if (!isInitializing) {
                        showCurating = true
                        onIsInitializingChange(true)
                        
                        coroutineScope.launch(Dispatchers.Default) {
                            try {
                                WindowsAutoStart.enable()
                                serviceController.nextWallpaper()
                                
                                // Wait for the wallpaper to be set
                                withTimeout(30000) { // 30 seconds timeout
                                    while (currentArtwork == null) {
                                        delay(100)
                                    }
                                }
                                
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
                            } catch (e: Exception) {
                                logger.error("Failed to complete first run setup", e)
                                withContext(Dispatchers.Main) {
                                    onError("Failed to complete setup: ${e.message}")
                                    showCurating = false
                                    onIsInitializingChange(false)
                                }
                            }
                        }
                    }
                },
                enabled = !isInitializing
            ) {
                Text("Get Started")
            }
        }
    }
} 