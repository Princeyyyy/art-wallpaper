import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.nio.file.Path
import org.slf4j.LoggerFactory
import service.ServiceController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.io.path.exists

@OptIn(ExperimentalAnimationApi::class)
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

    val updateManager = remember { service.UpdateManager() }
    val updateStatus by updateManager.status.collectAsState()

    LaunchedEffect(Unit) {
        serviceController.wallpaperManager.syncWithActiveDesktopWallpaper()
        updateManager.checkForUpdates(silent = true)
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
                                painter = painterResource("icons/32x32.png"),
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

                        // Right Action Controls: Update Badge, Quick Theme Toggle, Fit Mode Badge, Settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Update Available Pill
                            if (updateStatus is service.UpdateStatus.Available) {
                                val latestVer = (updateStatus as service.UpdateStatus.Available).latestVersion
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = ArtColors.GalleryGold.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, ArtColors.GalleryGold),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { showSettings = true }
                                ) {
                                    val goldColor = ArtColors.GalleryGold
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
                                            drawCircle(color = goldColor)
                                        }
                                        Text(
                                            "v$latestVer Ready",
                                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                            color = goldColor
                                        )
                                    }
                                }
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
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                                        if (isDarkTheme) {
                                            drawCircle(color = themeGold)
                                        } else {
                                            drawCircle(color = themeGold, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
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
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Preferences",
                                    tint = ArtColors.PaperMuted
                                )
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

                                            // Curatorial Chips
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CuratorialChip("The Met Archive")
                                                val desc = metadata.description
                                                if (!desc.isNullOrBlank()) {
                                                    val firstLine = desc.lines().firstOrNull()?.trim()
                                                    if (!firstLine.isNullOrBlank()) {
                                                        val shortMedium = if (firstLine.length > 32) firstLine.take(30) + "…" else firstLine
                                                        CuratorialChip(shortMedium)
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
                                            painter = painterResource("icons/48x48.png"),
                                            contentDescription = "Art Wallpaper",
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
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
                                        "Fetching high-res painting & rendering laptop screen fit",
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
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
                            }
                            Text(
                                if (isLoading) "Downloading high-resolution painting from The Met..."
                                else "Running silently in Windows tray · Screen-fit active",
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
                    updateManager = updateManager,
                    onSettingsChange = { newSettings ->
                        Settings.updateCurrentSettings(newSettings)
                        onSettingsChange(newSettings)
                        coroutineScope.launch {
                            delay(500)
                            serviceController.restartService()
                        }
                    },
                    onDismiss = { showSettings = false }
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
                        painter = painterResource("icons/24x24.png"),
                        contentDescription = "Logo",
                        modifier = Modifier.size(18.dp)
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
                        onAutoStartChange = { autoStartEnabled = it },
                        onNotificationsChange = { notificationsEnabled = it }
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
                                            notificationsEnabled = notificationsEnabled
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
            "A museum gallery on your Windows desktop",
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
                title = "True Laptop Screen Fit",
                description = "Smart ambient matting prevents stretching, fitting your laptop resolution perfectly."
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

        // Custom time picker box
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                    Text(
                        String.format(java.util.Locale.ROOT, "%02d", hour),
                        style = MaterialTheme.typography.subtitle1,
                        color = ArtColors.GalleryGold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onTimeChange((hour + 1) % 24, minute) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                }
            }
        }
    }
}

// ── Step 3: Windows Integration ─────────────────────────────────────────────

@Composable
private fun OnboardingStep3Integration(
    autoStart: Boolean,
    notifications: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Windows Integration",
            style = MaterialTheme.typography.h5,
            color = ArtColors.GalleryGold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "ArtWallpaper runs silently in your system tray without interrupting your work.",
            style = MaterialTheme.typography.body2,
            color = ArtColors.PaperMuted
        )

        Spacer(Modifier.height(20.dp))

        // Auto-start toggle card
        OnboardingOptionCard(
            title = "Launch on System Startup",
            description = "Starts minimized in your taskbar tray when you login. Recommended.",
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
            description = "Displays a subtle Windows notification showing the artwork title and artist.",
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
                    painter = painterResource("tray/tray-24.png"),
                    contentDescription = "Tray Icon",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "ArtWallpaper lives in the Windows notification area (bottom-right taskbar). Right-click the icon anytime to open, pause, or switch artwork.",
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
                "Click below to curate your first museum wallpaper and apply it directly to your Windows desktop with true screen fit.",
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
