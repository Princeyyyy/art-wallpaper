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

@Composable
fun MainScreen(
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    serviceController: ServiceController,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    LoggerFactory.getLogger("MainScreen")
    var isFirstRun by remember { mutableStateOf(settings.isFirstRun) }
    var isInitialLoading by remember { mutableStateOf(false) }
    val isUpdatingWallpaper by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentSettings by Settings.currentSettings.collectAsState()
    val serviceRunning by serviceController.isServiceRunning.collectAsState()

    if (isFirstRun) {
        if (isInitializing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colors.primary
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "🎨 Curating your first masterpiece...",
                            style = MaterialTheme.typography.h6,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            "Your desktop is about to get fabulous! ✨",
                            style = MaterialTheme.typography.subtitle1,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            WelcomeScreen(
                serviceController = serviceController,
                settings = settings,
                isInitializing = isInitializing,
                onIsInitializingChange = { isInitializing = it },
                onFirstRunComplete = {
                    isFirstRun = false
                },
                onError = { errorMessage = it }
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar with settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Art Wallpaper ${if (serviceRunning) "(Running)" else "(Stopped)"}",
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

                        // Artwork info overlay with rounded corners
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
                    Crossfade(
                        targetState = isInitialLoading,
                        label = "initial_loading_overlay"
                    ) { loading ->
                        if (loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colors.surface.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text("Starting service...")
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
                        if (currentSettings.hasSetUpdateTime) {
                            "Your wallpaper updates daily at ${String.format("%02d:%02d", currentSettings.updateTimeHour, currentSettings.updateTimeMinute)}"
                        } else {
                            "Your wallpaper updates daily at 07:00"
                        },
                        style = MaterialTheme.typography.body1
                    )
                    Text(
                        "Sit back and enjoy the daily art! ✨",
                        style = MaterialTheme.typography.body2.copy(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            isInitialLoading = true
                            try {
                                withTimeout(60000) {
                                    serviceController.startService()
                                }
                            } catch (e: Exception) {
                                errorMessage = "Failed to start service: ${e.message}"
                            } finally {
                                isInitialLoading = false
                            }
                        }
                    },
                    modifier = Modifier.padding(8.dp),
                    enabled = !isInitialLoading
                ) {
                    if (isInitialLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colors.onPrimary
                        )
                    } else {
                        Text("Start Service")
                    }
                }
            }

            // Next wallpaper button
            Box(
                modifier = Modifier.wrapContentWidth(),
                contentAlignment = Alignment.Center
            ) {
                var isLoading by remember { mutableStateOf(false) }
                
                LaunchedEffect(isLoading) {
                    if (isLoading) {
                        try {
                            serviceController.nextWallpaper()
                        } catch (e: Exception) {
                            errorMessage = when (e) {
                                is TimeoutCancellationException -> "Operation timed out. Please try again."
                                else -> "Failed to update: ${e.message}"
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                }

                PulsingButton(
                    onClick = { if (!isLoading) isLoading = true },
                    enabled = true,
                    modifier = Modifier.wrapContentWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isLoading) Color.Red else MaterialTheme.colors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        if (isLoading) {
                            val infiniteTransition = rememberInfiniteTransition(label = "loading_spinner")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "spinner_rotation"
                            )
                            
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotation),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (isLoading) "Finding New Artwork..." else "Next Wallpaper"
                        )
                    }
                }
            }

            // Updating wallpaper overlay
            AnimatedVisibility(
                visible = isUpdatingWallpaper,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Updating Wallpaper...",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.7f))
                            .padding(16.dp)
                    )
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
                    scope.launch {
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
private fun WelcomeScreen(
    serviceController: ServiceController,
    settings: Settings,
    isInitializing: Boolean,
    onIsInitializingChange: (Boolean) -> Unit,
    onFirstRunComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🎨 Welcome to Art Wallpaper!",
            style = MaterialTheme.typography.h4,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Time to give your desktop a glow-up! 🖼️",
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Tired of the same old boring wallpaper? We've got your back! Every 24 hours, we'll surprise you with a stunning masterpiece that'll make your coworkers jealous. 🌟",
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "From Van Gogh's swirls to Monet's water lilies - let's turn your screen into the world's coolest mini-museum! ✨",
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier.wrapContentWidth(),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "loading_spinner")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spinner_rotation"
            )

            Button(
                onClick = {
                    if (!isInitializing) {
                        message = listOf(
                            "🎨 Time to make your desktop fabulous!",
                            "✨ Let's add some artistic flair to your day!",
                            "🖼️ Your desktop is about to get a glow-up!",
                            "🎭 Transforming your screen into an art gallery...",
                            "🌟 Get ready for daily doses of masterpieces!"
                        ).random()
                        
                        onIsInitializingChange(true)
                        scope.launch {
                            try {
                                WindowsAutoStart.enable()
                                serviceController.nextWallpaper()
                                serviceController.startService()
                                settings.copy(
                                    isFirstRun = false,
                                    hasEnabledAutoStart = true,
                                    startWithSystem = true
                                ).save()
                                onFirstRunComplete()
                            } catch (e: Exception) {
                                onError("Failed to set initial wallpaper: ${e.message}")
                            } finally {
                                onIsInitializingChange(false)
                            }
                        }
                    }
                },
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isInitializing) Color.Red else MaterialTheme.colors.primary,
                    contentColor = Color.White
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    if (isInitializing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotation),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = if (isInitializing) message ?: "Setting Things Up..." else "Get Started",
                        style = MaterialTheme.typography.button
                    )
                }
            }
        }
    }
} 